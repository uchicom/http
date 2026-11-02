// (C) 2012 uchicom
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Uchiyama Shigeki
 */
public class DefaultRouter implements Router {

  /** キャッシュ内容を保持するため、リクエストをまたいで使い回すWebFile(キャッシュ有効時のみ使用) */
  protected final Map<String, WebFile> webFileMap = new ConcurrentHashMap<>();

  protected final Map<String, Properties> basicMap = new ConcurrentHashMap<>();
  protected final Map<String, Properties> forwardMap = new ConcurrentHashMap<>();
  protected final Map<String, File> errorMap = new ConcurrentHashMap<>();

  /** 公開ディレクトリ(正規化済みの絶対パス) */
  protected Path htmlPath;

  /** 公開ディレクトリの実パス(シンボリックリンク・大文字小文字・短縮名を解決済み。解決できない場合はhtmlPathと同じ) */
  protected Path htmlRealPath;

  /** キャッシュ対象の最大ファイルサイズ(バイト)。0以下はキャッシュなし */
  protected long cacheSize;

  /** forward先への接続タイムアウト(ミリ秒) */
  private static final int FORWARD_CONNECT_TIMEOUT = 5000;

  /** forward先からの読み込みタイムアウト(ミリ秒) */
  private static final int FORWARD_READ_TIMEOUT = 30000;

  /*
   * (non-Javadoc)
   *
   * @see com.uchicom.server.http.Router#init(java.io.File)
   */
  @Override
  public void init(File baseFile, long cacheSize) {
    this.cacheSize = cacheSize;
    File htmlFile = new File(baseFile, "html");
    setHtmlDir(htmlFile);
    loadSettings(htmlFile);
    createErrorMap(new File(baseFile, "error"));
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
    for (String name : filePath.split("/")) {
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
    if (!file.isFile()) {
      webFileMap.remove(filePath);
      return null;
    }
    WebFile webFile;
    if (cacheSize > 0) {
      webFile = webFileMap.computeIfAbsent(filePath, key -> new WebFile(file, cacheSize));
    } else {
      webFile = new WebFile(file, 0);
    }
    webFile.setBasic(basicMap.get(directoryKey(path.getParent())));
    return webFile;
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

  /*
   * (non-Javadoc)
   *
   * @see com.uchicom.server.http.Router#exists(java.lang.String)
   */
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

  /*
   * (non-Javadoc)
   *
   * @see com.uchicom.server.http.Router#request(java.lang.String,
   * java.util.Map, java.io.OutputStream)
   */
  @Override
  public void request(
      String fileName,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream outputStream)
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
      ps.print("HTTP/1.1 200 OK \r\n");
      ps.print("Date: ");
      ps.print(Constants.formatter.format(OffsetDateTime.now()));
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

      file.write(ps);
      ps.flush();
    }
  }

  /**
   * ディレクトリ配下の設定ファイル(.basic, .forward)を読み込む。
   *
   * @param file
   */
  public void loadSettings(File file) {
    if (file.isFile()) {
      if (file.getName().startsWith(".")) {
        // 隠しファイルは表示しない
        if (".basic".equals(file.getName())) {
          String relativePath = directoryKey(file.getParentFile().toPath());
          // Basicファイル認証がある場合
          try (FileInputStream fis = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(fis);
            basicMap.put(relativePath, properties);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
        } else if (".forward".equals(file.getName())) {
          String relativePath = directoryKey(file.getParentFile().toPath());
          try (FileInputStream fis = new FileInputStream(file)) {
            Properties properties = new Properties();
            properties.load(fis);
            forwardMap.put(relativePath, properties);
          } catch (FileNotFoundException e) {
            e.printStackTrace();
          } catch (IOException e) {
            e.printStackTrace();
          }
        }
      }
    } else if (file.isDirectory()) {
      File[] childs = file.listFiles();
      for (File childFile : childs) {
        loadSettings(childFile);
      }
    }
  }

  public void createErrorMap(File file) {
    if (file.exists()) {
      if (file.isDirectory()) {
        File[] childs = file.listFiles();
        for (File childFile : childs) {
          errorMap.put(childFile.getName().substring(0, 3), childFile);
        }
      }
    }
  }

  /*
   * (non-Javadoc)
   *
   * @see com.uchicom.server.http.Router#error(java.lang.String,
   * java.io.OutputStream)
   */
  @Override
  public void error(String code, OutputStream outputStream) throws IOException {
    error(code, "", outputStream);
  }

  /**
   * エラー応答を返す. エラーページがない場合も、空行とContent-Length: 0で終端する.
   *
   * @param extraHeaders 追加するヘッダー(各行をCRLFで終端したもの)
   */
  void error(String code, String extraHeaders, OutputStream outputStream) throws IOException {
    File error = errorMap.get(code);
    long len = error != null ? error.length() : 0;
    try (PrintStream ps = new PrintStream(outputStream); ) {
      ps.print("HTTP/1.1 ");
      ps.print(code);
      ps.print(" ");
      ps.print(reasonPhrase(code));
      ps.print("\r\n");
      ps.print("Date: ");
      ps.print(Constants.formatter.format(OffsetDateTime.now()));
      ps.print("\r\n");
      ps.print(extraHeaders);
      ps.print("Connection: close\r\n");
      ps.print("Content-Length: ");
      ps.print(String.valueOf(len));
      ps.print("\r\n");
      if (len > 0) {
        ps.print("Content-Type: text/html; charset=UTF-8\r\n");
      }
      ps.print("\r\n");
      if (len > 0) {
        try (FileInputStream fis = new FileInputStream(error); ) {
          byte[] bytes = new byte[1024];
          int length = 0;
          while ((length = fis.read(bytes)) >= 0) {
            ps.write(bytes, 0, length);
          }
        }
      }
      ps.flush();
    }
  }

  static String reasonPhrase(String code) {
    switch (code) {
      case "400":
        return "Bad Request";
      case "401":
        return "Unauthorized";
      case "403":
        return "Forbidden";
      case "404":
        return "Not Found";
      case "405":
        return "Method Not Allowed";
      case "408":
        return "Request Timeout";
      case "411":
        return "Length Required";
      case "413":
        return "Payload Too Large";
      case "414":
        return "URI Too Long";
      case "500":
        return "Internal Server Error";
      case "501":
        return "Not Implemented";
      case "502":
        return "Bad Gateway";
      case "503":
        return "Service Unavailable";
      case "504":
        return "Gateway Timeout";
      default:
        return "Error";
    }
  }

  /*
   * (非 Javadoc)
   *
   * @see com.uchicom.http.Router#forward(java.io.BufferedReader,
   * java.io.OutputStream)
   */
  @Override
  public void forward(
      String filePath, String[] heads, BufferedReader reader, OutputStream outputStream)
      throws IOException {
    Properties prop = forwardMap.get(filePath.substring(0, filePath.indexOf('/', 1) + 1));
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
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress(host, port), FORWARD_CONNECT_TIMEOUT);
      socket.setSoTimeout(FORWARD_READ_TIMEOUT);
      OutputStream os = socket.getOutputStream();
      os.write(
          (heads[0] + " " + heads[1] + " " + heads[2] + "\r\n")
              .getBytes(StandardCharsets.ISO_8859_1));
      String line = reader.readLine();
      int contentLength = 0;
      while (line != null && !"".equals(line)) {
        if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
          try {
            contentLength = Integer.parseInt(line.substring(15).trim());
          } catch (NumberFormatException e) {
            error("400", outputStream);
            return;
          }
          if (contentLength < 0 || contentLength > Constants.MAX_CONTENT_LENGTH) {
            error("400", outputStream);
            return;
          }
        }
        os.write((line + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        line = reader.readLine();
      }
      os.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
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
        outputStream.write(bytes, 0, length);
        outputStream.flush();
      }
    }
  }

  /*
   * (非 Javadoc)
   *
   * @see com.uchicom.http.Router#isForward(java.lang.String)
   */
  @Override
  public boolean isForward(String filePath) {
    return forwardMap.containsKey(filePath.substring(0, filePath.indexOf('/', 1) + 1));
  }
}
