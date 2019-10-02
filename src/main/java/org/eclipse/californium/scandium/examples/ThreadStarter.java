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

/**
 * We use this class to avoid having to restart the vm (which is can be a slow process). 
 */
public class ThreadStarter {
	
	private Supplier<Thread> supplier;
	private ServerSocket srvSocket;
	private Thread dtlsServerThread;
	private Socket cmdSocket;
	private boolean ack;
	
	public ThreadStarter(Supplier<Thread> supplier, String ipPort, boolean ack) throws IOException {
		String[] addr = ipPort.split("\\:");
		InetSocketAddress address = new InetSocketAddress(addr[0], Integer.valueOf(addr[1]));		
		this.supplier = supplier;
		srvSocket = new ServerSocket();
		srvSocket.setReuseAddress(true);
		srvSocket.bind(address);
		this.ack = ack;
		
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					ThreadStarter.this.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}));
	}
	
	public void run() throws IOException {
		System.out.println("Listening at " + srvSocket.getInetAddress() + ":" + srvSocket.getLocalPort());
		cmdSocket = srvSocket.accept();
		BufferedReader in = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
		BufferedWriter out = new BufferedWriter(new OutputStreamWriter(cmdSocket.getOutputStream()));
		dtlsServerThread = null;
		while (true) {
			try {
				String cmd = in.readLine();
				System.out.println("Received: " + cmd);
				if (cmd != null) {
					switch(cmd.trim()) {
					case "reset":
						if (dtlsServerThread != null) {
							dtlsServerThread.interrupt();
						}
						dtlsServerThread = supplier.get();
						dtlsServerThread.start();
						if (ack) {
							out.write("ack");
							out.newLine();
							out.flush();
						}
						break;
					case "exit":
						close();
						return;
					}
				} else {
					close();
					return;
				}
			} catch (Exception e) {
				String errorFileName = "ts.error" + (System.currentTimeMillis() / 1000) + ".log";
				PrintWriter errorPw = new PrintWriter(new FileWriter(errorFileName));
				e.printStackTrace(errorPw);
				errorPw.close();
				close();
				return;
			}
		}
	}
	
	private void close() throws IOException {
		System.out.println("Shutting down thread starter");
		if (dtlsServerThread != null) {
			dtlsServerThread.interrupt();
		}
		if (cmdSocket != null) {
			cmdSocket.close();
		}
		srvSocket.close();
	}
}
