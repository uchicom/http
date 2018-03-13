/**
 * (c) 2014 uchicom
 */
package com.uchicom.http;

import java.io.PrintStream;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.SelectorServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.util.Parameter;

/**
 * Parameterに統一の機能を持たせるほうがいいかな。
 * そのあと入力チェックを各サーバプロセスに用意しておく。
 *
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class HttpParameter extends Parameter {

    public HttpParameter(String[] args) {
    	super(args);
    }

    public boolean init(PrintStream ps) {

    	// 基準フォルダ
    	if (!is("dir")) {
    		put("dir", Constants.DEFAULT_DIR);
    	}
        // 実行するサーバのタイプ
    	if (!is("type")) {
    		put("type", "single");
    	}
    	// ホスト名
    	if (!is("host")) {
    		put("host", "localhost");
    	}
    	// 待ち受けポート
    	if (!is("port")) {
    		put("port", Constants.DEFAULT_PORT);
    	}
    	// 受信する接続 (接続要求) のキューの最大長
    	if (!is("back")) {
    		put("back", Constants.DEFAULT_BACK);
    	}
    	// プールするスレッド数
    	if (!is("pool")) {
    		put("pool", Constants.DEFAULT_POOL);
    	}

    	Context.singleton().init(getFile("dir"));

        return true;
    }
    public Server createServer() {
    	Server server = null;
		switch (get("type")) {
		case "multi":
			server = new MultiSocketServer(this, (parameter, socket)->{
				return new HttpProcess(parameter, socket);
			});
			break;
		case "pool":
			server = new PoolSocketServer(this, (parameter, socket)->{
				return new HttpProcess(parameter, socket);
			});
			break;
		case "single":
			server = new SingleSocketServer(this, (parameter, socket)->{
				return new HttpProcess(parameter, socket);
			});
			break;
		case "selector":
			server = new SelectorServer(this, ()->{
				return new HttpHandler(this);
			});
			break;
		case "redirect":
			server = new MultiSocketServer(this, (parameter, socket)->{
				return new RedirectHttpServerProcess(parameter, socket);
			});
			break;
		case "proxy":
			server = new MultiSocketServer(this, (parameter, socket)->{
				return new ProxyHttpServerProcess(parameter, socket);
			});
			break;
		}
    	return server;
    }
}
