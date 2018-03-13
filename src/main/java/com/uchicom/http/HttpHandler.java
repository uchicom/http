/**
 * (c) 2013 uchicom
 */
package com.uchicom.http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.uchicom.server.Handler;
import com.uchicom.util.Parameter;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class HttpHandler implements Handler {

	ByteBuffer readBuff = ByteBuffer.allocate(1024);
	ByteBuffer writeBuff = ByteBuffer.allocate(1024);
	String fileName = null;
	StringBuffer strBuff = new StringBuffer();

	protected static Router router;

	TemporalAccessor ifModified;

	Map<String, List<String>> paramMap = null;
	Map<String, String> headMap = new HashMap<>();
	public HttpHandler(Parameter parameter) {
		router = Context.singleton().getRouter();
	}

	/* (non-Javadoc)
	 * @see com.uchicom.http.Handler#handle(java.nio.channels.SelectionKey)
	 */
	@Override
	public void handle(SelectionKey key) throws IOException {
		SocketChannel channel = (SocketChannel) key.channel();
		if (key.isReadable()) {
			int length = channel.read(readBuff);
			if (length > 0) {
				readBuff.flip();
				fileName = request(new String(readBuff.array()));
			}
			readBuff.clear();
			key.interestOps(SelectionKey.OP_WRITE);
		}

		if (key.isWritable()) {

			ByteBuffer buff = null;
			buff = response(channel, fileName).asReadOnlyBuffer();

			try {
				buff.position(0);
				channel.write(buff);
			} catch (IOException e) {
				System.err.println(channel.socket().getInetAddress());
				e.printStackTrace();
			}
			strBuff.setLength(0);
			readBuff.clear();
			key.cancel();
		}
	}

	public String head(String head) throws IOException {

		String fileName = "";
		String[] heads = head.split(" ");
		if (heads.length == 3) {
			//初期応答行を返却する
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
			//			if (router.isForward(fileName)) {
			//				router.forward(fileName, heads, br, os);
			//				return;
			//			}
			if (urls.length > 1) {
				//GETパラメータありなので解析。
				paramMap = createGetParamMap(urls[1]);
			}
		}
		return fileName;
	}

	public Map<String, List<String>> createGetParamMap(String get) {
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
		return map;
	}

	public String request(String request) throws IOException {
		int rnIndex = request.indexOf("\r\n");
		String head = request.substring(0, rnIndex);
		String fileName = head(head);

//		System.out.println("head:" + head);
		String head2 = request.substring(rnIndex + 2);
		String[] heads2 = head2.split("\r\n");
		for (String line : heads2) {
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
		return fileName;
	}

	/**
	 * channel と bytebufferを引き回したほうが早いかも.
	 * @param channel
	 * @param fileName
	 * @return
	 * @throws IOException
	 */
	public ByteBuffer response(SocketChannel channel, String fileName) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
		if (router.exists(fileName)) {
			router.request(fileName, paramMap, headMap, baos);
		} else {
			//Not Found
			router.error("404", baos);
		}
		return ByteBuffer.wrap(baos.toByteArray());
	}

}
