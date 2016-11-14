/**
 * (c) 2013 uchicom
 */
package com.uchicom.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.time.OffsetDateTime;

import com.uchicom.server.Handler;


/**
 * @author uchicom: Shigeki Uchiyama
 *
 */
public class HttpHandler implements Handler {

    ByteBuffer readBuff = ByteBuffer.allocate(1014);
    ByteBuffer writeBuff = ByteBuffer.allocate(256);
    File file = null;
    StringBuffer strBuff = new StringBuffer();

    /* (non-Javadoc)
     * @see com.uchicom.http.Handler#handle(java.nio.channels.SelectionKey)
     */
    @Override
    public void handle(SelectionKey key) throws IOException {
    	SocketChannel channel = (SocketChannel) key.channel();
        if (key.isReadable()) {
            int length = channel.read(readBuff);
            if (length > 0) {
                readBuff.flip();
                file = request(new String(readBuff.array()));
            }
            readBuff.clear();
            key.interestOps(SelectionKey.OP_WRITE);
        }

        if (key.isWritable()) {

            ByteBuffer buff = null;
//            if (map.containsKey(file)) {
//                buff = map.get(file);
//            } else {
                buff = response(channel, file).asReadOnlyBuffer();
//                map.put(file, buff);
//            }

            try {
                buff.position(0);
                channel.write(buff);
            } catch (IOException e) {
                System.err.println(channel.socket().getInetAddress());
                e.printStackTrace();
            }
            strBuff.setLength(0);
            readBuff.clear();
            key.cancel();
        }
    }

    public File request(String request) {
        String[] lines = request.split("\r\n");
        String[] heads = lines[0].split(" ");
        File file = null;
        if ("/".equals(heads[1])) {
            file = new File("html", "index.htm");
        } else {
            file = new File("html", heads[1].substring(1));
        }
        return file;
    }

    public ByteBuffer response(SocketChannel channel, File file) {

        if (file == null || !file.exists() || !file.isFile()) {
            strBuff.append("HTTP/1.1 404 ERR \r\n");
            strBuff.append("Date: ");
            strBuff.append(Constants.formatter.format(OffsetDateTime.now()));
            strBuff.append("\r\n");
            strBuff.append("Server: non\r\n");
            strBuff.append("Accept-Ranges: bytes\r\n");
            strBuff.append("Connection: close\r\n");
            strBuff.append("Content-Length: 69\r\n");
            strBuff.append("Content-Type: text/html\r\n\r\n");
            strBuff.append("<html><head>404ERROR</head><body><h1>404 Not Found</h1></body></html>");
        } else {
            strBuff.append("HTTP/1.1 200 OK \r\n");
            strBuff.append("Date: ");
            strBuff.append(Constants.formatter.format(OffsetDateTime.now()));
            strBuff.append("\r\n");
            strBuff.append("Server: non\r\n");
            strBuff.append("Accept-Ranges: bytes\r\n");
            strBuff.append("Connection: close\r\n");
            if (file.length() != 0) {
                strBuff.append("Content-Length: ");
                strBuff.append(String.valueOf(file.length()));
                strBuff.append("\r\n");
            }
            strBuff.append("Content-Type: text/html\r\n\r\n");
            try (FileInputStream fis = new FileInputStream(file);) {
                byte[] bytes = new byte[1024];
                while (fis.read(bytes) > 0) {
                    strBuff.append(new String(bytes));
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return ByteBuffer.wrap(strBuff.toString().getBytes());
    }

}
