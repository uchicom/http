/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.IOException;
import java.net.ServerSocket;

import com.uchicom.server.AbstractSocketServer;
import com.uchicom.server.Parameter;


/**
 * 80→443 に転送する用のリダイレクトサーバ
 * @author Uchiyama Shigeki
 *
 */
public class RedirectHttpServer extends AbstractSocketServer {

    public RedirectHttpServer(Parameter parameter) {
		super(parameter);
	}

	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
            RedirectHttpServerProcess process = new RedirectHttpServerProcess(parameter, serverSocket.accept());
            process.execute();
        }
	}

}
