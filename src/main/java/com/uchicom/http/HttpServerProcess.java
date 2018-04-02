package com.uchicom.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.HashMap;
import java.util.Map;

import com.uchicom.server.ServerProcess;
import com.uchicom.util.Parameter;

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
		System.out.println("forceClose");
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
		System.out.println("execute");
		try {
            BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream os = socket.getOutputStream();
            //リクエストの分解、保持
            String head = br.readLine();
    		System.out.println( socket.getRemoteSocketAddress() +"head:" + head);
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
                    if (router.isForward(fileName)) {
                    	router.forward(fileName, heads, br, os);
                    	return;
                    }
                    Map<String, String[]> paramMap = null;
                    Map<String, String> headMap = new HashMap<>();
                    if (heads[0].equals("GET") && urls.length > 1) {
                        //GETパラメータありなので解析。
                        paramMap = router.createParamMap(urls[1]);
                    }
                    TemporalAccessor ifModified = null;
                    String line = br.readLine();
                    System.out.println( socket.getRemoteSocketAddress() + line);
                    while (line != null && !"".equals(line)) {
                        line = br.readLine();
                        System.out.println( socket.getRemoteSocketAddress() + line);
                        int index = line.indexOf(":");
                        if (index >= 0) {
	                        headMap.put(line.substring(0, index), line.substring(index + 2));
	                        if (line.startsWith("If-Modified-Since:")) {
	                        	try {
	                        		ifModified = Constants.formatter.parse(line.substring(19));
	                        	} catch (DateTimeParseException e) {
	                        		e.printStackTrace();
	                        	}
	                        }
                        } else {
                        	headMap.put(line, line);
                        }
                    }
                    System.out.println( socket.getRemoteSocketAddress() + "middle:" + line);
                    if (headMap.containsKey("Content-Length")) {
                    	int contentLength = Integer.parseInt(headMap.get("Content-Length"));
                    	char[] cha = new char[contentLength];
                        int length = 0;
                        int index = 0;
                        while ((length = br.read(cha, index, contentLength - index)) > 0) {
                        	index += length;
                        }
                        if (headMap.containsKey("Content-Type")) {
                        	if (headMap.get("Content-Type").indexOf("multipart/form-data") >= 0) {
                        		//TODO マルチパート未実装
                        	} else if ("POST".equals(heads[0])){
                        		paramMap = router.createParamMap(new String(cha));
                        	}
                        }
                        System.out.println( socket.getRemoteSocketAddress() + "body" + new String(cha) );
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
                    		router.request(heads[0], fileName, socket.getRemoteSocketAddress(), paramMap, headMap, os);
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
                    System.out.println("close!");
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    socket = null;
                }
            }
        }

	}

}
