// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HttpProcessTest {

  @TempDir File baseDir;

  @BeforeEach
  public void setUp() throws Exception {
    DefaultRouter.fileMap.clear();
    DefaultRouter.basicMap.clear();
    DefaultRouter.forwardMap.clear();
    DefaultRouter.errorMap.clear();
    File htmlDir = new File(baseDir, "html");
    htmlDir.mkdirs();
    DefaultRouter.htmlFileLength = htmlDir.toURI().getPath().length();
    Files.writeString(new File(htmlDir, "a.html").toPath(), "body");
    DefaultRouter.createMap(htmlDir);
  }

  private String execute(String request) throws Exception {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream())
        .thenReturn(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(socket.getOutputStream()).thenReturn(out);

    new HttpProcess(null, socket).execute();

    return out.toString();
  }

  private String ifModifiedSince(long plusSeconds) {
    return Constants.formatter.format(
        DefaultRouter.fileMap.get("/a.html").getLastModified().plusSeconds(plusSeconds));
  }

  @Test
  public void getFile() throws Exception {
    String response = execute("GET /a.html HTTP/1.1\r\nHost: localhost\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 200 OK");
    assertThat(response).endsWith("\r\n\r\nbody");
  }

  @Test
  public void firstHeaderIsParsed() throws Exception {
    // 先頭のヘッダーが読み飛ばされず、If-Modified-Sinceにより304になる
    String response =
        execute("GET /a.html HTTP/1.1\r\nIf-Modified-Since: " + ifModifiedSince(3600) + "\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 304");
  }

  @Test
  public void oldIfModifiedSince() throws Exception {
    String response =
        execute(
            "GET /a.html HTTP/1.1\r\nIf-Modified-Since: " + ifModifiedSince(-3600) + "\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void requestEndsWithoutBlankLine() throws Exception {
    // 空行が来ないまま切断されてもNPEにならない
    String response = execute("GET /a.html HTTP/1.1\r\nHost: localhost");

    assertThat(response).startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void headerWithoutValue() throws Exception {
    assertThat(execute("GET /a.html HTTP/1.1\r\nX-Empty:\r\n\r\n")).startsWith("HTTP/1.1 200 OK");
    assertThat(execute("GET /a.html HTTP/1.1\r\nX-NoSpace:value\r\n\r\n"))
        .startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void invalidContentLength() throws Exception {
    assertThat(execute("POST /a.html HTTP/1.1\r\nContent-Length: abc\r\n\r\n"))
        .startsWith("HTTP/1.1 400");
    assertThat(execute("POST /a.html HTTP/1.1\r\nContent-Length: -1\r\n\r\n"))
        .startsWith("HTTP/1.1 400");
  }

  @Test
  public void tooLargeContentLength() throws Exception {
    // 巨大な配列を確保せずに拒否する
    assertThat(
            execute("POST /a.html HTTP/1.1\r\nContent-Length: " + Integer.MAX_VALUE + "\r\n\r\n"))
        .startsWith("HTTP/1.1 400");
  }

  @Test
  public void contentLengthWithBody() throws Exception {
    assertThat(execute("POST /a.html HTTP/1.1\r\nContent-Length: 3\r\n\r\nabc"))
        .startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void contentLengthLargerThanBody() throws Exception {
    // 宣言より短いボディでも無限ループや例外にならない
    assertThat(execute("POST /a.html HTTP/1.1\r\nContent-Length: 10\r\n\r\nabc"))
        .startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void badRequest() throws Exception {
    assertThat(execute("GARBAGE\r\n\r\n")).startsWith("HTTP/1.1 400");
    assertThat(execute("")).startsWith("HTTP/1.1 400");
  }
}
