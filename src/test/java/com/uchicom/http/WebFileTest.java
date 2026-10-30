// (C) 2012 uchicom
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WebFileTest {

  @TempDir File dir;

  private WebFile create(String name, String content) throws Exception {
    File file = new File(dir, name);
    Files.writeString(file.toPath(), content);
    return new WebFile(file);
  }

  @Test
  public void mimeProperties() {
    assertThat(WebFile.mimeProperties.getProperty("html")).isEqualTo("text/html");
  }

  @Test
  public void contentTypeKnownExtension() throws Exception {
    assertThat(create("a.html", "x").getContentType()).isEqualTo("text/html");
  }

  @Test
  public void contentTypeUnknownExtension() throws Exception {
    assertThat(create("a.unknownext", "x").getContentType()).isEqualTo("text/plain");
  }

  @Test
  public void contentTypeNoExtension() throws Exception {
    assertThat(create("noext", "x").getContentType()).isEqualTo("text/plain");
  }

  @Test
  public void lengthAndLastModified() throws Exception {
    WebFile webFile = create("a.txt", "12345");

    assertThat(webFile.length()).isEqualTo(5);
    assertThat(webFile.getLastModified().toInstant().toEpochMilli())
        .isEqualTo(webFile.getFile().lastModified());
  }

  @Test
  public void write() throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();

    create("a.txt", "content").write(new PrintStream(out));

    assertThat(out.toString()).isEqualTo("content");
  }

  @Test
  public void basic() throws Exception {
    WebFile webFile = create("a.txt", "x");
    assertThat(webFile.isBasic()).isFalse();

    webFile.setBasic(new Properties());

    assertThat(webFile.isBasic()).isTrue();
  }
}
