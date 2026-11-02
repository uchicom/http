// (C) 2017 uchicom
package com.uchicom.http;

import com.uchicom.server.ServerProcess;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * プロキシ. CONNECTは素のTCPトンネルとして中継する.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class ProxyHttpServerProcess implements ServerProcess {

  private static final int MAX_LINE_LENGTH = Constants.MAX_LINE_LENGTH;
  private static final int MAX_HEADER_COUNT = Constants.MAX_HEADER_COUNT;
  private static final int CONNECT_TIMEOUT = 10 * 1000;

  private final ProxyConfig config;
  private final Socket socket;
  private volatile Socket send;
  private volatile long lastTime = System.currentTimeMillis();

  public ProxyHttpServerProcess(ProxyConfig config, Socket socket) {
    this.config = config;
    this.socket = socket;
  }

  /* (非 Javadoc)
   * @see com.uchicom.server.ServerProcess#getLastTime()
   */
  @Override
  public long getLastTime() {
    return lastTime;
  }

  /* (非 Javadoc)
   * @see com.uchicom.server.ServerProcess#forceClose()
   */
  @Override
  public void forceClose() {
    close(socket);
    close(send);
  }

  /* (非 Javadoc)
   * @see com.uchicom.server.ServerProcess#execute()
   */
  @Override
  public void execute() {
    try {
      handle();
    } catch (IOException e) {
      // 切断などは正常系として扱う
    } finally {
      forceClose();
    }
  }

  private void handle() throws IOException {
    InputStream is = socket.getInputStream();
    OutputStream os = socket.getOutputStream();

    String requestLine = readLine(is);
    if (requestLine == null) {
      return;
    }
    String[] heads = requestLine.split(" ", -1);
    if (heads.length != 3 || heads[0].isEmpty() || heads[1].isEmpty() || heads[2].isEmpty()) {
      respond(os, "400 Bad Request");
      return;
    }
    List<String> headers = new ArrayList<>();
    String line;
    while ((line = readLine(is)) != null && !line.isEmpty()) {
      if (headers.size() >= MAX_HEADER_COUNT) {
        respond(os, "400 Bad Request");
        return;
      }
      headers.add(line);
    }
    if (line == null) {
      respond(os, "400 Bad Request");
      return;
    }

    if (config.isAuthRequired()
        && !config.authenticate(findHeader(headers, "Proxy-Authorization"))) {
      respond(os, "407 Proxy Authentication Required", "Proxy-Authenticate: Basic realm=\"PROXY\"");
      return;
    }

    boolean connect = heads[0].equals("CONNECT");
    String host;
    int port;
    String forwardLine = null;
    if (connect) {
      int index = heads[1].lastIndexOf(':');
      if (index <= 0) {
        respond(os, "400 Bad Request");
        return;
      }
      host = heads[1].substring(0, index);
      try {
        port = Integer.parseInt(heads[1].substring(index + 1));
      } catch (NumberFormatException e) {
        respond(os, "400 Bad Request");
        return;
      }
      if (port < 1 || port > 65535) {
        respond(os, "400 Bad Request");
        return;
      }
      if (!config.isConnectPortAllowed(port)) {
        respond(os, "403 Forbidden");
        return;
      }
    } else {
      URI uri;
      try {
        uri = new URI(heads[1]);
      } catch (URISyntaxException e) {
        respond(os, "400 Bad Request");
        return;
      }
      if (!"http".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
        respond(os, "400 Bad Request");
        return;
      }
      host = uri.getHost();
      port = uri.getPort() < 0 ? 80 : uri.getPort();
      String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
      if (uri.getRawQuery() != null) {
        path += "?" + uri.getRawQuery();
      }
      forwardLine = heads[0] + " " + path + " " + heads[2];
    }

    Socket target = connectTarget(host, port);
    if (target == null) {
      respond(os, "502 Bad Gateway");
      return;
    }
    send = target;
    if (socket.isClosed()) {
      // forceClose と競合した場合
      close(target);
      return;
    }

    OutputStream sendOs = target.getOutputStream();
    if (connect) {
      os.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));
      os.flush();
    } else {
      StringBuilder sb = new StringBuilder(forwardLine).append("\r\n");
      for (String header : headers) {
        String name =
            header.substring(0, Math.max(header.indexOf(':'), 0)).toLowerCase(Locale.ROOT);
        if (name.equals("proxy-authorization")
            || name.equals("proxy-connection")
            || name.equals("connection")) {
          continue;
        }
        sb.append(header).append("\r\n");
      }
      // 後続リクエストを絶対URIのまま流さないよう、1リクエストで閉じる
      sb.append("Connection: close\r\n\r\n");
      sendOs.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
      sendOs.flush();
    }

    relay(is, os, target);
  }

  /** 双方向に中継する. どちらかが終了したら戻る(呼び出し側でソケットを閉じる). */
  private void relay(InputStream is, OutputStream os, Socket target) throws IOException {
    Runnable activity = () -> lastTime = System.currentTimeMillis();
    Thread toTarget =
        new Thread(
            () -> {
              try {
                new IOThrougher(is, target.getOutputStream(), activity).run();
              } catch (IOException e) {
                // 無視
              } finally {
                forceClose();
              }
            });
    toTarget.setDaemon(true);
    toTarget.start();
    new IOThrougher(target.getInputStream(), os, activity).run();
    forceClose();
  }

  /**
   * 接続先へ接続する. 名前解決した結果を検証し、そのアドレスへ接続する(DNSリバインディング対策).
   *
   * @return 接続したソケット。拒否・接続失敗の場合null
   */
  private Socket connectTarget(String host, int port) {
    InetAddress[] addresses;
    try {
      addresses = InetAddress.getAllByName(host);
    } catch (IOException e) {
      return null;
    }
    if (!config.isAllowPrivate()) {
      for (InetAddress address : addresses) {
        if (isPrivate(address)) {
          return null;
        }
      }
    }
    for (InetAddress address : addresses) {
      Socket target = new Socket();
      try {
        target.connect(new InetSocketAddress(address, port), CONNECT_TIMEOUT);
        return target;
      } catch (IOException e) {
        close(target);
      }
    }
    return null;
  }

  static boolean isPrivate(InetAddress address) {
    if (address.isAnyLocalAddress()
        || address.isLoopbackAddress()
        || address.isLinkLocalAddress()
        || address.isSiteLocalAddress()
        || address.isMulticastAddress()) {
      return true;
    }
    byte[] bytes = address.getAddress();
    if (bytes.length == 16) {
      // IPv6 ユニークローカル fc00::/7
      return (bytes[0] & 0xfe) == 0xfc;
    }
    // 100.64.0.0/10 (CGNAT)
    return (bytes[0] & 0xff) == 100 && (bytes[1] & 0xc0) == 64;
  }

  private static String findHeader(List<String> headers, String name) {
    for (String header : headers) {
      int index = header.indexOf(':');
      if (index > 0 && header.substring(0, index).trim().equalsIgnoreCase(name)) {
        return header.substring(index + 1).trim();
      }
    }
    return null;
  }

  /**
   * 1行読む. 末尾のCRLF(またはLF)は取り除く.
   *
   * @return 行。接続が閉じられた場合・長すぎる場合はnull
   */
  private static String readLine(InputStream is) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    int b;
    while ((b = is.read()) >= 0) {
      if (b == '\n') {
        byte[] bytes = baos.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') {
          length--;
        }
        return new String(bytes, 0, length, StandardCharsets.ISO_8859_1);
      }
      if (baos.size() >= MAX_LINE_LENGTH) {
        return null;
      }
      baos.write(b);
    }
    return null;
  }

  private static void respond(OutputStream os, String status, String... headers)
      throws IOException {
    StringBuilder sb = new StringBuilder("HTTP/1.1 ").append(status).append("\r\n");
    for (String header : headers) {
      sb.append(header).append("\r\n");
    }
    sb.append("Content-Length: 0\r\nConnection: close\r\n\r\n");
    os.write(sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    os.flush();
  }

  private static void close(Socket socket) {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        // 無視
      }
    }
  }
}
