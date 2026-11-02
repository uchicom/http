// (C) 2026 uchicom
package com.uchicom.http;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public class Constants {

  // 初期設定
  /** デフォルトメールボックスディレクトリ */
  public static String DEFAULT_DIR = "www";

  /** デフォルト待ち受けポート番号 */
  public static String DEFAULT_PORT = "80";

  /** デフォルト接続待ち数 */
  public static String DEFAULT_BACK = "10";

  /** デフォルトスレッドプール数 */
  public static String DEFAULT_POOL = "10";

  /** リクエストボディの最大サイズ(バイト) */
  public static final int MAX_CONTENT_LENGTH = 10 * 1024 * 1024;

  /** リクエスト行・ヘッダー1行の最大長(バイト) */
  public static final int MAX_LINE_LENGTH = 8 * 1024;

  /** ヘッダーの最大件数 */
  public static final int MAX_HEADER_COUNT = 100;

  public static DateTimeFormatter formatter =
      DateTimeFormatter.RFC_1123_DATE_TIME.withZone(ZoneOffset.UTC);
}
