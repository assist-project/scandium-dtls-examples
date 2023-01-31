package org.eclipse.californium.scandium.examples;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * We use this class to avoid having to restart the vm (which is can be a slow
 * process).
 */
public class ThreadStarter {
	private static final Logger LOG = LoggerFactory.getLogger(ThreadStarter.class);

	public static final String CMD_EXIT = "exit";
	public static final String CMD_START = "start";
	public static final String CMD_STOP = "stop";
	public static final String RESP_STOPPED = "stopped";
	public static final String RESP_STARTED = "started";

	private Supplier<ExampleDTLSClient> supplier;
	private ServerSocket srvSocket;
	private ExampleDTLSClient dtlsClient;
	private Thread dtlsClientThread;
	private Socket cmdSocket;
	private Integer port;

	public ThreadStarter(Supplier<ExampleDTLSClient> supplier, String ipPort, Integer runWait)
			throws IOException {
		String[] addr = ipPort.split("\\:");
		port = Integer.valueOf(addr[1]);
		InetSocketAddress address = new InetSocketAddress(addr[0], port);
		this.supplier = supplier;
		srvSocket = new ServerSocket();
		srvSocket.setReuseAddress(true);
		srvSocket.setSoTimeout(20000);
		srvSocket.bind(address);

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ThreadStarter.this.closeAll();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}));
	}

	public void run() throws IOException {
		LOG.info("Listening at {}:{}", srvSocket.getInetAddress(), srvSocket.getLocalPort());
		cmdSocket = srvSocket.accept();
		BufferedReader in = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
		PrintWriter out = new PrintWriter(new OutputStreamWriter(cmdSocket.getOutputStream()), true);
		while (true) {
			try {
				String cmd = in.readLine();
				LOG.info("Received: {}", cmd);
				if (cmd != null) {
					switch (cmd.trim()) {
					case CMD_START:
						if (dtlsClient == null || !dtlsClient.isRunning()) {
							// spawn a new dtls client thread
							dtlsClient = supplier.get();
							dtlsClient.startClient();
						}
						out.println(String.format("%s %d", RESP_STARTED, port));
						LOG.info("Responding with: {} {}", RESP_STARTED, port);
						break;

					case CMD_STOP:
						dtlsClient.stopClient();
						out.println(String.format("%s", RESP_STOPPED));
						break;

					// command for exiting
					case CMD_EXIT:
						closeAll();
						return;
					}
				} else {
					LOG.info("Received Nothing");
					closeData();
					break;
				}
			} catch (Exception e) {
				String errorFileName = "ts.error." + port + ".log";
				PrintWriter errorPw = new PrintWriter(new FileWriter(errorFileName));
				e.printStackTrace(errorPw);
				e.printStackTrace();
				errorPw.close();
				closeAll();
				return;
			}
		}
	}

	private void closeAll() throws IOException {
		LOG.warn("Shutting down thread starter");
		closeData();
		srvSocket.close();
	}

	private void closeData() throws IOException {
		if (dtlsClientThread != null) {
			dtlsClientThread.interrupt();
		}
		if (cmdSocket != null) {
			cmdSocket.close();
		}
	}
}
