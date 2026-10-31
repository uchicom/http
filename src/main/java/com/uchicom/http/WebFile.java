// (C) 2012 uchicom
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
 */
public class WebFile {

  private File file;
  public static Properties mimeProperties;

  private Properties basic;

  /** キャッシュ対象とする最大ファイルサイズ(バイト)。0以下の場合はキャッシュしない */
  private final long cacheLimit;

  /** キャッシュした内容と、読み込み時点のファイル更新時刻 */
  private static final class Cache {
    final byte[] bytes;
    final long lastModified;

    Cache(byte[] bytes, long lastModified) {
      this.bytes = bytes;
      this.lastModified = lastModified;
    }
  }

  private volatile Cache cache;

  static {
    mimeProperties = new Properties();
    try {
      mimeProperties.load(
          Thread.currentThread()
              .getContextClassLoader()
              .getResource("com/uchicom/http/mime.properties")
              .openStream());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  /**
   * fileを取得します。
   *
   * @return file
   */
  public File getFile() {
    return file;
  }

  /**
   * fileを設定します。
   *
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
   * 常にキャッシュ対象とするWebFileを生成する.
   *
   * @param file
   */
  public WebFile(File file) {
    this(file, Long.MAX_VALUE);
  }

  /**
   * @param file
   * @param cacheLimit キャッシュ対象とする最大ファイルサイズ(バイト)。0以下の場合はキャッシュせず毎回ファイルから読み込む
   */
  public WebFile(File file, long cacheLimit) {
    this.file = file;
    this.cacheLimit = cacheLimit;
    String name = file.getName();
    int lastIndex = name.lastIndexOf(".");
    if (lastIndex >= 0) {
      contentType = mimeProperties.getProperty(name.substring(lastIndex + 1));
    }
    if (contentType == null) {
      contentType = "text/plain";
    }
  }

  public long length() {
    return file.length();
  }

  /** 現在のファイル更新時刻を返す. 毎回ファイルから取得する. */
  public OffsetDateTime getLastModified() {
    return OffsetDateTime.ofInstant(
        Instant.ofEpochMilli(file.lastModified()), ZoneId.systemDefault());
  }

  /** 現在のファイルサイズでキャッシュ対象となる場合にtrueを返す. */
  boolean isCacheable() {
    return cacheLimit > 0 && file.length() <= cacheLimit;
  }

  public void write(PrintStream ps) throws FileNotFoundException, IOException {
    if (!isCacheable()) {
      cache = null;
      try (FileInputStream fis = new FileInputStream(file); ) {
        fis.transferTo(ps);
      }
      return;
    }
    Cache current = cache;
    if (current == null || file.lastModified() != current.lastModified) {
      synchronized (this) {
        // 読み込み前の更新時刻を保持し、読み込み中に更新されても次回再読み込みされるようにする
        long modified = file.lastModified();
        byte[] bytes = new byte[(int) file.length()];
        try (FileInputStream fis = new FileInputStream(file); ) {
          fis.readNBytes(bytes, 0, bytes.length);
        }
        current = new Cache(bytes, modified);
        cache = current;
      }
    }
    ps.write(current.bytes);
  }

  public boolean isBasic() {
    return basic != null;
  }

  public void setBasic(Properties basic) {
    this.basic = basic;
  }
}
