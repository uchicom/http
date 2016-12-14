package com.uchicom.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

public class RedirectHttpServerProcess implements ServerProcess {

//	private static byte[] response302Hader = "HTTP/1.1 302 Found\r\nLocation: ".getBytes();
	private static byte[] response301Hader = "HTTP/1.1 301 Moved Permanently\r\nLocation: ".getBytes();
    private static byte[] responseFooder = "\r\n\r\n".getBytes();
	private Parameter parameter;
	private Socket socket;
	Router router;
	public RedirectHttpServerProcess(Parameter parameter, Socket socket) {
		this.parameter = parameter;
		this.socket = socket;
		router = Context.singleton().getRouter();
	}

	@Override
	public long getLastTime() {
		// TODO 自動生成されたメソッド・スタブ
		return System.currentTimeMillis();
	}

	@Override
	public void forceClose() {
		try {
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	@Override
	public void execute() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            OutputStream os = socket.getOutputStream();) {
            //リクエストの分解、保持
            String head = br.readLine();
            if (head != null) {
                String[] heads = head.split(" ");
                if (heads.length == 3) {
                    //初期応答行を返却する
                    os.write(response301Hader);
                    os.write(parameter.get("redirect").getBytes());
                    os.write(heads[1].getBytes());
                    os.write(responseFooder);

                } else {
                    //Bad Request
                    router.error("400", os);
                }

            } else {
                //Bad Request
                router.error("400", os);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Throwable e) {
            e.printStackTrace();
        }

	}

}
