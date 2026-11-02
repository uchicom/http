// (C) 2017 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

public class IOThrougherTest {

  @Test
  public void run() {
    byte[] data = "hello through".getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream os = new ByteArrayOutputStream();

    new IOThrougher(new ByteArrayInputStream(data), os).run();

    assertThat(os.toByteArray()).isEqualTo(data);
  }
}
