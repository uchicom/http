// (C) 2026 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import com.uchicom.server.MultiSocketServer;
import com.uchicom.server.PoolSocketServer;
import com.uchicom.server.SingleSocketServer;
import com.uchicom.server.VirtualSocketServer;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HttpParameterTest {

  @TempDir Path baseDir;

  private HttpParameter create(String type) throws Exception {
    HttpParameter parameter =
        new HttpParameter(new String[] {"-type", type, "-dir", baseDir.toString()});
    assertThat(parameter.init(new PrintStream(new ByteArrayOutputStream()))).isTrue();
    return parameter;
  }

  @Test
  public void createServerVirtual() throws Exception {
    assertThat(create("virtual").createServer()).isInstanceOf(VirtualSocketServer.class);
  }

  @Test
  public void createServerExistingTypes() throws Exception {
    assertThat(create("single").createServer()).isInstanceOf(SingleSocketServer.class);
    assertThat(create("multi").createServer()).isInstanceOf(MultiSocketServer.class);
    assertThat(create("pool").createServer()).isInstanceOf(PoolSocketServer.class);
  }

  @Test
  public void redirectRequiresRedirectParameter() throws Exception {
    HttpParameter parameter =
        new HttpParameter(new String[] {"-type", "redirect", "-dir", baseDir.toString()});
    assertThat(parameter.init(new PrintStream(new ByteArrayOutputStream()))).isFalse();

    parameter =
        new HttpParameter(
            new String[] {
              "-type", "redirect", "-dir", baseDir.toString(), "-redirect", "http://example.com"
            });
    assertThat(parameter.init(new PrintStream(new ByteArrayOutputStream()))).isTrue();
  }
}
