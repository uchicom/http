// (C) 2012 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
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
    router.htmlFileLength = htmlDir.toURI().getPath().length();
  }

  private void write(File dir, String name, String content) throws Exception {
    Files.writeString(new File(dir, name).toPath(), content);
  }

  private String request(String path, Map<String, String> headMap) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    router.request(path, new HashMap<>(), headMap, out);
    return out.toString();
  }

  private void prepareBasic() {
    router.createMap(htmlDir);
    router.fileMap.forEach(
        (name, file) -> {
          String key = name.substring(0, name.lastIndexOf('/') + 1);
          if (router.basicMap.containsKey(key)) {
            file.setBasic(router.basicMap.get(key));
          }
        });
  }

  private Map<String, String> authHeader(String credentials) {
    Map<String, String> head = new HashMap<>();
    head.put(
        "Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes()));
    return head;
  }

  @Test
  public void createMapRegistersFiles() throws Exception {
    write(htmlDir, "index.html", "top");
    File sub = new File(htmlDir, "sub");
    sub.mkdirs();
    write(sub, "a.txt", "a");

    router.createMap(htmlDir);

    assertThat(router.exists("/index.html")).isTrue();
    assertThat(router.exists("/sub/a.txt")).isTrue();
    assertThat(router.exists("/none.html")).isFalse();
  }

  @Test
  public void createMapIgnoresHiddenFiles() throws Exception {
    write(htmlDir, ".hidden", "x");

    router.createMap(htmlDir);

    assertThat(router.exists("/.hidden")).isFalse();
  }

  @Test
  public void createMapLoadsBasicAndForward() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    File fwd = new File(htmlDir, "fwd");
    fwd.mkdirs();
    write(fwd, ".forward", "host=localhost\nport=8080\n");

    router.createMap(htmlDir);

    assertThat(router.basicMap.get("/").getProperty("user")).isEqualTo("pass");
    assertThat(router.isForward("/fwd/x")).isTrue();
    assertThat(router.isForward("/other/x")).isFalse();
  }

  @Test
  public void createErrorMap() throws Exception {
    File errorDir = new File(baseDir, "error");
    errorDir.mkdirs();
    write(errorDir, "404.html", "not found");

    router.createErrorMap(errorDir);

    assertThat(router.errorMap.get("404")).isEqualTo(new File(errorDir, "404.html"));
  }

  @Test
  public void createErrorMapMissingDirectory() {
    router.createErrorMap(new File(baseDir, "nothing"));

    assertThat(router.errorMap).isEmpty();
  }

  @Test
  public void isNotModified() throws Exception {
    write(htmlDir, "a.txt", "a");
    router.createMap(htmlDir);

    assertThat(router.isNotModified("/a.txt", router.fileMap.get("/a.txt").getLastModified()))
        .isTrue();
    assertThat(router.isNotModified("/a.txt", OffsetDateTime.MIN)).isFalse();
  }

  @Test
  public void isNotModifiedComparesInSeconds() throws Exception {
    write(htmlDir, "a.txt", "a");
    router.createMap(htmlDir);
    OffsetDateTime lastModified = router.fileMap.get("/a.txt").getLastModified();
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
    assertThat(router.isNotModified("/none.txt", OffsetDateTime.now())).isFalse();
  }

  @Test
  public void requestNormalFile() throws Exception {
    write(htmlDir, "a.html", "body");
    router.createMap(htmlDir);

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
          "Basic " + Base64.getEncoder().encodeToString("nocolon".getBytes()),
          "Basic " + Base64.getEncoder().encodeToString("unknown:pass".getBytes()),
          "Bearer " + Base64.getEncoder().encodeToString("user:pass".getBytes())
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
  public void requestBasicWithoutBasicProperties() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    write(htmlDir, "a.html", "body");
    prepareBasic();
    router.basicMap.clear();

    assertThat(request("/a.html", authHeader("user:pass"))).startsWith("HTTP/1.1 401");
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
  public void errorWithRegisteredPage() throws Exception {
    File errorDir = new File(baseDir, "error");
    errorDir.mkdirs();
    write(errorDir, "404.html", "not found");
    router.createErrorMap(errorDir);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    router.error("404", out);

    String response = out.toString();
    assertThat(response).startsWith("HTTP/1.1 404 ERR");
    assertThat(response).endsWith("\r\n\r\nnot found");
  }

  @Test
  public void errorWithoutRegisteredPage() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    router.error("500", out);

    assertThat(out.toString()).isEqualTo("HTTP/1.1 500 ERR\r\n");
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
        out);
    return out.toString();
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
                  socket.getOutputStream().write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
                } catch (Exception e) {
                  e.printStackTrace();
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

    // 接続できない場合は例外を呼び出し元に伝える(ソケットはリークしない)
    assertThatThrownBy(() -> forward("\r\n")).isInstanceOf(IOException.class);
  }
}
