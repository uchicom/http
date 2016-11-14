/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Properties;

/**
 * @author Uchiyama Shigeki
 *
 */
public class WebFile {

	private File file = null;
	private OffsetDateTime lastModified;
	public static Properties mimeProperties;
	static {
		mimeProperties = new Properties();
		try {
			mimeProperties.load(Thread.currentThread().getContextClassLoader().getResource("com/uchicom/http/mime.properties").openStream());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * fileを取得します。
	 * @return file
	 */
	public File getFile() {
		return file;
	}

	/**
	 * fileを設定します。
	 * @param file
	 */
	public void setFile(File file) {
		this.file = file;
	}


	private String contentType = null;

	public String getContentType() {
		return contentType;
	}

	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 *
	 * @param file
	 */
	public WebFile(File file) {
		this.file = file;
		String name = file.getName();
		int lastIndex = name.lastIndexOf(".");
		if (lastIndex >= 0) {
			contentType = mimeProperties.getProperty(name.substring(lastIndex + 1));
		}
		if (contentType == null) {
            contentType = "text/plain";
		}
		lastModified = OffsetDateTime.ofInstant(Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
	}


	public long length() {
		return file.length();
	}

	public OffsetDateTime getLastModified() {
		return lastModified;
	}


}
