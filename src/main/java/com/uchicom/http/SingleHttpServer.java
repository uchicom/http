/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.IOException;
import java.net.ServerSocket;

import com.uchicom.server.AbstractSocketServer;
import com.uchicom.server.Parameter;


/**
 * baseファイルにwwwを指定した場合は
 * その配下にhtml,error,appsを用意する。
 * htmlは必須、なければエラー
 * errorは任意
 * appsは任意、ある場合は、アプリケーション動作モードで起動する。
 * @author Uchiyama Shigeki
 *
 */
public class SingleHttpServer extends AbstractSocketServer {

	public SingleHttpServer(Parameter parameter) {
		super(parameter);
		router.init(parameter.getFile("base"));
	}

    protected static Router router = new DefaultRouter();

	@Override
	protected void execute(ServerSocket serverSocket) throws IOException {
		while (true) {
            HttpServerProcess process = new HttpServerProcess(parameter, serverSocket.accept());
            process.execute();
        }
	}
}
