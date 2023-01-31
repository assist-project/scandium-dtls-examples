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
 * 
 */
public class ThreadStarter {
	private static final Logger LOG = LoggerFactory.getLogger(ThreadStarter.class);

	public static final String CMD_EXIT = "exit";
	public static final String CMD_START = "start";
	public static final String CMD_STOP = "stop";
	public static final String RESP_STOPPED = "stopped";
	public static final String RESP_STARTED = "started";

	private ServerSocket srvSocket;
	private Supplier<ExampleDTLSServer> serverBuilder;
	private ExampleDTLSServer dtlsServer;
	private Socket cmdSocket;
	private Integer port;

	public ThreadStarter(Supplier<ExampleDTLSServer> dtlsServerSupplier, String ipPort)
			throws IOException {
		String[] addr = ipPort.split("\\:");
		port = Integer.valueOf(addr[1]);
		InetSocketAddress address = new InetSocketAddress(addr[0], port);
		serverBuilder = dtlsServerSupplier;
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
					// command for killing the current server thread and spawning a new one
					case CMD_START:
						if (dtlsServer == null) {
							dtlsServer = serverBuilder.get();
						}
						dtlsServer.startServer();
						out.println(String.format("%s %d", RESP_STARTED, dtlsServer.getAddress().getPort()));
						break;
					case CMD_STOP:
						if (dtlsServer != null) {
							dtlsServer.stopServer();
						}
						out.println(RESP_STOPPED);
						break;
					// command for exiting
					case CMD_EXIT:
						closeAll();
						return;
					}
				} else {
					LOG.warn("Received Nothing");
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
		if (dtlsServer != null) {
			dtlsServer.stopServer();
		}
		if (cmdSocket != null) {
			cmdSocket.close();
		}
	}
}