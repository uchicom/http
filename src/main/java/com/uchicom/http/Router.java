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
 * @author Uchiyama Shigeki
 */
public interface Router {

  /**
   * 初期化 ここで、設定ファイルを読み込む。ファイルの存在や更新時刻はリクエストごとに確認する。
   *
   * @param baseFile
   * @param cacheSize キャッシュ対象とする最大ファイルサイズ(バイト)。0以下の場合はキャッシュしない
   */
  public void init(File baseFile, long cacheSize);

  /**
   * If-Modified-Since以降に更新されていない(304を返してよい)かをチェックする
   *
   * @param fileName
   * @param ifModified
   */
  public boolean isNotModified(String fileName, TemporalAccessor ifModified);

  /**
   * 指定のURLが存在するかをチェックする
   *
   * @param filePath
   */
  public boolean exists(String filePath);

  public boolean isForward(String filePath);

  /**
   * リクエストを処理する。 基本的には出力結果をストリームに書き出す。
   *
   * @param filePath
   * @param paramMap
   * @param outputStream
   */
  public void request(
      String filePath,
      Map<String, List<String>> paramMap,
      Map<String, String> headMap,
      OutputStream outputStream)
      throws IOException;

  public void error(String code, OutputStream outputStream) throws IOException;

  public void forward(
      String filePath, String[] heads, BufferedReader reader, OutputStream outputStream)
      throws IOException;
}
