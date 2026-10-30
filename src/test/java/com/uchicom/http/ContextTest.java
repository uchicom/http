/**
 * (c) 2016 uchicom
 */
package com.uchicom.http;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ContextTest {

	@Test
	public void singleton() {
		assertThat(Context.singleton()).isSameAs(Context.singleton());
	}

	@Test
	public void getRouter() {
		assertThat(Context.singleton().getRouter()).isInstanceOf(DefaultRouter.class);
	}
}
