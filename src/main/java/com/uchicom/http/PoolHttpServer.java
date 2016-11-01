/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.uchicom.server.Parameter;

/**
 * @author Uchiyama Shigeki
 *
 */
public class PoolHttpServer extends SingleHttpServer {
	ExecutorService exec;
    public PoolHttpServer(Parameter parameter) {
		super(parameter);
		exec = Executors.newFixedThreadPool(parameter.getInt("pool"));
	}


	/* (非 Javadoc)
	 * @see com.uchicom.dirsmtp.AbstractSocketServer#execute(java.net.ServerSocket)
	 */
	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
            final HttpServerProcess process = new HttpServerProcess(parameter, serverSocket.accept());
            exec.execute(new Runnable() {
            	public void run() {
            		process.execute();
            	}
            });
        }
	}

}
