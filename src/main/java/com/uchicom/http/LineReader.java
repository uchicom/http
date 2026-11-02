// (C) 2026 uchicom
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.IOException;

/** 上限付きの行読み取り. */
public class LineReader {

  private LineReader() {}

  /** 行の長さが上限を超えたことを表す. */
  public static class LineTooLongException extends IOException {
    private static final long serialVersionUID = 1L;

    public LineTooLongException() {
      super("line too long");
    }
  }

  /**
   * 1行読む. 末尾のCRLF(またはLF)は取り除く.
   *
   * @param reader ISO-8859-1で生成されたreader(1文字=1バイト)
   * @return 行。終端に達した場合はnull
   * @throws LineTooLongException 改行を除いた長さが{@link Constants#MAX_LINE_LENGTH}を超えた場合
   */
  public static String readLine(BufferedReader reader) throws IOException {
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = reader.read()) >= 0) {
      if (c == '\n') {
        int length = sb.length();
        if (length > 0 && sb.charAt(length - 1) == '\r') {
          sb.setLength(length - 1);
        }
        if (sb.length() > Constants.MAX_LINE_LENGTH) {
          throw new LineTooLongException();
        }
        return sb.toString();
      }
      // 末尾のCR分を1文字だけ余分に読む
      if (sb.length() > Constants.MAX_LINE_LENGTH) {
        throw new LineTooLongException();
      }
      sb.append((char) c);
    }
    return sb.length() > 0 ? sb.toString() : null;
  }
}
