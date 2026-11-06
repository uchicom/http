// (C) 2026 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
   * @return 先頭が`/`のファイルパス(UTF-8でパーセントデコード済み)
   * @throws IllegalArgumentException 不正なパーセントエスケープ・UTF-8の場合
   */
  public static String normalize(String path) {
    // 隠しファイル・`..`・`\`の判定は、デコード後のパスに対してRouterで行われる
    path = decode(path);
    String fileName = path.startsWith("/") ? path : "/" + path;
    if (path.endsWith("/")) {
      fileName += "index.htm";
    }
    return fileName;
  }

  /**
   * パーセントエンコードをUTF-8としてデコードする. パスなので`+`は空白に変換しない.
   *
   * @throws IllegalArgumentException 不正なエスケープ、または不正なUTF-8の場合
   */
  static String decode(String path) {
    if (path.indexOf('%') < 0) {
      return path;
    }
    // URLDecoderはフォーム用で`+`を空白にするため、先に`%2B`へ置き換えて保持する
    String decoded = URLDecoder.decode(path.replace("+", "%2B"), StandardCharsets.UTF_8);
    // 不正なUTF-8は黙ってU+FFFDに置き換えられるので、置換されていたら不正として扱う
    if (decoded.indexOf('�') >= 0) {
      throw new IllegalArgumentException("invalid UTF-8: " + path);
    }
    return decoded;
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
    handle(path, paramMap, headMap, os, true);
  }

  /**
   * リクエストを処理して、ファイル・304・404のいずれかを書き出す.
   *
   * @param withBody falseの場合(HEAD)はボディを書き出さない
   */
  public void handle(
      String path,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream os,
      boolean withBody)
      throws IOException {
    String fileName;
    try {
      fileName = normalize(path);
    } catch (IllegalArgumentException e) {
      router.error("400", os);
      return;
    }
    if (!router.exists(fileName)) {
      router.error("404", os);
      return;
    }
    TemporalAccessor ifModified = parseIfModifiedSince(headMap.get("If-Modified-Since"));
    if (ifModified != null && router.isNotModified(fileName, ifModified)) {
      os.write("HTTP/1.1 304 Not Modified\r\n".getBytes(StandardCharsets.ISO_8859_1));
      os.write("Date: ".getBytes(StandardCharsets.ISO_8859_1));
      os.write(
          Constants.formatter
              .format(OffsetDateTime.now(ZoneOffset.UTC))
              .getBytes(StandardCharsets.ISO_8859_1));
      os.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
      os.write("\r\n".getBytes(StandardCharsets.ISO_8859_1));
      return;
    }
    if (withBody) {
      router.request(fileName, paramMap, headMap, os);
    } else {
      router.requestHead(fileName, paramMap, headMap, os);
    }
  }

  private TemporalAccessor parseIfModifiedSince(String value) {
    if (value == null) {
      return null;
    }
    try {
      return Constants.formatter.parse(value);
    } catch (DateTimeParseException e) {
      // クライアントが不正な値を送った場合は、条件なしのリクエストとして扱う
      return null;
    }
  }
}
