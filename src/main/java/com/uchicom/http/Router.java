/**
 * (c) 2012 uchicom
 */
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
 *
 */
public interface Router {

    /**
     * 初期化
     * ここで、存在するファイルをチェックする。
     * また、動的にロードする場合は、定期的にチェックを行うスレッドを立ち上げる。
     * @param baseFile
     */
    public void init(File baseFile);
    public boolean isModified(String fileName, TemporalAccessor ifModified);
    /**
     * 指定のURLが存在するかをチェックする
     * @param filePath
     */
    public boolean exists(String filePath);
    public boolean isForward(String filePath);
    /**
     * リクエストを処理する。
     * 基本的には出力結果をストリームに書き出す。
     * @param filePath
     * @param paramMap
     * @param outputStream
     */
    public void request(String filePath, Map<String, List<String>> paramMap, Map<String, String> headMap, OutputStream outputStream) throws IOException;

    public void error(String code, OutputStream outputStream) throws IOException;

    public void forward(String filePath, String[] heads, BufferedReader reader, OutputStream outputStream) throws IOException;

}
