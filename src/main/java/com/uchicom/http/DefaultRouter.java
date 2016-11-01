/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.SocketAddress;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Uchiyama Shigeki
 *
 */
public class DefaultRouter implements Router {

    protected static Map<String, WebFile> map = new HashMap<String, WebFile>();
    protected static Map<String, File> errorMap = new HashMap<String, File>();

    protected static int htmlFileLength;

    /*
     * (non-Javadoc)
     *
     * @see com.uchicom.server.http.Router#init(java.io.File)
     */
    @Override
    public void init(File baseFile) {
        File htmlFile = new File(baseFile, "html");
        htmlFileLength = htmlFile.toURI().getPath().length();
        createMap(htmlFile);
        createErrorMap(new File(baseFile, "error"));
    }
	@Override
	public boolean isModified(String filePath, Date ifModified) {
		WebFile file = map.get(filePath);
		return file.getFile().lastModified() / 1000 == ifModified.getTime() / 1000;
	}
    /* (non-Javadoc)
     * @see com.uchicom.server.http.Router#exists(java.lang.String)
     */
    @Override
    public boolean exists(String filePath) {
        return map.containsKey(filePath);
    }
    /*
     * (non-Javadoc)
     *
     * @see com.uchicom.server.http.Router#request(java.lang.String, java.util.Map,
     * java.io.OutputStream)
     */
    @Override
    public void request(String fileName, SocketAddress address, Map<String, String[]> paramMap,
            OutputStream outputStream) throws IOException {
        WebFile file = map.get(fileName);
        long len = file.length();
        if (true) {
            outputStream.write("HTTP/1.1 200 OK \r\n".getBytes());
            outputStream.write("Date: ".getBytes());
            outputStream.write(Constants.format.format(new Date()).getBytes());
            outputStream.write("\r\n".getBytes());
            outputStream.write("Server: uchicom-http\r\n".getBytes());
            outputStream.write("Accept-Ranges: bytes\r\n".getBytes());
            outputStream.write("Connection: close\r\n".getBytes());
            if (len != 0) {
                outputStream.write("Content-Length: ".getBytes());
                outputStream.write(String.valueOf(len).getBytes());
                outputStream.write("\r\n".getBytes());
            }
            outputStream.write("Last-Modified: ".getBytes());
            outputStream.write(Constants.format.format(new Date(file.getFile().lastModified())).getBytes());
            outputStream.write("\r\n".getBytes());
            outputStream.write("Content-Type: ".getBytes());
            outputStream.write(file.getContentType().getBytes());
            outputStream.write("\r\n\r\n".getBytes());
//        } else if (false) {
//            outputStream.write("HTTP/1.1 302 Found".getBytes());
        }
        byte[] bytes = null;
        if ("text/html".equals(file.getContentType())) {
            bytes = new byte[2 * 1024];
        } else {
            bytes = new byte[64 * 1024];
        }
        FileInputStream stream = new FileInputStream(file.getFile());
        int length = stream.read(bytes);
        while (length > 0) {
            outputStream.write(bytes, 0, length);
            outputStream.flush();
            length = stream.read(bytes);
        }
        stream.close();
    }


    /**
     * 表示してよい存在するファイルを登録する。
     * @param file
     */
    public static void createMap(File file) {
        if (file.isFile()) {
            String relativePath = file.toURI().getPath().substring(htmlFileLength-1);
            System.out.print(relativePath);
            System.out.print(" ");
            WebFile webFile = initialize(file);
            map.put(relativePath, webFile);
        } else if (file.isDirectory()) {
            File[] childs = file.listFiles();
            for (File childFile : childs) {
                createMap(childFile);
            }
        }
    }


    public static WebFile initialize(File file) {
        WebFile webFile = new WebFile(file, "jtag");
        return webFile;
    }

    public static void createErrorMap(File file) {
        if (file.exists()) {
            if (file.isDirectory()) {
                File[] childs = file.listFiles();
                for (File childFile : childs) {
                    errorMap.put(childFile.getName().substring(0, 3), childFile);
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see com.uchicom.server.http.Router#error(java.lang.String, java.io.OutputStream)
     */
    @Override
    public void error(String code, OutputStream outputStream)
            throws IOException {

        PrintStream ps = new PrintStream(outputStream);

        ps.print("HTTP/1.1 ");
        ps.print(code);
        ps.print(" ERR\r\n");
        if (errorMap.containsKey(code)) {
            File error = errorMap.get(code);
            ps.print("Date: ");
            ps.print(Constants.format.format(new Date()));
            ps.print("\r\n");
            ps.print("Server: SigleHttpServer\r\n");
            ps.print("Accept-Ranges: bytes\r\n");
            ps.print("Connection: close\r\n");
            long len = 0;
            len = error.length();
            if (len != 0) {
                ps.print("Content-Length: ");
                ps.print(String.valueOf(len));
                ps.print("\r\n");
            }
            ps.print("Content-Type: text/html");
            ps.print("\r\n\r\n");
            DataInputStream stream = new DataInputStream(new FileInputStream(error));
            int ch = stream.read();
            while (ch >= 0) {
                outputStream.write(ch);
                outputStream.flush();
                ch = stream.read();
            }
            stream.close();
        }
        outputStream.flush();
        outputStream.close();

    }

}
