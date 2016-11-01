package com.uchicom.http;

import com.uchicom.server.Handler;
import com.uchicom.server.HandlerFactory;
import com.uchicom.server.Parameter;

public class HttpHandlerFactory extends HandlerFactory {

	public HttpHandlerFactory(Parameter parameter) {
		super(parameter);
	}
	@Override
	public Handler createHandler() {
		return new HttpHandler();
	}

}
