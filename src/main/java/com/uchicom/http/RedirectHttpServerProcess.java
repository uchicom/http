package com.uchicom.http;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;

import com.uchicom.server.Parameter;
import com.uchicom.server.ServerProcess;

public class RedirectHttpServerProcess implements ServerProcess {

	private static byte[] responseHader = "HTTP/1.1 302 Found\r\nLocation: ".getBytes();
    private static byte[] responseFooder = "\r\n\r\n".getBytes();
	private Parameter parameter;
	private Socket socket;
	Router router = new DefaultRouter();
	public RedirectHttpServerProcess(Parameter parameter, Socket socket) {
		this.parameter = parameter;
		this.socket = socket;
		router.init(parameter.getFile("base"));
	}

	@Override
	public long getLastTime() {
		// TODO 自動生成されたメソッド・スタブ
		return 0;
	}

	@Override
	public void forceClose() {
		// TODO 自動生成されたメソッド・スタブ

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
                    os.write(responseHader);
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
