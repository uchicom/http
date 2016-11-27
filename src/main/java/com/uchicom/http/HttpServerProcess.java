package com.uchicom.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URLDecoder;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

public class HttpServerProcess implements ServerProcess {

	private Socket socket;

    protected static Router router;
	/** 最終処理時刻 */
	private long lastTime = System.currentTimeMillis();
	public HttpServerProcess(Parameter parameter, Socket socket) {
		this.socket = socket;
		router = Context.singleton().getRouter();
	}
	@Override
	public long getLastTime() {
		return lastTime; //TODO これabstracだな
	}

	@Override
	public void forceClose() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				// TODO 自動生成された catch ブロック
				e.printStackTrace();
			}//TODO これabstractだな
		};
	}
	@Override
	public void execute() {
		try {
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream os = socket.getOutputStream();
            //リクエストの分解、保持
            String head = br.readLine();
            if (head != null) {
                String[] heads = head.split(" ");
                if (heads.length == 3) {
                    //初期応答行を返却する
                    String fileName = "";
                    String[] urls = heads[1].split("\\?");
                    if (urls[0].endsWith("/")) {
                        if (urls[0].startsWith("/")) {
                            fileName = urls[0] + "index.htm";
                        } else {
                            fileName = "/" + urls[0] + "index.htm";
                        }
                    } else {
                        if (urls[0].startsWith("/")) {
                            fileName = urls[0];
                        } else {
                            fileName = "/" + urls[0];
                        }

                    }
                    Map<String, String[]> paramMap = null;
                    Map<String, String> headMap = new HashMap<>();
                    if (urls.length > 1) {
                        //GETパラメータありなので解析。
                        paramMap = createGetParamMap(urls[1]);
                    }
                    TemporalAccessor ifModified = null;
                    String line = br.readLine();
                    while (line != null && !"".equals(line)) {
                        line = br.readLine();
                        System.out.println(line);
                        int index = line.indexOf(":");
                        if (index >= 0) {
	                        headMap.put(line.substring(0, index), line.substring(index + 2));
	                        if (line.startsWith("If-Modified-Since:")) {
	                        	ifModified = Constants.formatter.parse(line.substring(19));
	                        }
                        } else {
                        	headMap.put(line, line);
                        }
                    }
                    // TODO WebFileを継承してBasicWebFileとする
                    if (router.exists(fileName)) {
                    	if (ifModified != null && router.isModified(fileName, ifModified)) {
                    		//キャッシュ
                            os.write("HTTP/1.1 304 Not Modified \r\n".getBytes());
                            os.write("Date: ".getBytes());
                            os.write(Constants.formatter.format(OffsetDateTime.now()).getBytes());
                            os.write("\r\n".getBytes());
                            os.write("\r\n".getBytes());
                    	} else {
                    		router.request(fileName, socket.getRemoteSocketAddress(), paramMap, headMap, os);
                    	}
                    } else {
                        //Not Found
                        router.error("404", os);
                    }

                } else {
                    //Bad Request
                    router.error("400", os);
                }

            } else {
                //Bad Request
                router.error("400", os);
            }
            br.close();
            os.close();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    socket = null;
                }
            }
        }

	}

	public Map<String, String[]> createGetParamMap(String get) {
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
