// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ProxyHttpServerProcessTest {

  private ServerSocket target;
  private ServerSocket proxy;

  @BeforeEach
  public void setUp() throws Exception {
    target = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
    proxy = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
  }

  @AfterEach
  public void tearDown() throws Exception {
    target.close();
    proxy.close();
  }

  private ProxyConfig config(boolean allowPrivate, Properties users) {
    return new ProxyConfig(Set.of(target.getLocalPort()), allowPrivate, users);
  }

  /** プロキシを1接続分起動し、クライアント側ソケットを返す. */
  private Socket connectProxy(ProxyConfig config) throws Exception {
    Socket client = new Socket(InetAddress.getLoopbackAddress(), proxy.getLocalPort());
    client.setSoTimeout(5000);
    Socket accepted = proxy.accept();
    Thread thread = new Thread(() -> new ProxyHttpServerProcess(config, accepted).execute());
    thread.setDaemon(true);
    thread.start();
    return client;
  }

  private static String readHead(InputStream is) throws Exception {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    while (!baos.toString(StandardCharsets.ISO_8859_1).endsWith("\r\n\r\n")) {
      int b = is.read();
      if (b < 0) {
        break;
      }
      baos.write(b);
    }
    return baos.toString(StandardCharsets.ISO_8859_1);
  }

  private static void send(Socket socket, String text) throws Exception {
    OutputStream os = socket.getOutputStream();
    os.write(text.getBytes(StandardCharsets.ISO_8859_1));
    os.flush();
  }

  private void startEcho() {
    Thread thread =
        new Thread(
            () -> {
              try (Socket s = target.accept()) {
                byte[] buf = new byte[1024];
                int len;
                while ((len = s.getInputStream().read(buf)) > 0) {
                  s.getOutputStream().write(buf, 0, len);
                  s.getOutputStream().flush();
                }
              } catch (Exception e) {
                // 終了
              }
            });
    thread.setDaemon(true);
    thread.start();
  }

  @Test
  public void connectTunnel() throws Exception {
    startEcho();
    try (Socket client = connectProxy(config(true, null))) {
      send(client, "CONNECT 127.0.0.1:" + target.getLocalPort() + " HTTP/1.1\r\nHost: x\r\n\r\n");
      assertThat(readHead(client.getInputStream()))
          .isEqualTo("HTTP/1.1 200 Connection Established\r\n\r\n");

      send(client, "hello");
      byte[] buf = new byte[5];
      client.getInputStream().readNBytes(buf, 0, 5);
      assertThat(new String(buf, StandardCharsets.ISO_8859_1)).isEqualTo("hello");
    }
  }

  @Test
  public void plainHttpForward() throws Exception {
    Thread server =
        new Thread(
            () -> {
              try (Socket s = target.accept()) {
                String head = readHead(s.getInputStream());
                String body = head.startsWith("GET /a?b=1 HTTP/1.1\r\n") ? "ok" : "ng:" + head;
                send(s, "HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n" + body);
              } catch (Exception e) {
                // 終了
              }
            });
    server.setDaemon(true);
    server.start();
    try (Socket client = connectProxy(config(true, null))) {
      send(
          client,
          "GET http://127.0.0.1:"
              + target.getLocalPort()
              + "/a?b=1 HTTP/1.1\r\nHost: x\r\nProxy-Connection: keep-alive\r\n\r\n");
      String all = new String(client.getInputStream().readAllBytes(), StandardCharsets.ISO_8859_1);
      assertThat(all).endsWith("ok");
    }
  }

  @Test
  public void badRequestLine() throws Exception {
    try (Socket client = connectProxy(config(true, null))) {
      send(client, "GARBAGE\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 400 ");
    }
  }

  @Test
  public void connectWithoutPort() throws Exception {
    try (Socket client = connectProxy(config(true, null))) {
      send(client, "CONNECT host HTTP/1.1\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 400 ");
    }
  }

  @Test
  public void urlWithoutPath() throws Exception {
    try (Socket client = connectProxy(config(true, null))) {
      send(client, "GET ftp://host HTTP/1.1\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 400 ");
    }
  }

  @Test
  public void connectPortNotAllowed() throws Exception {
    try (Socket client = connectProxy(config(true, null))) {
      send(client, "CONNECT 127.0.0.1:" + (target.getLocalPort() + 1) + " HTTP/1.1\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 403 ");
    }
  }

  @Test
  public void privateAddressRejected() throws Exception {
    try (Socket client = connectProxy(config(false, null))) {
      send(client, "CONNECT 127.0.0.1:" + target.getLocalPort() + " HTTP/1.1\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 502 ");
    }
  }

  @Test
  public void authRequired() throws Exception {
    Properties users = new Properties();
    users.setProperty("user", "pass");
    try (Socket client = connectProxy(config(true, users))) {
      send(client, "CONNECT 127.0.0.1:" + target.getLocalPort() + " HTTP/1.1\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 407 ");
    }
  }

  @Test
  public void authSuccess() throws Exception {
    startEcho();
    Properties users = new Properties();
    users.setProperty("user", "pass");
    String credential =
        Base64.getEncoder().encodeToString("user:pass".getBytes(StandardCharsets.UTF_8));
    try (Socket client = connectProxy(config(true, users))) {
      send(
          client,
          "CONNECT 127.0.0.1:"
              + target.getLocalPort()
              + " HTTP/1.1\r\nProxy-Authorization: Basic "
              + credential
              + "\r\n\r\n");
      assertThat(readHead(client.getInputStream())).startsWith("HTTP/1.1 200 ");
    }
  }

  @Test
  public void isPrivate() throws Exception {
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("127.0.0.1")[0])).isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("10.1.2.3")[0])).isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("169.254.169.254")[0]))
        .isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("100.64.0.1")[0]))
        .isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("fd00::1")[0])).isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("::1")[0])).isTrue();
    assertThat(ProxyHttpServerProcess.isPrivate(InetAddress.getAllByName("93.184.216.34")[0]))
        .isFalse();
  }
}
