// (C) 2026 uchicom
package com.uchicom.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Properties;

/**
 * Basic認証ヘッダーの検証.
 *
 * @author uchicom: Shigeki Uchiyama
 */
public final class BasicAuthenticator {

  private BasicAuthenticator() {}

  /**
   * Basic認証を検証する. ヘッダーが不正な場合も例外にせず、認証失敗として扱う.
   *
   * @param authorization Authorization系ヘッダーの値(null可)
   * @param users ユーザー名=パスワードのProperties(null可)
   * @return 認証できた場合true
   */
  public static boolean authenticate(String authorization, Properties users) {
    if (authorization == null || users == null) {
      return false;
    }
    int typeIndex = authorization.indexOf(' ');
    if (typeIndex < 0 || !"Basic".equals(authorization.substring(0, typeIndex))) {
      return false;
    }
    String decoded;
    try {
      decoded =
          new String(
              Base64.getDecoder().decode(authorization.substring(typeIndex + 1).trim()),
              StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      return false;
    }
    int index = decoded.indexOf(':');
    if (index < 0) {
      return false;
    }
    String password = users.getProperty(decoded.substring(0, index));
    if (password == null) {
      return false;
    }
    return MessageDigest.isEqual(
        decoded.substring(index + 1).getBytes(StandardCharsets.UTF_8),
        password.getBytes(StandardCharsets.UTF_8));
  }
}
