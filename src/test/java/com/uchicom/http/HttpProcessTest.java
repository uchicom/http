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

  DefaultRouter router;

  @BeforeEach
  public void setUp() throws Exception {
    router = new DefaultRouter();
    File htmlDir = new File(baseDir, "html");
    htmlDir.mkdirs();
    router.setHtmlDir(htmlDir);
    Files.writeString(new File(htmlDir, "a.html").toPath(), "body");
  }

  private String execute(String request) throws Exception {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream())
        .thenReturn(new ByteArrayInputStream(request.getBytes(StandardCharsets.UTF_8)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(socket.getOutputStream()).thenReturn(out);

    new HttpProcess(router, socket).execute();

    return out.toString(StandardCharsets.UTF_8);
  }

  private String ifModifiedSince(long plusSeconds) {
    return Constants.formatter.format(
        router.find("/a.html").getLastModified().plusSeconds(plusSeconds));
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
  public void lowerCaseHeaderNames() throws Exception {
    // ヘッダー名は大文字小文字を区別しない
    assertThat(
            execute(
                "GET /a.html HTTP/1.1\r\nif-modified-since: " + ifModifiedSince(3600) + "\r\n\r\n"))
        .startsWith("HTTP/1.1 304");
    assertThat(execute("POST /a.html HTTP/1.1\r\ncontent-length: abc\r\n\r\n"))
        .startsWith("HTTP/1.1 400");
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
  public void requestLineTooLong() throws Exception {
    String path = "/" + "a".repeat(Constants.MAX_LINE_LENGTH);
    assertThat(execute("GET " + path + " HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 414");
    // 改行なしの巨大データ
    assertThat(execute("a".repeat(Constants.MAX_LINE_LENGTH * 4))).startsWith("HTTP/1.1 414");
  }

  @Test
  public void requestLineAtLimit() throws Exception {
    String prefix = "GET /";
    String suffix = " HTTP/1.1";
    String path = "a".repeat(Constants.MAX_LINE_LENGTH - prefix.length() - suffix.length());
    assertThat(execute(prefix + path + suffix + "\r\n\r\n")).startsWith("HTTP/1.1 404");
  }

  @Test
  public void headerLineTooLong() throws Exception {
    String header = "X-Long: " + "a".repeat(Constants.MAX_LINE_LENGTH);
    assertThat(execute("GET /a.html HTTP/1.1\r\n" + header + "\r\n\r\n"))
        .startsWith("HTTP/1.1 431");
  }

  @Test
  public void headerLineAtLimit() throws Exception {
    String header = "X-Long: " + "a".repeat(Constants.MAX_LINE_LENGTH - "X-Long: ".length());
    assertThat(execute("GET /a.html HTTP/1.1\r\n" + header + "\r\n\r\n"))
        .startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void tooManyHeaders() throws Exception {
    String over = "X-A: b\r\n".repeat(Constants.MAX_HEADER_COUNT + 1);
    assertThat(execute("GET /a.html HTTP/1.1\r\n" + over + "\r\n")).startsWith("HTTP/1.1 431");
    String atLimit = "X-A: b\r\n".repeat(Constants.MAX_HEADER_COUNT);
    assertThat(execute("GET /a.html HTTP/1.1\r\n" + atLimit + "\r\n"))
        .startsWith("HTTP/1.1 200 OK");
  }

  @Test
  public void badRequest() throws Exception {
    assertThat(execute("GARBAGE\r\n\r\n")).startsWith("HTTP/1.1 400");
    assertThat(execute("")).startsWith("HTTP/1.1 400");
  }

  @Test
  public void doesNotWriteRequestToStdout() throws Exception {
    java.io.PrintStream original = System.out;
    ByteArrayOutputStream captured = new ByteArrayOutputStream();
    System.setOut(new java.io.PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      execute(
          "POST /a.html HTTP/1.1\r\nAuthorization: Basic c2VjcmV0\r\nContent-Length: 11\r\n\r\npassword=xx");
    } finally {
      System.setOut(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8)).isEmpty();
  }
}
