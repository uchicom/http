// (C) 2017 uchicom
package com.uchicom.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author uchicom: Shigeki Uchiyama
 */
public class IOThrougher implements Runnable {

  private static final int BUFFER_SIZE = 8 * 1024;

  private InputStream is;
  private OutputStream os;
  private Runnable onActivity;

  public IOThrougher(InputStream is, OutputStream os) {
    this(is, os, null);
  }

  /**
   * @param onActivity データを中継するたびに呼ばれる処理(null可)
   */
  public IOThrougher(InputStream is, OutputStream os, Runnable onActivity) {
    this.is = is;
    this.os = os;
    this.onActivity = onActivity;
  }

  /* (非 Javadoc)
   * @see java.lang.Runnable#run()
   */
  @Override
  public void run() {
    int length = 0;
    byte[] bytes = new byte[BUFFER_SIZE];
    try {
      while ((length = is.read(bytes)) > 0) {
        os.write(bytes, 0, length);
        os.flush();
        if (onActivity != null) {
          onActivity.run();
        }
      }
    } catch (IOException e) {
      // 相手の切断・自側のクローズによる終了は正常系として扱う
    }
  }
}
