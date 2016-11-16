/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.SocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Uchiyama Shigeki
 *
 */
public class DefaultRouter implements Router {

	protected static Map<String, WebFile> map = new ConcurrentHashMap<String, WebFile>();
	protected static Map<String, File> errorMap = new ConcurrentHashMap<String, File>();

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
		watch(baseFile);
	}

	@Override
	public boolean isModified(String filePath, TemporalAccessor ifModified) {
		WebFile file = map.get(filePath);
		return file.getLastModified().equals(ifModified);
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
	public void request(String fileName,
			SocketAddress address,
			Map<String, String[]> paramMap,
			OutputStream outputStream) throws IOException {

		try (PrintStream ps = new PrintStream(outputStream);) {
			WebFile file = map.get(fileName);
			long len = file.length();
			ps.print("HTTP/1.1 200 OK \r\n");
			ps.print("Date: ");
			ps.print(Constants.formatter.format(OffsetDateTime.now()));
			ps.print("\r\n");
			ps.print("Server: uchicom-http\r\n");
			ps.print("Accept-Ranges: bytes\r\n");
			ps.print("Connection: close\r\n");
			if (len != 0) {
				ps.print("Content-Length: ");
				ps.print(String.valueOf(len));
				ps.print("\r\n");
			}
			ps.print("Last-Modified: ");
			ps.print(Constants.formatter.format(file.getLastModified()));
			ps.print("\r\n");
			ps.print("Content-Type: ");
			ps.print(file.getContentType());
			ps.print("\r\n");
			ps.print("Expires: ");
			ps.print(Constants.formatter.format(file.getLastModified().plusDays(1)));
			ps.print("\r\n\r\n");
			byte[] bytes = null;
			if ("text/html".equals(file.getContentType())) {
				bytes = new byte[2 * 1024];
			} else {
				bytes = new byte[64 * 1024];
			}

			try (FileInputStream fis = new FileInputStream(file.getFile());) {
				int length = 0;
				while ((length = fis.read(bytes)) >= 0) {
					ps.write(bytes, 0, length);
				}
			}
		}
	}

	/**
	 * 表示してよい存在するファイルを登録する。
	 *
	 * @param file
	 */
	public static void createMap(File file) {
		if (file.isFile()) {
			String relativePath = file.toURI().getPath().substring(htmlFileLength - 1);
			WebFile webFile = new WebFile(file);
			map.put(relativePath, webFile);
		} else if (file.isDirectory()) {
			File[] childs = file.listFiles();
			for (File childFile : childs) {
				createMap(childFile);
			}
		}
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

		try (PrintStream ps = new PrintStream(outputStream);) {
			ps.print("HTTP/1.1 ");
			ps.print(code);
			ps.print(" ERR\r\n");
			if (errorMap.containsKey(code)) {
				File error = errorMap.get(code);
				ps.print("Date: ");
				ps.print(Constants.formatter.format(OffsetDateTime.now()));
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
				try (FileInputStream fis = new FileInputStream(error);) {
					byte[] bytes = new byte[1024];
					int length = 0;
					while ((length = fis.read(bytes)) >= 0) {
						ps.write(bytes, 0, length);
					}
				}
			}
			ps.flush();
		}

	}

	/**
	 *
	 * @param baseFile
	 */
	private void watch(File baseFile) {
		Thread thread = new Thread(()-> {
			WatchKey key = null;
			try {
				Path path = new File(baseFile, "html").toPath();
				FileSystem fileSystem = path.getFileSystem();
				WatchService service = fileSystem.newWatchService();
				key = path.register(service, new Kind[] { StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE }, new Modifier[] {com.sun.nio.file.ExtendedWatchEventModifier.FILE_TREE});
				while (key.isValid()) {

					// スレッドの割り込み = 終了要求を判定する.
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException();
					}

					WatchKey detecedtWatchKey = service.poll(1000, TimeUnit.MILLISECONDS);
					if (detecedtWatchKey == null) {
						// タイムアウト
						continue;
					}
					if (detecedtWatchKey.equals(key)) {
						for (WatchEvent<?> event : detecedtWatchKey.pollEvents()) {
							Path file = (Path) event.context();
							System.out.println(event.kind() +
									": count=" + event.count() +
									": path=" + file);

							File htmlFile = new File(baseFile, "html");
							String value = file.toString().replaceAll("\\\\", "/");
							String relativePath = "/" + value;
							if (StandardWatchEventKinds.ENTRY_CREATE.equals(event.kind())) {
								if (!map.containsKey(relativePath)) {
									map.put(relativePath, new WebFile(new File(htmlFile, file.toString())));
								}
							} else if (StandardWatchEventKinds.ENTRY_DELETE.equals(event.kind())) {
								if (map.containsKey(relativePath)) {
									map.remove(relativePath);
								}
							} else if (StandardWatchEventKinds.ENTRY_MODIFY.equals(event.kind())) {
								if (map.containsKey(relativePath)) {
									map.put(relativePath, new WebFile(new File(htmlFile, file.toString())));
								}
							}
						}

					}
					detecedtWatchKey.reset();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
				key.cancel();
			}
		});
		thread.setDaemon(false);
		thread.start();
	}
}
