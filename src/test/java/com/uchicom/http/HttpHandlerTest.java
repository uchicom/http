// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HttpHandlerTest {

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

  /** channel.readが、指定の文字列を順番に返し、その後は切断(-1)を返すようにする. */
  private SocketChannel channelReading(String... chunks) throws Exception {
    SocketChannel channel = mock(SocketChannel.class);
    int[] count = {0};
    when(channel.read(any(ByteBuffer.class)))
        .thenAnswer(
            invocation -> {
              if (count[0] >= chunks.length) {
                return -1;
              }
              byte[] bytes = chunks[count[0]++].getBytes(StandardCharsets.UTF_8);
              ByteBuffer buff = invocation.getArgument(0);
              buff.put(bytes);
              return bytes.length;
            });
    return channel;
  }

  private SelectionKey readableKey(SocketChannel channel) {
    SelectionKey key = mock(SelectionKey.class);
    when(key.channel()).thenReturn(channel);
    when(key.isReadable()).thenReturn(true);
    return key;
  }

  private String response(HttpHandler handler) throws Exception {
    return StandardCharsets.UTF_8.decode(handler.response(null, handler.fileName)).toString();
  }

  @Test
  public void handleParsesOnlyReceivedBytes() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    SelectionKey key = readableKey(channelReading("GET /a.html HTTP/1.1\r\nHost: x\r\n\r\n"));

    handler.handle(key);

    // 未使用領域(\0)が混ざらずに解析される
    assertThat(handler.fileName).isEqualTo("/a.html");
    assertThat(handler.headMap).containsOnlyKeys("Host").containsEntry("Host", "x");
    verify(key).interestOps(SelectionKey.OP_WRITE);
  }

  @Test
  public void handleWaitsUntilHeaderIsComplete() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    SelectionKey key = readableKey(channelReading("GET /a.html HTTP/1.1\r\nHo", "st: x\r\n\r\n"));

    handler.handle(key);
    assertThat(handler.fileName).isNull();
    verify(key, never()).interestOps(SelectionKey.OP_WRITE);

    handler.handle(key);
    assertThat(handler.fileName).isEqualTo("/a.html");
    assertThat(handler.headMap).containsEntry("Host", "x");
    verify(key).interestOps(SelectionKey.OP_WRITE);
  }

  @Test
  public void handleRequestLargerThanOldLimit() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    String request = "GET /a.html HTTP/1.1\r\nX-Long: " + "a".repeat(3000) + "\r\n\r\n";
    SelectionKey key = readableKey(channelReading(request));

    handler.handle(key);

    assertThat(handler.fileName).isEqualTo("/a.html");
    assertThat(handler.headMap.get("X-Long")).hasSize(3000);
  }

  @Test
  public void handleOversizedRequestIsBadRequest() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    // バッファを満たしても終端(空行)が来ない
    String request = "GET /a.html HTTP/1.1\r\nX-Long: " + "a".repeat(9000);
    SelectionKey key = readableKey(channelReading(request.substring(0, 8192)));

    handler.handle(key);

    assertThat(handler.fileName).isNull();
    verify(key).interestOps(SelectionKey.OP_WRITE);
    assertThat(response(handler)).startsWith("HTTP/1.1 400");
  }

  @Test
  public void handleClosedBeforeRequestIsComplete() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    SocketChannel channel = channelReading("GET /a.html HTTP/1.1\r\n");
    SelectionKey key = readableKey(channel);

    handler.handle(key);
    handler.handle(key);

    verify(key).cancel();
    verify(channel).close();
    verify(key, never()).interestOps(SelectionKey.OP_WRITE);
  }

  @Test
  public void responseFile() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    handler.fileName = "/a.html";

    assertThat(response(handler)).startsWith("HTTP/1.1 200 OK");
    handler.fileName = "/none.html";
    assertThat(response(handler)).startsWith("HTTP/1.1 404");
  }

  @Test
  public void requestWithoutLineBreakIsBadRequest() throws Exception {
    HttpHandler handler = new HttpHandler(null);

    assertThat(handler.request("GET /a.html HTTP/1.1")).isNull();
    assertThat(handler.request("")).isNull();
    assertThat(response(handler)).startsWith("HTTP/1.1 400");
  }

  @Test
  public void requestWithMalformedRequestLineIsBadRequest() throws Exception {
    HttpHandler handler = new HttpHandler(null);

    handler.fileName = handler.request("GARBAGE\r\n\r\n");

    assertThat(handler.fileName).isNull();
    assertThat(response(handler)).startsWith("HTTP/1.1 400");
  }

  @Test
  public void requestParsesFileName() throws Exception {
    HttpHandler handler = new HttpHandler(null);

    assertThat(handler.request("GET /a.html HTTP/1.1\r\nHost: x\r\n\r\n")).isEqualTo("/a.html");
  }

  @Test
  public void requestResetsPreviousState() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    handler.request("GET /a.html?x=1 HTTP/1.1\r\nAuthorization: Basic abc\r\n\r\n");
    assertThat(handler.paramMap).containsKey("x");
    assertThat(handler.headMap).containsKey("Authorization");

    handler.request("GET /a.html HTTP/1.1\r\nHost: x\r\n\r\n");

    // GETパラメータの無いリクエストで、前のパラメータやヘッダーが残らない
    assertThat(handler.paramMap).isNull();
    assertThat(handler.headMap).containsOnlyKeys("Host");
  }

  @Test
  public void requestResetsIfModified() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    handler.request(
        "GET /a.html HTTP/1.1\r\nIf-Modified-Since: Thu, 01 Jan 2026 00:00:00 GMT\r\n\r\n");
    assertThat(handler.ifModified).isNotNull();

    handler.request("GET /a.html HTTP/1.1\r\nHost: x\r\n\r\n");

    assertThat(handler.ifModified).isNull();
  }

  @Test
  public void requestResetsStateEvenIfBadRequest() throws Exception {
    HttpHandler handler = new HttpHandler(null);
    handler.request("GET /a.html?x=1 HTTP/1.1\r\nHost: x\r\n\r\n");

    handler.request("GARBAGE");

    assertThat(handler.paramMap).isNull();
    assertThat(handler.headMap).isEmpty();
  }

  @Test
  public void routerIsPerInstance() throws Exception {
    assertThat(new HttpHandler(null).router).isSameAs(Context.singleton().getRouter());
    assertThat(
            java.lang.reflect.Modifier.isStatic(
                HttpHandler.class.getDeclaredField("router").getModifiers()))
        .isFalse();
    assertThat(
            java.lang.reflect.Modifier.isStatic(
                HttpProcess.class.getDeclaredField("router").getModifiers()))
        .isFalse();
  }
}
