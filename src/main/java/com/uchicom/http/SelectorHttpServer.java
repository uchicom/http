/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import com.uchicom.server.SelectorServer;
import com.uchicom.server.Parameter;


/**
 * @author Uchiyama Shigeki
 *
 */
public class SelectorHttpServer extends SelectorServer {

	public SelectorHttpServer(Parameter parameter) {
		super(parameter, new HttpHandlerFactory(parameter));
	}

}
