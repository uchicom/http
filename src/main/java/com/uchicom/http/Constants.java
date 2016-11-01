package com.uchicom.http;

import java.text.SimpleDateFormat;
import java.util.Locale;

public class Constants {

	//初期設定
	/** デフォルトメールボックスディレクトリ */
    public static String DEFAULT_DIR = "www";
    /** デフォルト待ち受けポート番号 */
    public static String DEFAULT_PORT = "80";
    /** デフォルト接続待ち数 */
	public static String DEFAULT_BACK = "10";
	/** デフォルトスレッドプール数 */
	public static String DEFAULT_POOL = "10";

	public static SimpleDateFormat format = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss 'GMT'", Locale.UK);

}
