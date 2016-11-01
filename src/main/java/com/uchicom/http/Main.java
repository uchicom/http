/**
 * (c) 2016 uchicom
 */
package com.uchicom.http;

/**
 * 起動クラス.
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Main {

	/**
	 * アドレスとメールユーザーフォルダの格納フォルダを指定する.
	 *
	 * @param args
	 */
	public static void main(String[] args) {
		HttpParameter parameter = new HttpParameter(args);
		if (parameter.init(System.err)) {
			parameter.createServer().execute();
		}
	}

}
