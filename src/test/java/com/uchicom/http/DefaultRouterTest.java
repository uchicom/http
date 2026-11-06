// (C) 2012 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultRouterTest {

  @TempDir File baseDir;

  File htmlDir;

  DefaultRouter router;

  @BeforeEach
  public void setUp() {
    router = new DefaultRouter();
    htmlDir = new File(baseDir, "html");
    htmlDir.mkdirs();
    router.setHtmlDir(htmlDir);
  }

  private void write(File dir, String name, String content) throws Exception {
    Files.writeString(new File(dir, name).toPath(), content);
  }

  private String request(String path, Map<String, String> headMap) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    router.request(path, new HashMap<>(), headMap, out);
    return out.toString(StandardCharsets.UTF_8);
  }

  private void prepareBasic() {
    router.loadSettings(htmlDir);
  }

  private Map<String, String> authHeader(String credentials) {
    Map<String, String> head = new HashMap<>();
    head.put(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
    return head;
  }

  @Test
  public void findServesExistingFilesOnly() throws Exception {
    write(htmlDir, "index.html", "top");
    File sub = new File(htmlDir, "sub");
    sub.mkdirs();
    write(sub, "a.txt", "a");

    assertThat(router.exists("/index.html")).isTrue();
    assertThat(router.exists("/sub/a.txt")).isTrue();
    assertThat(router.exists("/none.html")).isFalse();
  }

  @Test
  public void findHonorsCacheSize() throws Exception {
    write(htmlDir, "small.txt", "12345");
    write(htmlDir, "large.txt", "1234567890");

    router.cacheSize = 0;
    assertThat(router.find("/small.txt").isCacheable()).isFalse();

    router.cacheSize = 5;
    assertThat(router.find("/small.txt").isCacheable()).isTrue();
    assertThat(router.find("/large.txt").isCacheable()).isFalse();
  }

  @Test
  public void findReflectsFilesAddedAndRemovedAfterInit() throws Exception {
    assertThat(router.exists("/late.txt")).isFalse();

    write(htmlDir, "late.txt", "x");
    assertThat(router.exists("/late.txt")).isTrue();

    Files.delete(new File(htmlDir, "late.txt").toPath());
    assertThat(router.exists("/late.txt")).isFalse();
  }

  @Test
  public void findRejectsDirectoryHiddenDirectoryAndTraversal() throws Exception {
    new File(htmlDir, "sub").mkdirs();
    File hidden = new File(htmlDir, ".dir");
    hidden.mkdirs();
    write(hidden, "a.txt", "x");
    write(baseDir, "outside.txt", "x");

    assertThat(router.exists("/sub")).isFalse();
    assertThat(router.exists("/.dir/a.txt")).isFalse();
    assertThat(router.exists("/../outside.txt")).isFalse();
    assertThat(router.exists("/sub/../../outside.txt")).isFalse();
  }

  @Test
  public void findRejectsBackslashPaths() throws Exception {
    File sub = new File(htmlDir, "sub");
    sub.mkdirs();
    write(sub, ".basic", "user=pass\n");
    write(htmlDir, ".x", "x");

    for (String path : new String[] {"/sub\\.basic", "/sub/..\\.basic", "/.\\x", "/sub\\a.txt"}) {
      assertThat(router.exists(path)).as(path).isFalse();
      assertThat(request(path, new HashMap<>())).as(path).doesNotStartWith("HTTP/1.1 200");
    }
  }

  @Test
  public void findRejectsHiddenFilesByShortName() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    File sub = new File(htmlDir, "sub");
    sub.mkdirs();
    write(sub, ".basic", "user=pass\n");
    write(sub, ".forward", "host=localhost\nport=1\n");
    write(sub, "a.txt", "x");
    // 8.3短縮名が生成されない環境では対象外
    String shortBasic = shortName(sub, ".basic");
    String shortForward = shortName(sub, ".forward");
    Assumptions.assumeTrue(shortBasic != null && shortForward != null);

    assertThat(router.exists("/sub/" + shortBasic)).isFalse();
    assertThat(router.exists("/sub/" + shortForward)).isFalse();
    assertThat(request("/sub/" + shortBasic, new HashMap<>())).doesNotStartWith("HTTP/1.1 200");
    assertThat(router.exists("/sub/a.txt")).isTrue();
  }

  /** `dir /x`で、指定の名前に対応する8.3短縮名を返す. 無い場合はnull. */
  private String shortName(File dir, String name) throws Exception {
    Process process =
        new ProcessBuilder("cmd", "/c", "dir", "/x", dir.getAbsolutePath())
            .redirectErrorStream(true)
            .start();
    String output =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
    process.waitFor();
    for (String line : output.split("\r?\n", 0)) {
      if (line.endsWith(" " + name)) {
        for (String token : line.trim().split("\\s+", 0)) {
          if (token.contains("~")) {
            return token;
          }
        }
      }
    }
    return null;
  }

  @Test
  public void findRejectsSymbolicLinkOutsideHtml() throws Exception {
    write(baseDir, "outside.txt", "secret");
    File sub = new File(htmlDir, "sub");
    sub.mkdirs();
    write(sub, "inside.txt", "x");
    try {
      Files.createSymbolicLink(
          new File(htmlDir, "link.txt").toPath(), new File(baseDir, "outside.txt").toPath());
      Files.createSymbolicLink(new File(htmlDir, "linkdir").toPath(), baseDir.toPath());
      Files.createSymbolicLink(
          new File(htmlDir, "inside-link.txt").toPath(), new File(sub, "inside.txt").toPath());
    } catch (UnsupportedOperationException | java.io.IOException e) {
      Assumptions.abort("symbolic link is not available");
    }

    assertThat(router.exists("/link.txt")).isFalse();
    assertThat(router.exists("/linkdir/outside.txt")).isFalse();
    assertThat(router.exists("/inside-link.txt")).isTrue();
  }

  @Test
  public void findAppliesBasicToFilesAddedAfterInit() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    router.loadSettings(htmlDir);
    write(htmlDir, "late.txt", "x");

    assertThat(router.find("/late.txt").isBasic()).isTrue();
  }

  @Test
  public void requestReflectsUpdatedLastModifiedWithoutCache() throws Exception {
    write(htmlDir, "a.txt", "old");
    File file = new File(htmlDir, "a.txt");
    file.setLastModified(1_000_000_000_000L);
    OffsetDateTime before = router.find("/a.txt").getLastModified();

    write(htmlDir, "a.txt", "new");
    file.setLastModified(1_100_000_000_000L);

    assertThat(router.find("/a.txt").getLastModified()).isAfter(before);
    assertThat(router.isNotModified("/a.txt", before)).isFalse();
    assertThat(request("/a.txt", new HashMap<>())).endsWith("new");
  }

  @Test
  public void hiddenFilesAreNotServed() throws Exception {
    write(htmlDir, ".hidden", "x");

    assertThat(router.exists("/.hidden")).isFalse();
  }

  @Test
  public void loadSettingsLoadsBasicAndForward() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    File fwd = new File(htmlDir, "fwd");
    fwd.mkdirs();
    write(fwd, ".forward", "host=localhost\nport=8080\n");

    router.loadSettings(htmlDir);

    assertThat(router.basicMap.get("/").getProperty("user")).isEqualTo("pass");
    assertThat(router.isForward("/fwd/x")).isTrue();
    assertThat(router.isForward("/other/x")).isFalse();
  }

  @Test
  public void loadSettingsFailsWhenBasicCannotBeLoaded() throws Exception {
    // .basicがファイルでなく読めない(ディレクトリ扱い)ケースは再現しづらいため、不正なUnicodeエスケープで読み込み失敗させる
    write(htmlDir, ".basic", "user=\\uZZZZ\n");

    org.assertj.core.api.Assertions.assertThatThrownBy(() -> router.loadSettings(htmlDir))
        .isInstanceOf(java.io.UncheckedIOException.class);
  }

  @Test
  public void loadSettingsFailsWhenDirectoryCannotBeListed() throws Exception {
    File missing = new File(htmlDir, "missing");
    File notListable =
        new File(htmlDir, "x") {
          @Override
          public boolean isDirectory() {
            return true;
          }

          @Override
          public File[] listFiles() {
            return null;
          }
        };
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> router.loadSettings(notListable))
        .isInstanceOf(java.io.UncheckedIOException.class);
    assertThat(missing).doesNotExist();
  }

  @Test
  public void basicAddedAfterStartupIsApplied() throws Exception {
    router.loadSettings(htmlDir);
    write(htmlDir, "a.txt", "a");
    assertThat(router.find("/a.txt").isBasic()).isFalse();

    write(htmlDir, ".basic", "user=pass\n");

    assertThat(router.find("/a.txt").isBasic()).isTrue();
    assertThat(request("/a.txt", new HashMap<>())).startsWith("HTTP/1.1 401");
  }

  @Test
  public void brokenBasicAddedAfterStartupDeniesAccess() throws Exception {
    router.loadSettings(htmlDir);
    write(htmlDir, "a.txt", "a");
    write(htmlDir, ".basic", "user=\\uZZZZ\n");

    assertThat(router.exists("/a.txt")).isFalse();
  }

  @Test
  public void isNotModified() throws Exception {
    write(htmlDir, "a.txt", "a");

    assertThat(router.isNotModified("/a.txt", router.find("/a.txt").getLastModified())).isTrue();
    assertThat(router.isNotModified("/a.txt", OffsetDateTime.MIN)).isFalse();
  }

  @Test
  public void isNotModifiedComparesInSeconds() throws Exception {
    write(htmlDir, "a.txt", "a");
    OffsetDateTime lastModified = router.find("/a.txt").getLastModified();
    // 実際のIf-Modified-Sinceと同じく、秒精度のHTTP日付文字列から解析する
    TemporalAccessor same = Constants.formatter.parse(Constants.formatter.format(lastModified));
    TemporalAccessor older =
        Constants.formatter.parse(Constants.formatter.format(lastModified.minusSeconds(10)));
    TemporalAccessor newer =
        Constants.formatter.parse(Constants.formatter.format(lastModified.plusSeconds(10)));

    assertThat(router.isNotModified("/a.txt", same)).isTrue();
    assertThat(router.isNotModified("/a.txt", newer)).isTrue();
    assertThat(router.isNotModified("/a.txt", older)).isFalse();
  }

  @Test
  public void isNotModifiedMissingFile() {
    assertThat(router.isNotModified("/none.txt", OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
  }

  @Test
  public void requestNormalFile() throws Exception {
    write(htmlDir, "a.html", "body");

    String response = request("/a.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 200 OK");
    assertThat(response).contains("Content-Length: 4");
    assertThat(response).contains("Content-Type: text/html");
    assertThat(response).endsWith("\r\n\r\nbody");
  }

  @Test
  public void requestBasicWithoutAuthorization() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();

    String response = request("/a.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 401");
    assertThat(response).contains("WWW-Authenticate: Basic");
  }

  @Test
  public void requestBasicIsNotBypassedByDoubleSlash() throws Exception {
    File secret = new File(htmlDir, "secret");
    secret.mkdirs();
    write(secret, ".basic", "user=pass\n");
    write(secret, "a.html", "body");
    prepareBasic();

    for (String path : new String[] {"/secret//a.html", "/secret///a.html"}) {
      assertThat(request(path, new HashMap<>())).as(path).startsWith("HTTP/1.1 401");
      assertThat(router.find(path).isBasic()).as(path).isTrue();
      assertThat(request(path, authHeader("user:pass"))).as(path).startsWith("HTTP/1.1 200");
    }
  }

  @Test
  public void requestBasicIsNotBypassedByCaseVariationOnCaseInsensitiveFileSystem()
      throws Exception {
    File secret = new File(htmlDir, "secret");
    secret.mkdirs();
    write(secret, ".basic", "user=pass\n");
    write(secret, "a.html", "body");
    prepareBasic();
    Assumptions.assumeTrue(new File(htmlDir, "SECRET/A.HTML").isFile());

    assertThat(request("/SECRET/a.html", new HashMap<>())).startsWith("HTTP/1.1 401");
    assertThat(request("/secret/A.HTML", new HashMap<>())).startsWith("HTTP/1.1 401");
    assertThat(request("/SECRET/a.html", authHeader("user:pass"))).startsWith("HTTP/1.1 200");
  }

  @Test
  public void requestBasicIsNotBypassedByTrailingDotOnWindows() throws Exception {
    Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
    File secret = new File(htmlDir, "secret");
    secret.mkdirs();
    write(secret, ".basic", "user=pass\n");
    write(secret, "a.html", "body");
    prepareBasic();

    assertThat(request("/secret./a.html", new HashMap<>())).doesNotStartWith("HTTP/1.1 200");
  }

  @Test
  public void requestBasicWithValidAuthorization() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();

    assertThat(request("/a.html", authHeader("user:pass"))).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void requestBasicWithWrongPassword() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();

    assertThat(request("/a.html", authHeader("user:wrong"))).startsWith("HTTP/1.1 401");
  }

  @Test
  public void requestBasicWithMalformedAuthorization() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();

    for (String authorization :
        new String[] {
          "Basic",
          "token",
          "Basic !!!not-base64!!!",
          "Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes(StandardCharsets.UTF_8)),
          "Basic "
              + Base64.getEncoder().encodeToString("unknown:pass".getBytes(StandardCharsets.UTF_8)),
          "Bearer "
              + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8))
        }) {
      Map<String, String> head = new HashMap<>();
      head.put("Authorization", authorization);

      String response = request("/a.html", head);

      // 例外にならず、401のみを返す(400と二重に書かない)
      assertThat(response).as(authorization).startsWith("HTTP/1.1 401");
      assertThat(response).as(authorization).doesNotContain("400");
    }
  }

  @Test
  public void authenticateWithoutBasicProperties() throws Exception {
    assertThat(
            router.authenticate(new WebFile(new File(htmlDir, "a.html")), authHeader("user:pass")))
        .isFalse();
  }

  @Test
  public void requestBasicDecodesAsUtf8() throws Exception {
    // .basicはProperties(ISO-8859-1)で読まれるため、ASCII以外はUnicodeエスケープで書く
    write(htmlDir, ".basic", "user=p\\u00e4ss\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();
    Map<String, String> head = new HashMap<>();
    head.put(
        "Authorization",
        "Basic "
            + Base64.getEncoder().encodeToString("user:päss".getBytes(StandardCharsets.UTF_8)));

    assertThat(request("/a.html", head)).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void errorHasNoBody() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    router.error("500", out);

    String response = out.toString(StandardCharsets.UTF_8);
    assertThat(response).startsWith("HTTP/1.1 500 Internal Server Error\r\n");
    assertThat(response).doesNotContain("Server:");
    assertThat(response).doesNotContain("Content-Type:");
    assertThat(response).contains("Content-Length: 0\r\n");
    assertThat(response).endsWith("\r\n\r\n");
  }

  @Test
  public void errorTerminatesHeaders() throws Exception {
    for (String code : new String[] {"401", "404", "502"}) {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      router.error(code, out);
      assertThat(out.toString(StandardCharsets.UTF_8)).as(code).contains("\r\n\r\n");
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    router.error("502", out);
    assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("HTTP/1.1 502 Bad Gateway\r\n");
  }

  @Test
  public void request401IsWellFormed() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();

    String response = request("/a.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 401 Unauthorized\r\n");
    assertThat(response).contains("WWW-Authenticate: Basic");
    assertThat(response).doesNotContain("Transfer-Encoding");
    assertThat(response).endsWith("\r\n\r\n");
  }

  private static Properties forwardProperties(String host, String port) {
    Properties properties = new Properties();
    if (host != null) {
      properties.setProperty("host", host);
    }
    if (port != null) {
      properties.setProperty("port", port);
    }
    return properties;
  }

  private String forward(String request) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    router.forward(
        "/fwd/x",
        new String[] {"POST", "/fwd/x", "HTTP/1.1"},
        new BufferedReader(new StringReader(request)),
        out,
        "192.0.2.1");
    return out.toString(StandardCharsets.UTF_8);
  }

  /** 転送先に届いたヘッダー部を返す. 転送先は応答を返したあと接続を閉じない. */
  private String forwardAndCaptureHeaders(String request) throws Exception {
    StringBuilder received = new StringBuilder();
    try (ServerSocket server = new ServerSocket(0)) {
      Thread thread =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  InputStream in = socket.getInputStream();
                  while (!received.toString().endsWith("\r\n\r\n")) {
                    int b = in.read();
                    if (b < 0) {
                      return;
                    }
                    received.append((char) b);
                  }
                  socket
                      .getOutputStream()
                      .write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                  socket.getOutputStream().flush();
                  // keep-alive 指定なら閉じずに待つ転送先を模す
                  if (!received.toString().contains("Connection: close\r\n")) {
                    Thread.sleep(3000);
                  }
                } catch (Exception e) {
                  // テスト終了時の切断は無視
                }
              });
      thread.start();
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));
      long start = System.currentTimeMillis();
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      router.forwardReadTimeout = 5000;
      router.forward(
          "/fwd/x",
          new String[] {"GET", "/fwd/x", "HTTP/1.1"},
          new BufferedReader(new StringReader(request)),
          out,
          "192.0.2.1");
      thread.join(5000);
      assertThat(System.currentTimeMillis() - start).isLessThan(2500);
      assertThat(out.toString(StandardCharsets.UTF_8)).startsWith("HTTP/1.1 200 OK");
      return received.toString();
    }
  }

  @Test
  public void forwardReplacesConnectionAndAddsForwardedHeaders() throws Exception {
    String received =
        forwardAndCaptureHeaders(
            """
            Host: example.com\r
            Connection: keep-alive\r
            Keep-Alive: timeout=5\r
            X-Forwarded-For: 198.51.100.7\r
            X-Forwarded-Proto: https\r
            \r
            """);

    assertThat(received).contains("Connection: close\r\n");
    assertThat(received).doesNotContain("keep-alive").doesNotContain("Keep-Alive");
    assertThat(received).contains("X-Forwarded-For: 198.51.100.7, 192.0.2.1\r\n");
    assertThat(received).contains("X-Forwarded-Proto: http\r\n").doesNotContain("https");
    assertThat(received).contains("X-Forwarded-Host: example.com\r\n");
  }

  @Test
  public void forwardRejectsTransferEncoding() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));

      assertThat(forward("Transfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n0\r\n\r\n"))
          .startsWith("HTTP/1.1 501");
    }
  }

  private static String basicHeader(String userPass) {
    return "Authorization: Basic "
        + Base64.getEncoder().encodeToString(userPass.getBytes(StandardCharsets.UTF_8))
        + "\r\n";
  }

  private void putForwardBasic() {
    Properties basic = new Properties();
    basic.setProperty("user", "pass");
    router.basicMap.put("/fwd/", basic);
  }

  @Test
  public void forwardWithBasicRejectsMissingOrWrongCredentials() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));
      putForwardBasic();

      String none = forward("\r\n");
      assertThat(none).startsWith("HTTP/1.1 401 Unauthorized\r\n");
      assertThat(none).contains("WWW-Authenticate: Basic");
      assertThat(forward(basicHeader("user:wrong") + "\r\n")).startsWith("HTTP/1.1 401");
    }
  }

  @Test
  public void forwardWithBasicDropsAuthorizationOnSuccess() throws Exception {
    putForwardBasic();

    String received = forwardAndCaptureHeaders(basicHeader("user:pass") + "\r\n");

    assertThat(received).doesNotContain("Authorization");
  }

  @Test
  public void forwardWithoutBasicKeepsAuthorization() throws Exception {
    String received = forwardAndCaptureHeaders(basicHeader("user:pass") + "\r\n");

    assertThat(received).contains("Authorization: Basic ");
  }

  @Test
  public void forwardWithoutSetting() throws Exception {
    assertThat(forward("\r\n")).startsWith("HTTP/1.1 404");
  }

  @Test
  public void forwardWithInvalidSetting() throws Exception {
    router.forwardMap.put("/fwd/", forwardProperties("localhost", "abc"));
    assertThat(forward("\r\n")).startsWith("HTTP/1.1 502");

    router.forwardMap.put("/fwd/", forwardProperties("localhost", null));
    assertThat(forward("\r\n")).startsWith("HTTP/1.1 502");

    router.forwardMap.put("/fwd/", forwardProperties(null, "8080"));
    assertThat(forward("\r\n")).startsWith("HTTP/1.1 502");
  }

  @Test
  public void forwardWithInvalidContentLength() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));
      for (String contentLength : new String[] {"abc", "-1", String.valueOf(Integer.MAX_VALUE)}) {
        assertThat(forward("Content-Length: " + contentLength + "\r\n\r\n"))
            .as(contentLength)
            .startsWith("HTTP/1.1 400");
      }
    }
  }

  @Test
  public void forwardRejectsTooLongHeaderLine() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));
      String longLine = "X-Long: " + "a".repeat(Constants.MAX_LINE_LENGTH);
      assertThat(forward(longLine + "\r\n\r\n")).startsWith("HTTP/1.1 431");
      // 改行なしで巨大なデータが来ても上限で打ち切る
      assertThat(forward("a".repeat(Constants.MAX_LINE_LENGTH * 4))).startsWith("HTTP/1.1 431");
    }
  }

  @Test
  public void forwardRejectsTooManyHeaders() throws Exception {
    try (ServerSocket server = new ServerSocket(0)) {
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));
      String headers = "X-A: b\r\n".repeat(Constants.MAX_HEADER_COUNT + 1);
      assertThat(forward(headers + "\r\n")).startsWith("HTTP/1.1 431");
    }
  }

  @Test
  public void forwardKeepsBinaryBody() throws Exception {
    byte[] body = new byte[256];
    for (int i = 0; i < body.length; i++) {
      body[i] = (byte) i;
    }
    ByteArrayOutputStream received = new ByteArrayOutputStream();
    try (ServerSocket server = new ServerSocket(0)) {
      Thread thread =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  InputStream in = socket.getInputStream();
                  // ヘッダー終端までを読み捨て、続くボディを受信する
                  int matched = 0;
                  while (matched < 4) {
                    int b = in.read();
                    if (b < 0) {
                      return;
                    }
                    matched = (b == "\r\n\r\n".charAt(matched)) ? matched + 1 : (b == '\r' ? 1 : 0);
                  }
                  received.write(in.readNBytes(body.length));
                  socket
                      .getOutputStream()
                      .write("HTTP/1.1 200 OK\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                  throw new IllegalStateException(e);
                }
              });
      thread.start();
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));

      String response =
          forward("Content-Length: 256\r\n\r\n" + new String(body, StandardCharsets.ISO_8859_1));
      thread.join(5000);

      assertThat(received.toByteArray()).isEqualTo(body);
      assertThat(response).startsWith("HTTP/1.1 200 OK");
    }
  }

  @Test
  public void forwardConnectionRefused() throws Exception {
    int port;
    try (ServerSocket server = new ServerSocket(0)) {
      port = server.getLocalPort();
    }
    router.forwardMap.put("/fwd/", forwardProperties("localhost", String.valueOf(port)));

    assertThat(forward("\r\n")).startsWith("HTTP/1.1 502 Bad Gateway\r\n");
  }

  @Test
  public void forwardReadTimeout() throws Exception {
    router.forwardReadTimeout = 200;
    try (ServerSocket server = new ServerSocket(0)) {
      // 接続は受け付けるが応答しない
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));

      assertThat(forward("\r\n")).startsWith("HTTP/1.1 504 Gateway Timeout\r\n");
    }
  }

  @Test
  public void forwardFailureAfterResponseStartedOnlyCloses() throws Exception {
    router.forwardReadTimeout = 200;
    try (ServerSocket server = new ServerSocket(0)) {
      Thread thread =
          new Thread(
              () -> {
                try (Socket socket = server.accept()) {
                  socket
                      .getOutputStream()
                      .write("HTTP/1.1 200 OK\r\n".getBytes(StandardCharsets.UTF_8));
                  socket.getOutputStream().flush();
                  // 続きを返さず待つ
                  Thread.sleep(1000);
                } catch (Exception e) {
                  // テスト終了時の切断は無視
                }
              });
      thread.start();
      router.forwardMap.put(
          "/fwd/", forwardProperties("localhost", String.valueOf(server.getLocalPort())));

      String response = forward("\r\n");
      thread.join(5000);

      assertThat(response).isEqualTo("HTTP/1.1 200 OK\r\n");
    }
  }
}
