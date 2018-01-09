// (c) 2017 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import com.uchicom.server.ServerProcess;
import com.uchicom.util.Parameter;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class ProxyHttpServerProcess implements ServerProcess {

	private Socket socket;
	private Socket send;

	public ProxyHttpServerProcess(Parameter parameter, Socket socket) {
		this.socket = socket;
	}

	/* (非 Javadoc)
	 * @see com.uchicom.server.ServerProcess#getLastTime()
	 */
	@Override
	public long getLastTime() {
		return System.currentTimeMillis();
	}

	/* (非 Javadoc)
	 * @see com.uchicom.server.ServerProcess#forceClose()
	 */
	@Override
	public void forceClose() {
		if (socket != null) {
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			} //TODO これabstractだな
		}
		;

	}

	/* (非 Javadoc)
	 * @see com.uchicom.server.ServerProcess#execute()
	 */
	@Override
	public void execute() {
		System.out.println("execute");
		try {
			InputStream is = socket.getInputStream();
			OutputStream os = socket.getOutputStream();
			byte[] headBytes = new byte[4 * 1024];
			int length = 0;
			int index = 0;
			while ((length = is.read(headBytes, index, 1)) > 0) {
				if (headBytes[index] == '\n' && index - 3 > 0 && headBytes[index - 1] == '\r'
						&& headBytes[index - 2] == '\n' && headBytes[index - 3] == '\r') {
					break;
				}
				index += length;
				if (headBytes.length == index) {
					break;
				}
			}
			//リクエストの分解、保持
			String head = new String(headBytes, 0, index);
			System.out.println("[" + head + "]");
			String[] splits = head.split("\r\n");
			head = splits[0];
			System.out.println("[" + head + "]");
			if (head != null) {
				//SSL
				String[] heads = head.split(" ");
				if (heads.length == 3) {
					String host = null;
					int port = 0;
					boolean ssl = false;
					if (heads[0].equals("CONNECT")) {
						String[] address = heads[1].split(":");
						ssl = true;
						host = address[0];
						port = Integer.parseInt(address[1]);
					} else {
						host = heads[1].substring(7, heads[1].indexOf('/', 7));
						int splitIndex = host.indexOf(":");
						if (splitIndex < 0) {
							port = 80;
						} else {
							port = Integer.parseInt(host.substring(splitIndex + 1));
							host = host.substring(0, splitIndex);
						}
					}
					System.out.println("host:" + host);
					System.out.println("port:" + port);

					send = new Socket(host, port);
					if (ssl) {
						os.write("HTTP/1.1 200 Connection Established\r\n\r\n".getBytes());
						os.flush();
					} else {
						String get = heads[0] + " " + heads[1].substring(heads[1].indexOf('/', 7)) + " " + heads[2]
								+ "\r\n";
						System.out.println("get:" + get);
						OutputStream sos = send.getOutputStream();
						sos.write(get.getBytes());
						System.out.println("get:" + get);
						for (int i = 1; i < splits.length; i++) {
							if (splits[i].contains("Proxy-")) {
								System.out.println(splits[i].replace("Proxy-", ""));
								sos.write((splits[i].replace("Proxy-", "") + "\r\n").getBytes());
							} else {
								System.out.println(splits[i]);
								sos.write((splits[i] + "\r\n").getBytes());
							}
						}
						sos.write(("\r\n").getBytes());
						sos.flush();
					}
					System.out.println("header出力完了");
					Thread remoteThrougher = new Thread(new IOThrougher(is, send.getOutputStream(), "socket:i→o"));
					remoteThrougher.setDaemon(true);
					remoteThrougher.start();

					Thread localThrougher = new Thread(new IOThrougher(send.getInputStream(), os, "socket:o←i"));
					localThrougher.setDaemon(true);
					localThrougher.start();

				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
