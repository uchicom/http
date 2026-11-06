// (C) 2012 uchicom
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 静的ファイルの配信・認証・転送を行う標準のRouter.
 *
 * @author Uchiyama Shigeki
 */
public class DefaultRouter implements Router {

  private static final Logger LOGGER = Logger.getLogger(DefaultRouter.class.getName());

  /** キャッシュ内容を保持するため、リクエストをまたいで使い回すWebFile(キャッシュ有効時のみ使用) */
  protected final Map<String, WebFile> webFileMap = new ConcurrentHashMap<>();

  protected final Map<String, Properties> basicMap = new ConcurrentHashMap<>();
  protected final Map<String, Properties> forwardMap = new ConcurrentHashMap<>();

  /** 公開ディレクトリ(正規化済みの絶対パス) */
  protected Path htmlPath;

  /** 公開ディレクトリの実パス(シンボリックリンク・大文字小文字・短縮名を解決済み。解決できない場合はhtmlPathと同じ) */
  protected Path htmlRealPath;

  /** .basicの最終更新時刻(ディレクトリキー別). 更新検知用 */
  private final Map<String, Long> basicModifiedMap = new ConcurrentHashMap<>();

  /** キャッシュ対象の最大ファイルサイズ(バイト)。0以下はキャッシュなし */
  protected long cacheSize;

  /** forward先への接続タイムアウト(ミリ秒) */
  protected int forwardConnectTimeout = 5000;

  /** forward先からの読み込みタイムアウト(ミリ秒) */
  protected int forwardReadTimeout = 30000;

  @Override
  public void init(File baseFile, long cacheSize) {
    this.cacheSize = cacheSize;
    setHtmlDir(baseFile);
    loadSettings(baseFile);
  }

  void setHtmlDir(File htmlFile) {
    htmlPath = htmlFile.toPath().toAbsolutePath().normalize();
    htmlRealPath = toRealPathOrSelf(htmlPath);
  }

  /**
   * リクエストパスに対応する公開可能なファイルを、都度ファイルシステムを確認して返す.
   *
   * <p>公開ディレクトリ外、および`.`で始まる名前を含むパスは対象外とする. 存在しない、またはファイルでない場合はnullを返す.
   */
  WebFile find(String filePath) {
    // Windowsでは`\`が区切りとして解釈され、隠しファイル判定を迂回されるため拒否する
    if (!filePath.startsWith("/") || filePath.indexOf('\\') >= 0) {
      return null;
    }
    for (String name : filePath.split("/", 0)) {
      if (name.startsWith(".")) {
        return null;
      }
    }
    Path path;
    try {
      path = htmlPath.resolve(filePath.substring(1)).normalize();
    } catch (InvalidPathException e) {
      return null;
    }
    if (!path.startsWith(htmlPath) || containsHiddenName(htmlPath.relativize(path))) {
      webFileMap.remove(filePath);
      return null;
    }
    File file = path.toFile();
    if (!file.isFile() || !isPublicRealPath(path)) {
      webFileMap.remove(filePath);
      return null;
    }
    WebFile webFile;
    if (cacheSize > 0) {
      webFile = webFileMap.computeIfAbsent(filePath, key -> new WebFile(file, cacheSize));
    } else {
      webFile = new WebFile(file, 0);
    }
    String key = directoryKey(path.getParent());
    try {
      refreshBasic(path.getParent().resolve(".basic").toFile(), key);
    } catch (IOException e) {
      // 認証設定を読めない場合は認証なしで公開せず、アクセスを拒否する
      LOGGER.log(Level.WARNING, path.getParent() + " .basic can not be loaded", e);
      webFileMap.remove(filePath);
      return null;
    }
    webFile.setBasic(basicMap.get(key));
    return webFile;
  }

  /** 起動後に追加・更新された.basicを反映する. 読み込めない場合はIOExceptionを投げる. */
  private void refreshBasic(File basicFile, String key) throws IOException {
    if (!basicFile.isFile()) {
      return;
    }
    long modified = basicFile.lastModified();
    Long loaded = basicModifiedMap.get(key);
    if (loaded != null && loaded == modified && basicMap.containsKey(key)) {
      return;
    }
    basicMap.put(key, loadProperties(basicFile));
    basicModifiedMap.put(key, modified);
  }

  private static Properties loadProperties(File file) throws IOException {
    try (FileInputStream fis = new FileInputStream(file)) {
      Properties properties = new Properties();
      properties.load(fis);
      return properties;
    } catch (IllegalArgumentException e) {
      // 不正なUnicodeエスケープ
      throw new IOException(file + " is malformed", e);
    }
  }

  /**
   * 実パス(シンボリックリンク・8.3短縮名などを解決済み)が、公開ディレクトリ内で、かつ`.`で始まる名前を含まないか確認する.
   *
   * <p>リクエストの表記では隠しファイルと分からない短縮名や、公開ディレクトリ外へのシンボリックリンクを拒否する.
   */
  private boolean isPublicRealPath(Path path) {
    Path real;
    try {
      real = path.toRealPath();
    } catch (IOException e) {
      return false;
    }
    return real.startsWith(htmlRealPath) && !containsHiddenName(htmlRealPath.relativize(real));
  }

  /** 相対パスの各要素に`.`で始まるものがあるか(解決後の最終確認). */
  private static boolean containsHiddenName(Path relative) {
    for (Path name : relative) {
      if (name.toString().startsWith(".")) {
        return true;
      }
    }
    return false;
  }

  /**
   * ディレクトリから、公開ディレクトリからの相対ディレクトリキー(`/`始まり`/`終わり)を作る.
   *
   * <p>リクエスト文字列ではなく、実パスの表記(大文字小文字など)から作ることで、表記ゆれによる認証の迂回を防ぐ.
   * 公開ディレクトリの外に解決される(シンボリックリンクなど)場合は、論理パスから作る.
   */
  String directoryKey(Path directory) {
    Path absolute = directory.toAbsolutePath().normalize();
    Path real = toRealPathOrSelf(absolute);
    String relative =
        real.startsWith(htmlRealPath)
            ? htmlRealPath.relativize(real).toString()
            : htmlPath.relativize(absolute).toString();
    if (relative.isEmpty()) {
      return "/";
    }
    return "/" + relative.replace(File.separatorChar, '/') + "/";
  }

  private static Path toRealPathOrSelf(Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return path;
    }
  }

  /**
   * If-Modified-Since以降に更新されていない(304を返してよい)場合にtrueを返す.
   *
   * <p>HTTP日付は秒精度なので、ファイル更新時刻を秒に切り捨てて比較する. ファイルが存在しない場合はfalseを返す.
   */
  @Override
  public boolean isNotModified(String filePath, TemporalAccessor ifModified) {
    WebFile file = find(filePath);
    if (file == null) {
      return false;
    }
    return !file.getLastModified()
        .toInstant()
        .truncatedTo(ChronoUnit.SECONDS)
        .isAfter(Instant.from(ifModified));
  }

  @Override
  public boolean exists(String filePath) {
    return find(filePath) != null;
  }

  /**
   * Basic認証を検証する. ヘッダーが不正な場合も例外にせず、認証失敗として扱う.
   *
   * @param webFile リクエスト対象のファイル(認証情報を保持している)
   * @param headMap リクエストヘッダー
   * @return 認証できた場合true
   */
  boolean authenticate(WebFile webFile, Map<String, String> headMap) {
    return BasicAuthenticator.authenticate(headMap.get("Authorization"), webFile.getBasic());
  }

  @Override
  public void request(
      String fileName,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream outputStream)
      throws IOException {
    respond(fileName, headMap, outputStream, true);
  }

  @Override
  public void requestHead(
      String fileName,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream outputStream)
      throws IOException {
    respond(fileName, headMap, outputStream, false);
  }

  private void respond(
      String fileName, Map<String, String> headMap, OutputStream outputStream, boolean withBody)
      throws IOException {

    WebFile file = find(fileName);
    if (file == null) {
      // existsの確認後に削除された場合
      error("404", outputStream);
      return;
    }
    try (PrintStream ps = new PrintStream(outputStream); ) {
      if (file.isBasic()) {
        if (!authenticate(file, headMap)) {
          // 認証エラー
          error("401", "WWW-Authenticate: Basic realm=\"SECRET AREA\"\r\n", outputStream);
          return;
        }
      }
      // 通常ファイルの場合
      long len = file.length();
      ps.print("HTTP/1.1 200 OK\r\n");
      ps.print("Date: ");
      ps.print(Constants.formatter.format(OffsetDateTime.now(ZoneOffset.UTC)));
      ps.print("\r\n");
      ps.print("Accept-Ranges: none\r\n");
      ps.print("Connection: close\r\n");
      if (len != 0) {
        ps.print("Content-Length: ");
        ps.print(String.valueOf(len));
        ps.print("\r\n");
      }
      ps.print("Last-Modified: ");
      ps.print(Constants.formatter.format(file.getLastModified()));
      ps.print("\r\n");
      ps.print("Content-Type: ");
      ps.print(file.getContentType());
      ps.print("\r\n");
      ps.print("Expires: ");
      ps.print(Constants.formatter.format(file.getLastModified().plusDays(1)));
      ps.print("\r\n\r\n");
      //			byte[] bytes = null;
      //			if ("text/html".equals(file.getContentType())) {
      //				bytes = new byte[2 * 1024];
      //			} else {
      //				bytes = new byte[64 * 1024];
      //			}

      if (withBody) {
        file.write(ps);
      }
      ps.flush();
    }
  }

  /**
   * ディレクトリ配下の設定ファイル(.basic, .forward)を読み込む。
   *
   * @param file 設定ファイルを探すファイルまたはディレクトリ
   */
  public void loadSettings(File file) {
    if (file.isFile()) {
      if (file.getName().startsWith(".")) {
        // 隠しファイルは表示しない
        if (".basic".equals(file.getName())) {
          String relativePath = directoryKey(file.getParentFile().toPath());
          // Basicファイル認証がある場合
          // 読み込めない場合は認証なしで公開しないよう、起動を失敗させる
          try {
            basicMap.put(relativePath, loadProperties(file));
            basicModifiedMap.put(relativePath, file.lastModified());
          } catch (IOException e) {
            throw new UncheckedIOException(file + " can not be loaded", e);
          }
        } else if (".forward".equals(file.getName())) {
          String relativePath = directoryKey(file.getParentFile().toPath());
          try (FileInputStream fis = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(fis);
            forwardMap.put(relativePath, properties);
          } catch (IOException e) {
            LOGGER.log(Level.WARNING, file + " can not be loaded", e);
          }
        }
      }
    } else if (file.isDirectory()) {
      File[] childs = file.listFiles();
      if (childs == null) {
        throw new UncheckedIOException(new IOException(file + " can not be listed"));
      }
      for (File childFile : childs) {
        loadSettings(childFile);
      }
    }
  }

  @Override
  public void error(String code, OutputStream outputStream) throws IOException {
    error(code, "", outputStream);
  }

  /**
   * エラー応答を返す. 本文は常に空で、空行とContent-Length: 0で終端する.
   *
   * @param extraHeaders 追加するヘッダー(各行をCRLFで終端したもの)
   */
  @Override
  public void error(String code, String extraHeaders, OutputStream outputStream)
      throws IOException {
    try (PrintStream ps = new PrintStream(outputStream); ) {
      ps.print("HTTP/1.1 ");
      ps.print(code);
      ps.print(" ");
      ps.print(reasonPhrase(code));
      ps.print("\r\n");
      ps.print("Date: ");
      ps.print(Constants.formatter.format(OffsetDateTime.now(ZoneOffset.UTC)));
      ps.print("\r\n");
      ps.print(extraHeaders);
      ps.print("Connection: close\r\n");
      ps.print("Content-Length: ");
      ps.print("0\r\n");
      ps.print("\r\n");
      ps.flush();
    }
  }

  static String reasonPhrase(String code) {
    return switch (code) {
      case "400" -> "Bad Request";
      case "401" -> "Unauthorized";
      case "403" -> "Forbidden";
      case "404" -> "Not Found";
      case "405" -> "Method Not Allowed";
      case "408" -> "Request Timeout";
      case "411" -> "Length Required";
      case "413" -> "Payload Too Large";
      case "414" -> "URI Too Long";
      case "431" -> "Request Header Fields Too Large";
      case "500" -> "Internal Server Error";
      case "501" -> "Not Implemented";
      case "502" -> "Bad Gateway";
      case "503" -> "Service Unavailable";
      case "504" -> "Gateway Timeout";
      default -> "Error";
    };
  }

  @Override
  public void forward(
      String filePath,
      String[] heads,
      BufferedReader reader,
      OutputStream outputStream,
      String clientAddress)
      throws IOException {
    String forwardKey = filePath.substring(0, filePath.indexOf('/', 1) + 1);
    Properties prop = forwardMap.get(forwardKey);
    if (prop == null) {
      error("404", outputStream);
      return;
    }
    String host = prop.getProperty("host");
    int port;
    try {
      port = Integer.parseInt(prop.getProperty("port"));
    } catch (NumberFormatException e) {
      // 設定不備
      error("502", outputStream);
      return;
    }
    if (host == null) {
      error("502", outputStream);
      return;
    }
    // readerはISO-8859-1で生成されている前提(1文字=1バイト)で、ボディのバイナリを壊さない
    // 先にヘッダーを検証し、転送先には不完全なリクエストを送らない
    int contentLength = 0;
    int headerCount = 0;
    String hostHeader = null;
    String forwardedFor = null;
    String authorization = null;
    // .forward と同じディレクトリ直下の .basic がある場合のみ認証する
    Properties basic = basicMap.get(forwardKey);
    List<String> headers = new ArrayList<>();
    String line;
    try {
      line = LineReader.readLine(reader);
    } catch (LineReader.LineTooLongException e) {
      error("431", outputStream);
      return;
    }
    while (line != null && !"".equals(line)) {
      if (++headerCount > Constants.MAX_HEADER_COUNT) {
        error("431", outputStream);
        return;
      }
      int index = line.indexOf(':');
      String name = index > 0 ? line.substring(0, index).trim().toLowerCase(Locale.ROOT) : "";
      String value = index > 0 ? line.substring(index + 1).trim() : "";
      switch (name) {
        case "content-length" -> {
          try {
            contentLength = Integer.parseInt(value);
          } catch (NumberFormatException e) {
            error("400", outputStream);
            return;
          }
          if (contentLength < 0 || contentLength > Constants.MAX_CONTENT_LENGTH) {
            error("400", outputStream);
            return;
          }
          headers.add(line);
        }
        case "transfer-encoding" -> {
          // chunked などのボディは解釈しないので明示的に拒否する
          error("501", outputStream);
          return;
        }
        case "connection", "keep-alive", "proxy-connection" -> {}
        case "x-forwarded-for" ->
            forwardedFor = forwardedFor == null ? value : forwardedFor + ", " + value;
        case "x-forwarded-proto", "x-forwarded-host" -> {
          // クライアントによる詐称を避けるため自前の値で置き換える
        }
        case "host" -> {
          hostHeader = value;
          headers.add(line);
        }
        case "authorization" -> {
          if (basic == null) {
            // .basic がなければ転送先の認証用にそのまま転送する
            headers.add(line);
          } else if (authorization == null) {
            // .basic の資格情報は転送先に渡さない
            authorization = value;
          }
        }
        default -> headers.add(line);
      }
      try {
        line = LineReader.readLine(reader);
      } catch (LineReader.LineTooLongException e) {
        error("431", outputStream);
        return;
      }
    }
    if (basic != null && !BasicAuthenticator.authenticate(authorization, basic)) {
      error("401", "WWW-Authenticate: Basic realm=\"SECRET AREA\"\r\n", outputStream);
      return;
    }
    headers.add(
        "X-Forwarded-For: " + (forwardedFor == null ? "" : forwardedFor + ", ") + clientAddress);
    headers.add("X-Forwarded-Proto: http");
    if (hostHeader != null) {
      headers.add("X-Forwarded-Host: " + hostHeader);
    }
    headers.add("Connection: close");
    // 転送先から応答を返し始めたあとは、ステータスを書けないので接続を閉じるだけにする
    boolean responded = false;
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), forwardConnectTimeout);
      socket.setSoTimeout(forwardReadTimeout);
      OutputStream os = socket.getOutputStream();
      StringBuilder sb =
          new StringBuilder(heads[0]).append(' ').append(heads[1]).append(' ').append(heads[2]);
      sb.append("\r\n");
      for (String header : headers) {
        sb.append(header).append("\r\n");
      }
      sb.append("\r\n");
      os.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
      // readerはISO-8859-1で生成されている前提(1文字=1バイト)で、ボディのバイナリを壊さない
      if (contentLength > 0) {
        char[] chars = new char[contentLength];
        int index = 0;
        int read;
        while (index < contentLength
            && (read = reader.read(chars, index, contentLength - index)) > 0) {
          index += read;
        }
        os.write(new String(chars, 0, index).getBytes(StandardCharsets.ISO_8859_1));
      }
      os.flush();
      InputStream is = socket.getInputStream();
      byte[] bytes = new byte[4 * 1024];
      int length = 0;
      while ((length = is.read(bytes)) > 0) {
        responded = true;
        outputStream.write(bytes, 0, length);
        outputStream.flush();
      }
    } catch (IOException e) {
      if (responded) {
        return;
      }
      error(e instanceof SocketTimeoutException ? "504" : "502", outputStream);
    }
  }

  @Override
  public boolean isForward(String filePath) {
    return forwardMap.containsKey(filePath.substring(0, filePath.indexOf('/', 1) + 1));
  }
}
