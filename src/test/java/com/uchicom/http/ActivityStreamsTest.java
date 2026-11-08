// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.uchicom.util.Parameter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

public class ActivityStreamsTest {

  @Test
  public void inputNotifiesOnRead() throws Exception {
    AtomicInteger count = new AtomicInteger();
    var is =
        ActivityStreams.wrap(
            new ByteArrayInputStream(new byte[] {1, 2, 3}), count::incrementAndGet);

    assertThat(is.read()).isEqualTo(1);
    assertThat(is.read(new byte[2], 0, 2)).isEqualTo(2);
    assertThat(count.get()).isEqualTo(2);

    // EOFでは通知しない
    assertThat(is.read()).isEqualTo(-1);
    assertThat(count.get()).isEqualTo(2);
  }

  @Test
  public void outputNotifiesOnWrite() throws Exception {
    AtomicInteger count = new AtomicInteger();
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    var os = ActivityStreams.wrap(out, count::incrementAndGet);

    os.write(1);
    os.write(new byte[] {2, 3}, 0, 2);

    assertThat(count.get()).isEqualTo(2);
    assertThat(out.toByteArray()).containsExactly(1, 2, 3);
  }

  @Test
  public void httpProcessUpdatesLastTimeOnCommunication() throws Exception {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream())
        .thenReturn(new ByteArrayInputStream("\r\n".getBytes(StandardCharsets.ISO_8859_1)));
    when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    HttpProcess process = new HttpProcess(new DefaultRouter(), socket, Constants.DEFAULT_TIMEOUT);
    long before = process.getLastTime();

    Thread.sleep(5);
    process.execute();

    assertThat(process.getLastTime()).isGreaterThan(before);
  }

  @Test
  public void redirectProcessUpdatesLastTimeOnCommunication() throws Exception {
    Socket socket = mock(Socket.class);
    when(socket.getInputStream())
        .thenReturn(new ByteArrayInputStream("\r\n".getBytes(StandardCharsets.ISO_8859_1)));
    when(socket.getOutputStream()).thenReturn(new ByteArrayOutputStream());
    RedirectHttpServerProcess process =
        new RedirectHttpServerProcess(
            new Parameter(new String[0]), new DefaultRouter(), socket, Constants.DEFAULT_TIMEOUT);
    long before = process.getLastTime();

    Thread.sleep(5);
    process.execute();

    assertThat(process.getLastTime()).isGreaterThan(before);
  }
}
