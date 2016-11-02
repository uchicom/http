// (c) 2016 uchicom
package com.uchicom.http;

import java.io.File;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class Context {

	private static final Context context = new Context();
	private static final Router router = new DefaultRouter();
	private Context() {

	}
	public static Context singleton() {
		return context;
	}
	public Router getRouter() {
		return router;
	}
	public void init(File file) {
		router.init(file);
	}
}
