/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.SocketAddress;
import java.net.URLDecoder;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

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
     * @param method
     * @param filePath
     * @param paramMap
     * @param outputStream
     */
    public void request(String method, String filePath, SocketAddress address, Map<String, String[]> paramMap, Map<String, String> headMap, OutputStream outputStream) throws IOException;

    public void error(String code, OutputStream outputStream) throws IOException;

    public void forward(String filePath, String[] heads, BufferedReader reader, OutputStream outputStream) throws IOException;
	default Map<String, String[]> createParamMap(String get) {
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
	    return stringMap;
	}

}
