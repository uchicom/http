// (C) 2012 uchicom
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;

/**
 * リクエストを処理して応答を書き出す.
 *
 * @author Uchiyama Shigeki
 */
public interface Router {

  /**
   * 初期化 ここで、設定ファイルを読み込む。ファイルの存在や更新時刻はリクエストごとに確認する。
   *
   * @param baseFile 基準フォルダ
   * @param cacheSize キャッシュ対象とする最大ファイルサイズ(バイト)。0以下の場合はキャッシュしない
   */
  public void init(File baseFile, long cacheSize);

  /**
   * If-Modified-Since以降に更新されていない(304を返してよい)かをチェックする
   *
   * @param fileName ファイルパス
   * @param ifModified If-Modified-Sinceの値
   * @return 304を返してよい場合true
   */
  public boolean isNotModified(String fileName, TemporalAccessor ifModified);

  /**
   * 指定のURLが存在するかをチェックする
   *
   * @param filePath ファイルパス
   * @return 存在する場合true
   */
  public boolean exists(String filePath);

  /**
   * 指定のパスが転送対象かをチェックする
   *
   * @param filePath ファイルパス
   * @return 転送対象の場合true
   */
  public boolean isForward(String filePath);

  /**
   * リクエストを処理する。 基本的には出力結果をストリームに書き出す。
   *
   * @param filePath ファイルパス
   * @param paramMap GETパラメータ。ない場合はnull
   * @param headMap リクエストヘッダー
   * @param outputStream 出力先
   * @throws IOException 入出力エラー
   */
  public void request(
      String filePath,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream outputStream)
      throws IOException;

  /**
   * エラー応答を書き出す
   *
   * @param code ステータスコード
   * @param outputStream 出力先
   * @throws IOException 入出力エラー
   */
  public void error(String code, OutputStream outputStream) throws IOException;

  /**
   * リクエストを転送先に中継して、応答を書き出す
   *
   * @param filePath ファイルパス
   * @param heads リクエスト行(メソッド、パス、バージョン)
   * @param reader リクエストヘッダー以降を読むreader(ISO-8859-1)
   * @param outputStream 出力先
   * @param clientAddress クライアントのアドレス
   * @throws IOException 入出力エラー
   */
  public void forward(
      String filePath,
      String[] heads,
      BufferedReader reader,
      OutputStream outputStream,
      String clientAddress)
      throws IOException;
}
