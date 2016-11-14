/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.time.temporal.TemporalAccessor;
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
    /**
     * リクエストを処理する。
     * 基本的には出力結果をストリームに書き出す。
     * @param filePath
     * @param paramMap
     * @param outputStream
     */
    public void request(String filePath, SocketAddress address, Map<String, String[]> paramMap, OutputStream outputStream) throws IOException;

    public void error(String code, OutputStream outputStream) throws IOException;
}
