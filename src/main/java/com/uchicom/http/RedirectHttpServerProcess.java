// (C) 2026 uchicom
package com.uchicom.http;

import com.uchicom.server.ServerProcess;
import com.uchicom.util.Parameter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RedirectHttpServerProcess implements ServerProcess {

  private static final Logger LOGGER = Logger.getLogger(RedirectHttpServerProcess.class.getName());

  //	private static byte[] response302Hader = "HTTP/1.1 302 Found\r\nLocation: ".getBytes();
  private static byte[] response301Hader =
      "HTTP/1.1 301 Moved Permanently\r\nLocation: ".getBytes(StandardCharsets.ISO_8859_1);
  private static byte[] responseFooder = "\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1);
  private Parameter parameter;
  private Socket socket;
  Router router;

  private final int timeout;

  public RedirectHttpServerProcess(
      Parameter parameter, Router router, Socket socket, int timeout) {
    this.parameter = parameter;
    this.socket = socket;
    this.router = router;
    this.timeout = timeout;
  }

  @Override
  public long getLastTime() {
    return System.currentTimeMillis();
  }

  @Override
  public void forceClose() {
    try {
      socket.close();
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "socket can not be closed", e);
    }
  }

  @Override
  public void execute() {
    try {
      socket.setSoTimeout(timeout);
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "timeout can not be set", e);
      forceClose();
      return;
    }
    try (BufferedReader br =
            new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
        OutputStream os = socket.getOutputStream(); ) {
      // リクエストの分解、保持
      String head = br.readLine();
      if (head != null) {
        String[] heads = head.split(" ", 0);
        if (heads.length == 3) {
          // 初期応答行を返却する
          os.write(response301Hader);
          os.write(parameter.get("redirect").getBytes(StandardCharsets.ISO_8859_1));
          os.write(heads[1].getBytes(StandardCharsets.ISO_8859_1));
          os.write(responseFooder);

        } else {
          // Bad Request
          router.error("400", os);
        }

      } else {
        // Bad Request
        router.error("400", os);
      }
    } catch (IOException e) {
      LOGGER.log(Level.WARNING, "request can not be processed", e);
    } catch (Throwable e) {
      LOGGER.log(Level.SEVERE, "unexpected error", e);
    }
  }
}
