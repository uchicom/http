// (C) 2026 uchicom
package com.uchicom.http;

import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 読み書きのたびに通知するストリーム.
 *
 * <p>ServerProcess#getLastTime() を通信のたびに更新するために使用する。
 */
final class ActivityStreams {

  private ActivityStreams() {}

  static InputStream wrap(InputStream is, Runnable onActivity) {
    return new FilterInputStream(is) {
      @Override
      public int read() throws IOException {
        int b = in.read();
        if (b >= 0) {
          onActivity.run();
        }
        return b;
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        int length = in.read(b, off, len);
        if (length > 0) {
          onActivity.run();
        }
        return length;
      }
    };
  }

  static OutputStream wrap(OutputStream os, Runnable onActivity) {
    return new FilterOutputStream(os) {
      @Override
      public void write(int b) throws IOException {
        out.write(b);
        onActivity.run();
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
        onActivity.run();
      }
    };
  }
}
