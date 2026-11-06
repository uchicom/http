// (C) 2014 uchicom
package com.uchicom.http;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.Server;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.server.VirtualSocketServer;
import com.uchicom.util.Parameter;
import java.io.PrintStream;

/**
 * Parameterに統一の機能を持たせるほうがいいかな。 そのあと入力チェックを各サーバプロセスに用意しておく。
 *
 * @author uchicom: Shigeki Uchiyama
 */
public class HttpParameter extends Parameter {

  private Router router;
  private ProxyConfig proxyConfig;
  private int timeout = Constants.DEFAULT_TIMEOUT;

  public HttpParameter(String[] args) {
    super(args);
  }

  public boolean init(PrintStream ps) {

    // 基準フォルダ
    if (!is("dir")) {
      put("dir", Constants.DEFAULT_DIR);
    }
    // 実行するサーバのタイプ
    if (!is("type")) {
      put("type", "single");
    }
    // ホスト名
    if (!is("host")) {
      put("host", "localhost");
    }
    // 待ち受けポート
    if (!is("port")) {
      put("port", Constants.DEFAULT_PORT);
    }
    // 受信する接続 (接続要求) のキューの最大長
    if (!is("back")) {
      put("back", Constants.DEFAULT_BACK);
    }
    // プールするスレッド数
    if (!is("pool")) {
      put("pool", Constants.DEFAULT_POOL);
    }

    // 読み込みタイムアウト(ミリ秒)。0は無制限
    try {
      timeout = getInt("timeout", Constants.DEFAULT_TIMEOUT);
      if (timeout < 0) {
        throw new NumberFormatException();
      }
    } catch (NumberFormatException e) {
      ps.println("timeout must be a non-negative integer (ms): " + get("timeout"));
      return false;
    }

    router = new DefaultRouter();
    // キャッシュ対象の最大ファイルサイズ(KB)。指定なしまたは0はキャッシュなし
    long cacheSize = 0;
    if (is("cache_max_kb")) {
      try {
        long kb = Long.parseLong(get("cache_max_kb"));
        if (kb < 0) {
          throw new NumberFormatException();
        }
        cacheSize = Math.multiplyExact(kb, 1024L);
      } catch (NumberFormatException | ArithmeticException e) {
        ps.println("cache_max_kb must be a non-negative integer: " + get("cache_max_kb"));
        return false;
      }
    }
    router.init(getFile("dir"), cacheSize);

    if ("proxy".equals(get("type"))) {
      proxyConfig = ProxyConfig.create(this, ps);
      if (proxyConfig == null) {
        return false;
      }
    }

    return true;
  }

  public Server createServer() {
    return switch (get("type")) {
      case "multi" ->
          new MultiSocketServer(
              this, (parameter, socket) -> new HttpProcess(router, socket, timeout));
      case "pool" ->
          new PoolSocketServer(
              this, (parameter, socket) -> new HttpProcess(router, socket, timeout));
      case "virtual" ->
          new VirtualSocketServer(
              this, (parameter, socket) -> new HttpProcess(router, socket, timeout));
      case "single" ->
          new SingleSocketServer(
              this, (parameter, socket) -> new HttpProcess(router, socket, timeout));
      case "redirect" ->
          new MultiSocketServer(
              this,
              (parameter, socket) ->
                  new RedirectHttpServerProcess(parameter, router, socket, timeout));
      case "proxy" ->
          new MultiSocketServer(
              this,
              (parameter, socket) -> new ProxyHttpServerProcess(proxyConfig, socket, timeout));
      default -> null;
    };
  }
}
