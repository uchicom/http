// (C) 2026 uchicom
package com.uchicom.http;

import com.uchicom.server.ServerProcess;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class HttpProcess implements ServerProcess {

  private Socket socket;

  protected final Router router;

  protected final StaticHandler staticHandler;

  /** 最終処理時刻 */
  private long lastTime = System.currentTimeMillis();

  public HttpProcess(Router router, Socket socket) {
    this.socket = socket;
    this.router = router;
    this.staticHandler = new StaticHandler(router);
  }

  @Override
  public long getLastTime() {
    return lastTime; // TODO これabstracだな
  }

  @Override
  public void forceClose() {
    if (socket != null) {
      try {
        socket.close();
      } catch (IOException e) {
        // TODO 自動生成された catch ブロック
        e.printStackTrace();
      } // TODO これabstractだな
    }
    ;
  }

  public void head(String head, BufferedReader br, OutputStream os) throws IOException {

    String[] heads = head.split(" ");
    if (heads.length == 3) {
      // 初期応答行を返却する
      String[] urls = heads[1].split("\\?");
      String fileName = StaticHandler.normalize(urls[0]);
      if (router.isForward(fileName)) {
        router.forward(fileName, heads, br, os);
        return;
      }
      Map<String, List<String>> paramMap = null;
      Map<String, String> headMap = new HashMap<>();
      if (urls.length > 1) {
        // GETパラメータありなので解析。
        paramMap = createGetParamMap(urls[1]);
      }
      int headerCount = 0;
      String line;
      try {
        line = LineReader.readLine(br);
      } catch (LineReader.LineTooLongException e) {
        router.error("431", os);
        return;
      }
      while (line != null && !"".equals(line)) {
        if (++headerCount > Constants.MAX_HEADER_COUNT) {
          router.error("431", os);
          return;
        }
        int index = line.indexOf(":");
        if (index >= 0) {
          String value = line.substring(index + 1).trim();
          headMap.put(line.substring(0, index), value);
        } else {
          headMap.put(line, line);
        }
        try {
          line = LineReader.readLine(br);
        } catch (LineReader.LineTooLongException e) {
          router.error("431", os);
          return;
        }
      }
      if (headMap.containsKey("Content-Length")) {
        int contentLength;
        try {
          contentLength = Integer.parseInt(headMap.get("Content-Length"));
        } catch (NumberFormatException e) {
          router.error("400", os);
          return;
        }
        if (contentLength < 0 || contentLength > Constants.MAX_CONTENT_LENGTH) {
          router.error("400", os);
          return;
        }
        // ボディは使わないので読み捨てる
        long remaining = contentLength;
        while (remaining > 0) {
          long skipped = br.skip(remaining);
          if (skipped <= 0) {
            break;
          }
          remaining -= skipped;
        }
      }
      staticHandler.handle(urls[0], paramMap, headMap, os);

    } else {
      // Bad Request
      router.error("400", os);
    }
  }

  @Override
  public void execute() {
    try {
      BufferedReader br =
          new BufferedReader(
              new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
      OutputStream os = socket.getOutputStream();
      // リクエストの分解、保持
      String startLine;
      try {
        startLine = LineReader.readLine(br);
      } catch (LineReader.LineTooLongException e) {
        router.error("414", os);
        br.close();
        os.close();
        return;
      }
      if (startLine != null) {
        head(startLine, br, os);

      } else {
        // Bad Request
        router.error("400", os);
      }
      br.close();
      os.close();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (Throwable e) {
      e.printStackTrace();
    } finally {
      if (socket != null) {
        try {
          socket.close();
        } catch (Exception e) {
          e.printStackTrace();
        } finally {
          socket = null;
        }
      }
    }
  }

  public Map<String, List<String>> createGetParamMap(String get) {
    String[] keyValues;
    try {
      keyValues = URLDecoder.decode(get, "utf-8").split("&");
    } catch (Exception e) {
      e.printStackTrace();
      keyValues = get.split("&");
    }
    Map<String, List<String>> map = new HashMap<String, List<String>>();
    for (String keyValue : keyValues) {
      String[] split = keyValue.split("=");
      if (split.length > 1) {
        if (map.containsKey(split[0])) {
          List<String> list = map.get(split[0]);
          list.add(split[1]);
        } else {
          List<String> list = new ArrayList<String>();
          list.add(split[1]);
          map.put(split[0], list);
        }
      }
    }
    Map<String, String[]> stringMap = new HashMap<String, String[]>();
    Iterator<Entry<String, List<String>>> iterator = map.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<String, List<String>> entry = iterator.next();
      stringMap.put(entry.getKey(), entry.getValue().toArray(new String[0]));
    }
    return map;
  }
}
