// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class StaticHandlerTest {

  @TempDir File baseDir;

  DefaultRouter router;
  StaticHandler handler;

  @BeforeEach
  public void setUp() throws Exception {
    router = new DefaultRouter();
    File htmlDir = new File(baseDir, "html");
    new File(htmlDir, "secret").mkdirs();
    router.setHtmlDir(htmlDir);
    Files.writeString(new File(htmlDir, "a.html").toPath(), "body");
    Files.writeString(new File(htmlDir, "index.htm").toPath(), "top");
    Files.writeString(new File(htmlDir, "secret/b.html").toPath(), "secret");
    Properties prop = new Properties();
    prop.setProperty("user", "pass");
    router.basicMap.put("/secret/", prop);
    handler = new StaticHandler(router);
  }

  private String handle(String path, Map<String, String> headMap) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    handler.handle(path, null, headMap, out);
    return out.toString(StandardCharsets.UTF_8);
  }

  private String ifModifiedSince(long plusSeconds) {
    return Constants.formatter.format(
        router.find("/a.html").getLastModified().plusSeconds(plusSeconds));
  }

  @Test
  public void normalize() {
    assertThat(StaticHandler.normalize("/")).isEqualTo("/index.htm");
    assertThat(StaticHandler.normalize("")).isEqualTo("/");
    assertThat(StaticHandler.normalize("/a.html")).isEqualTo("/a.html");
    assertThat(StaticHandler.normalize("a.html")).isEqualTo("/a.html");
    assertThat(StaticHandler.normalize("dir/")).isEqualTo("/dir/index.htm");
  }

  @Test
  public void file() throws Exception {
    String response = handle("/a.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 200 OK");
    assertThat(response).endsWith("\r\n\r\nbody");
  }

  @Test
  public void indexForDirectory() throws Exception {
    String response = handle("/", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 200 OK");
    assertThat(response).endsWith("\r\n\r\ntop");
  }

  @Test
  public void notModified() throws Exception {
    String response = handle("/a.html", Map.of("If-Modified-Since", ifModifiedSince(3600)));

    assertThat(response).startsWith("HTTP/1.1 304");
  }

  @Test
  public void statusLineHasNoTrailingSpace() throws Exception {
    assertThat(handle("/a.html", new HashMap<>())).startsWith("HTTP/1.1 200 OK\r\n");
    assertThat(handle("/a.html", Map.of("If-Modified-Since", ifModifiedSince(3600))))
        .startsWith("HTTP/1.1 304 Not Modified\r\n");
  }

  @Test
  public void modified() throws Exception {
    String response = handle("/a.html", Map.of("If-Modified-Since", ifModifiedSince(-3600)));

    assertThat(response).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void invalidIfModifiedSince() throws Exception {
    String response = handle("/a.html", Map.of("If-Modified-Since", "invalid"));

    assertThat(response).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void notFound() throws Exception {
    String response = handle("/none.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 404");
  }

  @Test
  public void unauthorized() throws Exception {
    String response = handle("/secret/b.html", new HashMap<>());

    assertThat(response).startsWith("HTTP/1.1 401");
  }

  @Test
  public void authorized() throws Exception {
    String auth =
        "Basic " + Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));

    String response = handle("/secret/b.html", Map.of("Authorization", auth));

    assertThat(response).startsWith("HTTP/1.1 200 OK");
    assertThat(response).endsWith("\r\n\r\nsecret");
  }
}
