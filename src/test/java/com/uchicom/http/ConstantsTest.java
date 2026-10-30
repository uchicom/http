/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

public class ConstantsTest {

	@Test
	public void defaults() {
		assertThat(Constants.DEFAULT_DIR).isEqualTo("www");
		assertThat(Constants.DEFAULT_PORT).isEqualTo("80");
		assertThat(Constants.DEFAULT_BACK).isEqualTo("10");
		assertThat(Constants.DEFAULT_POOL).isEqualTo("10");
	}

	@Test
	public void formatter() {
		OffsetDateTime time = OffsetDateTime.of(2020, 1, 2, 3, 4, 5, 0, ZoneOffset.UTC);
		assertThat(Constants.formatter.format(time)).isEqualTo("Thu, 2 Jan 2020 03:04:05 GMT");
	}
}
