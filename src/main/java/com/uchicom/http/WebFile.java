/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Properties;

/**
 * @author Uchiyama Shigeki
 *
 */
public class WebFile {

	private File file;
	private OffsetDateTime lastModified;
	public static Properties mimeProperties;
	private long lastModifiedTime;
	
	private Properties basic;
	
	private byte[] cache;
	
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
		lastModifiedTime = file.lastModified();
	}


	public long length() {
		return file.length();
	}

	public OffsetDateTime getLastModified() {
		return lastModified;
	}

	public void write(PrintStream ps) throws FileNotFoundException, IOException {
		if (cache == null || file.lastModified() != lastModifiedTime) {
			synchronized(this) {
				byte[] cache = new byte[(int)file.length()];
				try (FileInputStream fis = new FileInputStream(file);) {
					int length = 0;
					int index = 0;
					while ((length = fis.read(cache)) > 0) {
						ps.write(cache, index, length);
						index += length;
					}
				}
				this.cache = cache;
			}
		}
		ps.write(cache);
	}
	
	public boolean isBasic() {
		return basic != null;
	}
	public void setBasic(Properties basic) {
		this.basic = basic;
	}

}
