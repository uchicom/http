/**
 * (c) 2017 uchicom
 */
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

public class IOThrougherTest {

	@Test
	public void run() {
		byte[] data = "hello through".getBytes();
		ByteArrayOutputStream os = new ByteArrayOutputStream();

		new IOThrougher(new ByteArrayInputStream(data), os, "key").run();

		assertThat(os.toByteArray()).isEqualTo(data);
	}
}
