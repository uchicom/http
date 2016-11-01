/**
 * (c) 2014 uchicom
 */
package com.uchicom.http;

import java.io.PrintStream;

import com.uchicom.server.Parameter;
import com.uchicom.server.Server;

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

        return true;
    }
    public Server createServer() {
    	Server server = null;
		switch (get("type")) {
		case "multi":
			server = new MultiHttpServer(this);
			break;
		case "pool":
			server = new PoolHttpServer(this);
			break;
		case "single":
			server = new SingleHttpServer(this);
			break;
		case "selector":
			server = new SelectorHttpServer(this);
			break;
		}
    	return server;
    }
}
