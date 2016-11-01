/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.IOException;
import java.net.ServerSocket;

import com.uchicom.server.Parameter;

/**
 * @author Uchiyama Shigeki
 *
 */
public class MultiHttpServer extends SingleHttpServer {

	public MultiHttpServer(Parameter parameter) {
		super(parameter);
	}

	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
			final HttpServerProcess process = new HttpServerProcess(parameter,
					serverSocket.accept());
			processList.add(process);
			Thread thread = new Thread() {
				public void run() {
					process.execute();
				}
			};
			thread.setDaemon(true);
			thread.start();
		}
	}

}
