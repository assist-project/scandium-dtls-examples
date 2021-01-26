package org.eclipse.californium.scandium.examples;

import java.io.BufferedReader;
import java.io.BufferedWriter;
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
 * We use this class to avoid having to restart the vm (which is can be a slow process). 
 */
public class ThreadStarter {
	private static final Logger LOG = LoggerFactory.getLogger(ThreadStarter.class);
	
	private Supplier<ExampleDTLSClient> supplier;
	private ServerSocket srvSocket;
	private ExampleDTLSClient dtlsClientRunnable;
	private Thread dtlsClientThread;
	private Socket cmdSocket;
	private Integer port;
	private boolean continuous;
	private Integer startTimeout;
	
	public ThreadStarter(Supplier<ExampleDTLSClient> supplier, String ipPort, boolean continuous, Integer runWait) throws IOException {
		String[] addr = ipPort.split("\\:");
		port = Integer.valueOf(addr[1]);
		InetSocketAddress address = new InetSocketAddress(addr[0], port);		
		this.supplier = supplier;
		srvSocket = new ServerSocket();
		srvSocket.setReuseAddress(true);
		srvSocket.bind(address);
		this.continuous = continuous;
		this.startTimeout = runWait;
		
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
		LOG.error("Listening at {}:{}", srvSocket.getInetAddress(), srvSocket.getLocalPort());
		do {
			cmdSocket = srvSocket.accept();
			BufferedReader in = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(cmdSocket.getOutputStream()));
			dtlsClientThread = null;
			while (true) {
				try {
					String cmd = in.readLine();
					LOG.info("Received: {}", cmd);
					if (cmd != null) {
						switch(cmd.trim()) {
							// command for killing the current server thread and spawning a new one
						case "reset":
							// empty space acts as reset, used for debugging purposes
						case "":
							// we interrupt any existing client thread
							if (dtlsClientThread != null) {
								dtlsClientThread.interrupt();
								while (dtlsClientThread.isAlive()) {
									Thread.sleep(10);
								}
							}
							
							out.write("ack");
							out.newLine();
							out.flush();
							LOG.info("Waiting {} ms before starting the client", startTimeout);
							
							Thread.sleep(startTimeout);
							
							// spawn a new dtls client thread
							dtlsClientRunnable = supplier.get();
							dtlsClientThread = new Thread(dtlsClientRunnable);
							dtlsClientThread.start();
							
							break;
							
							// command for exiting
						case "exit":
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
		} while(continuous);
	}
	
	private void closeAll() throws IOException {
		LOG.warn("Shutting down thread starter");
		closeData();
		srvSocket.close();
	}
	
	private void closeData() throws IOException{
		if (dtlsClientThread != null) {
			dtlsClientThread.interrupt();
		}
		if (cmdSocket != null) {
			cmdSocket.close();
		}
	}
}
