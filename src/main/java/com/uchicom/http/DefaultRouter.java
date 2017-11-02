/**
 * (c) 2012 uchicom
 */
package com.uchicom.http;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchEvent.Modifier;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Uchiyama Shigeki
 *
 */
public class DefaultRouter implements Router {

	protected static Map<String, WebFile> fileMap = new ConcurrentHashMap<>();
	protected static Map<String, Properties> basicMap = new ConcurrentHashMap<>();
	protected static Map<String, Properties> forwardMap = new ConcurrentHashMap<>();
	protected static Map<String, File> errorMap = new ConcurrentHashMap<>();

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
		WebFile file = fileMap.get(filePath);
		return file.getLastModified().equals(ifModified);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.uchicom.server.http.Router#exists(java.lang.String)
	 */
	@Override
	public boolean exists(String filePath) {
		return fileMap.containsKey(filePath);
	}

	/*
	 * (non-Javadoc)
	 *
	 * @see com.uchicom.server.http.Router#request(java.lang.String,
	 * java.util.Map, java.io.OutputStream)
	 */
	@Override
	public void request(String fileName,
			SocketAddress address,
			Map<String, String[]> paramMap,
			Map<String, String> headMap,
			OutputStream outputStream) throws IOException {

		try (PrintStream ps = new PrintStream(outputStream);) {
			WebFile file = fileMap.get(fileName);
			if (basicMap.containsKey(fileName.substring(0, fileName.lastIndexOf('/') + 1))) {
				boolean auth = false;
				// ベーシック認証チェック用
				if (headMap.containsKey("Authorization")) {
					// ベーシック認証処理
					String authorization = headMap.get("Authorization");
					int typeIndex = authorization.indexOf(' ');
					String type = authorization.substring(0, typeIndex);
					String encoded = authorization.substring(typeIndex + 1);

					if ("Basic".equals(type)) {
						try {
							String decoded = new String(Base64.getDecoder().decode(encoded));
							int index = decoded.indexOf(":");
							// 認証結果
							Properties prop = basicMap.get(fileName.substring(0, fileName.lastIndexOf('/') + 1));
							if (prop.containsKey(decoded.substring(0, index))) {
								auth = decoded.substring(index + 1)
										.equals(prop.getProperty(decoded.substring(0, index)));
							}
						} catch (Exception e) {
							error("400", outputStream);
						}
					}
				}
				if (!auth) {
					// 認証エラー
					ps.print("HTTP/1.1 401 Authorization Required\r\n");
					ps.print("Date: ");
					ps.print(Constants.formatter.format(OffsetDateTime.now()));
					ps.print("\r\n");
					ps.print("Server: http\r\n");
					ps.print("WWW-Authenticate: Basic realm=\"SECRET AREA\"\r\n");
					ps.print("Connection: close\r\n");
					ps.print("Transfer-Encoding: chunked\r\n");
					ps.print("Content-Type: text/html; charset=iso-8859-1\r\n");
					ps.flush();
					return;
				}
			}
			// 通常ファイルの場合
			long len = file.length();
			boolean isRange = headMap.containsKey("Range");
			boolean isError = false;
			List<String[]> rengeList = null;
			if (isRange) {
				rengeList = new ArrayList<>();
				String range = headMap.get("Range");
				String index = range.substring(range.indexOf("bytes=") + 6);
				String[] splits = index.split(",");
				for (String split : splits) {
					String[] startEnd = split.split("-");
					if ("".equals(startEnd[0])) {
						//後ろから
						if (len < Long.parseLong(startEnd[1])) {
							//指定範囲エラー
							ps.print("HTTP/1.1 416 Range Not Satisfiable");
							isError = true;
						} else {
							rengeList.add(startEnd);
							ps.print("HTTP/1.1 206 Partial Content");
						}
					} else {
						//前から
						long start = Long.parseLong(startEnd[0]);
						long end = Long.parseLong(startEnd[1]);
						if (len <= start ||
								len <= end ||
								start > end) {
							//指定範囲エラー
							ps.print("HTTP/1.1 416 Range Not Satisfiable");
							isError = true;
						} else {
							rengeList.add(startEnd);
							ps.print("HTTP/1.1 206 Partial Content");
						}
					}
				}
			} else {
				ps.print("HTTP/1.1 200 OK \r\n");
			}
			ps.print("Date: ");
			ps.print(Constants.formatter.format(OffsetDateTime.now()));
			ps.print("\r\n");
			ps.print("Server: http\r\n");
			ps.print("Accept-Ranges: bytes\r\n");
			ps.print("Connection: close\r\n");
			if (isError == false && len != 0) {
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
			if (!isError) {
				if (isRange) {
					try (RandomAccessFile raf = new RandomAccessFile(file.getFile(), "r");) {
						for (String[] startEnd : rengeList) {
							long end = Long.parseLong(startEnd[1]);
							if ("".equals(startEnd[0])) {
								//TODO 未実装
							} else {
								long start = Long.parseLong(startEnd[0]);
								raf.seek(start);
								long getLength = end - start + 1;
								int length = 0;
								int totalLength = 0;
								while (totalLength < getLength) {
									int byteLength = bytes.length;
									if (getLength - totalLength < byteLength) {
										byteLength = (int)(getLength - totalLength);
									}
									length = raf.read(bytes, 0, byteLength);
									totalLength += length;
									ps.write(bytes, 0, length);
								}
							}
						}
					}
				} else {
					try (FileInputStream fis = new FileInputStream(file.getFile());) {
						int length = 0;
						while ((length = fis.read(bytes)) > 0) {
							ps.write(bytes, 0, length);
						}
					}
				ps.flush();
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
			if (file.getName().startsWith(".")) {
				// 隠しファイルは表示しない
				if (".basic".equals(file.getName())) {
					String relativePath = file.getParentFile().toURI().getPath().substring(htmlFileLength - 1);
					// Basicファイル認証がある場合
					try (FileInputStream fis = new FileInputStream(file)) {
						Properties properties = new Properties();
						properties.load(fis);
						basicMap.put(relativePath, properties);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				} else if (".forward".equals(file.getName())) {
					String relativePath = file.getParentFile().toURI().getPath().substring(htmlFileLength - 1);
					try (FileInputStream fis = new FileInputStream(file)) {
						Properties properties = new Properties();
						properties.load(fis);
						forwardMap.put(relativePath, properties);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			} else {
				String relativePath = file.toURI().getPath().substring(htmlFileLength - 1);
				WebFile webFile = new WebFile(file);
				fileMap.put(relativePath, webFile);
			}
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

	/*
	 * (non-Javadoc)
	 *
	 * @see com.uchicom.server.http.Router#error(java.lang.String,
	 * java.io.OutputStream)
	 */
	@Override
	public void error(String code, OutputStream outputStream) throws IOException {

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

	Map<WatchKey, Path> pathMap = new HashMap<>();

	/**
	 *
	 * @param baseFile
	 */
	private void watch(File baseFile) {
		Thread thread = new Thread(() -> {
			WatchKey key = null;
			try {
				WatchService service = FileSystems.getDefault().newWatchService();
				File htmlFile = new File(baseFile, "html");
				regist(service, htmlFile);
				while ((key = service.take()) != null) {

					// スレッドの割り込み = 終了要求を判定する. 必要なのか不明
					if (Thread.currentThread().isInterrupted()) {
						throw new InterruptedException();
					}
					if (!key.isValid())
						continue;
					for (WatchEvent<?> event : key.pollEvents()) {
						//eventではファイル名しかとれない
						Path file = (Path) event.context();
						//監視対象のフォルダを取得する必要がある
						Path real = pathMap.get(key).resolve(file);
						String cpath = real.toFile().getCanonicalPath();
						String bpath = htmlFile.getCanonicalPath();
						String absPath = cpath.substring(bpath.length()).replace('\\', '/');
						if (StandardWatchEventKinds.ENTRY_CREATE.equals(event.kind())) {
							regist(service, real.toFile());
							if (!fileMap.containsKey(absPath)) {
								fileMap.put(absPath, new WebFile(real.toFile()));
							}
						} else if (StandardWatchEventKinds.ENTRY_DELETE.equals(event.kind())) {
							// 削除時はcancel不要の認識
							if (fileMap.containsKey(absPath)) {
								fileMap.remove(absPath);
							}
						} else if (StandardWatchEventKinds.ENTRY_MODIFY.equals(event.kind())) {
							if (fileMap.containsKey(absPath)) {
								fileMap.put(absPath, new WebFile(real.toFile()));
							}
						}
					}
					key.reset();
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
				key.cancel();
			}
		});
		thread.setDaemon(false); //mainスレッドと運命を共に
		thread.start();
	}

	/**
	 * 監視サービスにフォルダを再起呼び出しして登録する
	 * 
	 * @param service
	 * @param file
	 * @throws IOException
	 */
	public void regist(WatchService service, File file) throws IOException {
		if (file.isDirectory()) {
			Path path = file.toPath();
			pathMap.put(
					path.register(
							service,
							new Kind[] { StandardWatchEventKinds.ENTRY_CREATE,
									StandardWatchEventKinds.ENTRY_MODIFY, StandardWatchEventKinds.ENTRY_DELETE },
							new Modifier[] {}),
					path);
			for (File child : file.listFiles()) {
				regist(service, child);
			}
		}
	}

	/*
	 * (非 Javadoc)
	 *
	 * @see com.uchicom.http.Router#forward(java.io.BufferedReader,
	 * java.io.OutputStream)
	 */
	@Override
	public void forward(String filePath, String[] heads, BufferedReader reader, OutputStream outputStream)
			throws IOException {
		Properties prop = forwardMap.get(filePath.substring(0, filePath.indexOf('/', 1) + 1));
		Socket socket = new Socket(prop.getProperty("host"), Integer.parseInt(prop.getProperty("port")));
		OutputStream os = socket.getOutputStream();
		os.write(heads[0].getBytes());
		os.write(" ".getBytes());
		os.write(heads[1].getBytes());
		os.write(" ".getBytes());
		os.write(heads[2].getBytes());
		os.write("\r\n".getBytes());
		String line = reader.readLine();
		int contentLength = 0;
		while (line != null && !"".equals(line)) {
			if (line.startsWith("Content-Length: ")) {
				contentLength = Integer.parseInt(line.substring(16));
			}
			os.write(line.getBytes());
			os.write("\r\n".getBytes());
			line = reader.readLine();
		}
		os.write("\r\n".getBytes());
		if (contentLength > 0) {
			char[] chars = new char[contentLength];
			reader.read(chars);
			os.write(new String(chars).getBytes());
		}
		os.flush();
		InputStream is = socket.getInputStream();
		byte[] bytes = new byte[4 * 1024];
		int length = 0;
		while ((length = is.read(bytes)) > 0) {
			outputStream.write(bytes, 0, length);
			outputStream.flush();
		}
		System.out.println("sock.close");
		socket.close();

	}

	/*
	 * (非 Javadoc)
	 *
	 * @see com.uchicom.http.Router#isForward(java.lang.String)
	 */
	@Override
	public boolean isForward(String filePath) {
		return forwardMap.containsKey(filePath.substring(0, filePath.indexOf('/', 1) + 1));
	}
}
