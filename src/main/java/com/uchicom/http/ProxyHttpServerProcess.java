// (c) 2017 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

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
			byte[] headBytes = new byte[4*1024];
			int length = 0;
			int index = 0;
			while ((length = is.read(headBytes, index, 1)) > 0) {
				if (headBytes[index] == '\n' && index - 1 > 0 && headBytes[index - 1] == '\r') {
					break;
				}
				index += length;
				if (headBytes.length == index) {
					break;
				}
			}
			//リクエストの分解、保持
			String head = new String(headBytes, 0, index);
			System.out.println(head);
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
						port = 80;
					}

					if (!ssl) {
						send = new Socket(host, port);
						send.getOutputStream().write((heads[0] + " " + heads[1].substring(heads[1].indexOf('/', 7)) + " " + heads[2]
								+ "\r\n").getBytes());
						System.out.println("header出力完了");
						Thread remoteThrougher = new Thread(new IOThrougher(is, send.getOutputStream(), "socket:i→o"));
						remoteThrougher.setDaemon(true);
						remoteThrougher.start();

						Thread localThrougher = new Thread(new IOThrougher(send.getInputStream(), os, "socket:o←i"));
						localThrougher.setDaemon(true);
						localThrougher.start();

					} else {
						SSLSocket sslSocket = (SSLSocket)SSLSocketFactory.getDefault().createSocket(host, port);
						sslSocket.startHandshake();
						System.out.println("handshake完了");
						Thread remoteThrougher = new Thread(new IOThrougher(is, sslSocket.getOutputStream(), "ssl:i→o"));
						remoteThrougher.setDaemon(true);
						remoteThrougher.start();

						Thread localThrougher = new Thread(new IOThrougher(sslSocket.getInputStream(), os, "ssl:o←i"));
						localThrougher.setDaemon(true);
						localThrougher.start();

					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}

}
