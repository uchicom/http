// (C) 2012 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultRouterTest {

  @TempDir File baseDir;

  File htmlDir;

  @BeforeEach
  public void setUp() {
    DefaultRouter.fileMap.clear();
    DefaultRouter.basicMap.clear();
    DefaultRouter.forwardMap.clear();
    DefaultRouter.errorMap.clear();
    htmlDir = new File(baseDir, "html");
    htmlDir.mkdirs();
    DefaultRouter.htmlFileLength = htmlDir.toURI().getPath().length();
  }

  private void write(File dir, String name, String content) throws Exception {
    Files.writeString(new File(dir, name).toPath(), content);
  }

  private String request(String path, Map<String, String> headMap) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    new DefaultRouter().request(path, new HashMap<>(), headMap, out);
    return out.toString();
  }

  private void prepareBasic() {
    DefaultRouter.createMap(htmlDir);
    DefaultRouter.fileMap.forEach(
        (name, file) -> {
          String key = name.substring(0, name.lastIndexOf('/') + 1);
          if (DefaultRouter.basicMap.containsKey(key)) {
            file.setBasic(DefaultRouter.basicMap.get(key));
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

    DefaultRouter.createMap(htmlDir);

    DefaultRouter router = new DefaultRouter();
    assertThat(router.exists("/index.html")).isTrue();
    assertThat(router.exists("/sub/a.txt")).isTrue();
    assertThat(router.exists("/none.html")).isFalse();
  }

  @Test
  public void createMapIgnoresHiddenFiles() throws Exception {
    write(htmlDir, ".hidden", "x");

    DefaultRouter.createMap(htmlDir);

    assertThat(new DefaultRouter().exists("/.hidden")).isFalse();
  }

  @Test
  public void createMapLoadsBasicAndForward() throws Exception {
    write(htmlDir, ".basic", "user=pass\n");
    File fwd = new File(htmlDir, "fwd");
    fwd.mkdirs();
    write(fwd, ".forward", "host=localhost\nport=8080\n");

    DefaultRouter.createMap(htmlDir);

    assertThat(DefaultRouter.basicMap.get("/").getProperty("user")).isEqualTo("pass");
    assertThat(new DefaultRouter().isForward("/fwd/x")).isTrue();
    assertThat(new DefaultRouter().isForward("/other/x")).isFalse();
  }

  @Test
  public void createErrorMap() throws Exception {
    File errorDir = new File(baseDir, "error");
    errorDir.mkdirs();
    write(errorDir, "404.html", "not found");

    DefaultRouter.createErrorMap(errorDir);

    assertThat(DefaultRouter.errorMap.get("404")).isEqualTo(new File(errorDir, "404.html"));
  }

  @Test
  public void createErrorMapMissingDirectory() {
    DefaultRouter.createErrorMap(new File(baseDir, "nothing"));

    assertThat(DefaultRouter.errorMap).isEmpty();
  }

  @Test
  public void isModified() throws Exception {
    write(htmlDir, "a.txt", "a");
    DefaultRouter.createMap(htmlDir);
    DefaultRouter router = new DefaultRouter();

    assertThat(router.isModified("/a.txt", DefaultRouter.fileMap.get("/a.txt").getLastModified()))
        .isTrue();
    assertThat(router.isModified("/a.txt", OffsetDateTime.MIN)).isFalse();
  }

  @Test
  public void requestNormalFile() throws Exception {
    write(htmlDir, "a.html", "body");
    DefaultRouter.createMap(htmlDir);

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
    Map<String, String> head = new HashMap<>();
    head.put("Authorization", "Basic !!!not-base64!!!");

    String response = request("/a.html", head);

    // 400のみを返し、続けて401を書かない
    assertThat(response).startsWith("HTTP/1.1 400");
    assertThat(response).doesNotContain("401");
  }

  @Test
  public void errorWithRegisteredPage() throws Exception {
    File errorDir = new File(baseDir, "error");
    errorDir.mkdirs();
    write(errorDir, "404.html", "not found");
    DefaultRouter.createErrorMap(errorDir);
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    new DefaultRouter().error("404", out);

    String response = out.toString();
    assertThat(response).startsWith("HTTP/1.1 404 ERR");
    assertThat(response).endsWith("\r\n\r\nnot found");
  }

  @Test
  public void errorWithoutRegisteredPage() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    new DefaultRouter().error("500", out);

    assertThat(out.toString()).isEqualTo("HTTP/1.1 500 ERR\r\n");
  }
}
