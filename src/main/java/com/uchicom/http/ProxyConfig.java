// (C) 2026 uchicom
package com.uchicom.http;

import com.uchicom.util.Parameter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * プロキシの設定.
 *
 * <ul>
 *   <li>proxy_ports: CONNECTを許可する接続先ポート(カンマ区切り、標準は443)
 *   <li>proxy_allow_private: trueでループバック・プライベートアドレスへの接続を許可する
 *   <li>proxy_auth: ユーザー名=パスワード形式のファイル。指定時はProxy-Authorization(Basic)を必須にする
 * </ul>
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class ProxyConfig {

  private final Set<Integer> connectPorts;
  private final boolean allowPrivate;
  private final Properties users;

  public ProxyConfig(Set<Integer> connectPorts, boolean allowPrivate, Properties users) {
    this.connectPorts = connectPorts;
    this.allowPrivate = allowPrivate;
    this.users = users;
  }

  /**
   * パラメータから設定を作る.
   *
   * @return 設定。不正な場合はメッセージを出力してnull
   */
  public static ProxyConfig create(Parameter parameter, PrintStream ps) {
    Set<Integer> ports = new HashSet<>();
    if (parameter.is("proxy_ports")) {
      for (String port : parameter.get("proxy_ports").split(",", 0)) {
        try {
          int value = Integer.parseInt(port.trim());
          if (value < 1 || value > 65535) {
            throw new NumberFormatException();
          }
          ports.add(value);
        } catch (NumberFormatException e) {
          ps.println("proxy_ports must be comma separated ports(1-65535): " + port);
          return null;
        }
      }
    } else {
      ports.add(443);
    }
    boolean allowPrivate =
        parameter.is("proxy_allow_private") && parameter.get("proxy_allow_private").equals("true");
    Properties users = null;
    if (parameter.is("proxy_auth")) {
      users = new Properties();
      try (InputStream is = new FileInputStream(parameter.get("proxy_auth"))) {
        users.load(is);
      } catch (IOException e) {
        ps.println("proxy_auth cannot be read: " + parameter.get("proxy_auth"));
        return null;
      }
      if (users.isEmpty()) {
        ps.println("proxy_auth has no users: " + parameter.get("proxy_auth"));
        return null;
      }
    }
    return new ProxyConfig(ports, allowPrivate, users);
  }

  public boolean isConnectPortAllowed(int port) {
    return connectPorts.contains(port);
  }

  public boolean isAllowPrivate() {
    return allowPrivate;
  }

  public boolean isAuthRequired() {
    return users != null;
  }

  public boolean authenticate(String authorization) {
    return BasicAuthenticator.authenticate(authorization, users);
  }
}
