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
  public void writeNoCacheReflectsUpdate() throws Exception {
    File file = new File(dir, "a.txt");
    Files.writeString(file.toPath(), "old");
    WebFile webFile = new WebFile(file, 0);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    webFile.write(new PrintStream(out));
    assertThat(out.toString()).isEqualTo("old");

    // 更新時刻が変わらなくても反映される
    long modified = file.lastModified();
    Files.writeString(file.toPath(), "new!");
    file.setLastModified(modified);
    out.reset();
    webFile.write(new PrintStream(out));

    assertThat(out.toString()).isEqualTo("new!");
  }

  @Test
  public void writeCacheReloadsWhenLastModifiedChanges() throws Exception {
    File file = new File(dir, "a.txt");
    Files.writeString(file.toPath(), "old");
    file.setLastModified(1_000_000_000_000L);
    WebFile webFile = new WebFile(file, 1024);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    webFile.write(new PrintStream(out));

    // 更新時刻が同じなら、キャッシュした内容を返す
    Files.writeString(file.toPath(), "new");
    file.setLastModified(1_000_000_000_000L);
    out.reset();
    webFile.write(new PrintStream(out));
    assertThat(out.toString()).isEqualTo("old");

    // 更新時刻が変われば読み直す
    file.setLastModified(1_100_000_000_000L);
    out.reset();
    webFile.write(new PrintStream(out));
    assertThat(out.toString()).isEqualTo("new");
  }

  @Test
  public void writeSkipsCacheWhenOverLimit() throws Exception {
    WebFile webFile = new WebFile(new File(dir, "a.txt"), 2);
    Files.writeString(webFile.getFile().toPath(), "abc");

    assertThat(webFile.isCacheable()).isFalse();
  }

  @Test
  public void basic() throws Exception {
    WebFile webFile = create("a.txt", "x");
    assertThat(webFile.isBasic()).isFalse();

    webFile.setBasic(new Properties());

    assertThat(webFile.isBasic()).isTrue();
  }
}
