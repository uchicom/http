// (C) 2026 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.OutputStream;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;

/**
 * 静的ファイル配信の入口. パース済みのリクエストパスとヘッダーを受け取り、Routerを使って応答を書き出す.
 *
 * @author Uchiyama Shigeki
 */
public class StaticHandler {

  protected final Router router;

  public StaticHandler(Router router) {
    this.router = router;
  }

  /**
   * リクエストパス(クエリ文字列を除く)を、公開ファイルのパスに正規化する. `/`で終わる場合は`index.htm`を付ける.
   *
   * @param path リクエストパス
   * @return 先頭が`/`のファイルパス
   */
  public static String normalize(String path) {
    String fileName = path.startsWith("/") ? path : "/" + path;
    if (path.endsWith("/")) {
      fileName += "index.htm";
    }
    return fileName;
  }

  /**
   * リクエストを処理して、ファイル・304・404のいずれかを書き出す.
   *
   * @param path リクエストパス(クエリ文字列を除く)
   * @param paramMap GETパラメータ。ない場合はnull
   * @param headMap リクエストヘッダー
   * @param os 出力先
   */
  public void handle(
      String path, Map<String, List<String>> paramMap, Map<String, String> headMap, OutputStream os)
      throws IOException {
    String fileName = normalize(path);
    if (!router.exists(fileName)) {
      router.error("404", os);
      return;
    }
    TemporalAccessor ifModified = parseIfModifiedSince(headMap.get("If-Modified-Since"));
    if (ifModified != null && router.isNotModified(fileName, ifModified)) {
      os.write("HTTP/1.1 304 Not Modified \r\n".getBytes());
      os.write("Date: ".getBytes());
      os.write(Constants.formatter.format(OffsetDateTime.now()).getBytes());
      os.write("\r\n".getBytes());
      os.write("\r\n".getBytes());
      return;
    }
    router.request(fileName, paramMap, headMap, os);
  }

  private TemporalAccessor parseIfModifiedSince(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Constants.formatter.parse(value);
    } catch (DateTimeParseException e) {
      e.printStackTrace();
      return null;
    }
  }
}
