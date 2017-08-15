// (c) 2017 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class IOThrougher implements Runnable {

	private InputStream is;
	private OutputStream os;
	private String key;
	public IOThrougher(InputStream is, OutputStream os, String key) {
		this.is = is;
		this.os = os;
		this.key = key;
	}
	/* (非 Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	@Override
	public void run() {
		int length = 0;
		byte[] bytes = new byte[4 * 1024 * 1024];
		try {
			while ((length = is.read(bytes)) > 0) {
				os.write(bytes, 0, length);
				os.flush();
			}
		} catch (IOException e) {
			System.out.println("key:" + key);
			e.printStackTrace();
		}
	}

}
