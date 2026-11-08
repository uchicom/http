// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class RedirectHttpServerProcessTest {

  private String execute(String request) throws Exception {
    HttpParameter parameter =
        new HttpParameter(new String[] {"-type", "redirect", "-redirect", "http://example.com"});
    Socket socket = mock(Socket.class);
    when(socket.getInputStream())
        .thenReturn(new ByteArrayInputStream(request.getBytes(StandardCharsets.ISO_8859_1)));
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    when(socket.getOutputStream()).thenReturn(out);

    new RedirectHttpServerProcess(parameter, new DefaultRouter(), socket, Constants.DEFAULT_TIMEOUT)
        .execute();

    return out.toString(StandardCharsets.ISO_8859_1);
  }

  @Test
  public void redirect() throws Exception {
    String response = execute("GET /a.html HTTP/1.1\r\n\r\n");

    assertThat(response).startsWith("HTTP/1.1 301");
    assertThat(response).contains("Location: http://example.com/a.html");
  }

  @Test
  public void badRequest() throws Exception {
    assertThat(execute("GET\r\n\r\n")).startsWith("HTTP/1.1 400");
    assertThat(execute("")).startsWith("HTTP/1.1 400");
  }

  @Test
  public void requestLineTooLong() throws Exception {
    String path = "/" + "a".repeat(Constants.MAX_LINE_LENGTH);
    assertThat(execute("GET " + path + " HTTP/1.1\r\n\r\n")).startsWith("HTTP/1.1 414");
    // 改行なしの巨大データ
    assertThat(execute("a".repeat(Constants.MAX_LINE_LENGTH * 4))).startsWith("HTTP/1.1 414");
  }
}
