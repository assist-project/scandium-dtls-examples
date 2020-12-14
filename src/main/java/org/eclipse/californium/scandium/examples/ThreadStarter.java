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

/**
 * We use this class to avoid having to restart the vm (which is can be a slow process). 
 * 
 */
public class ThreadStarter {
	
	private ServerSocket srvSocket;
	private ExampleDTLSServer dtlsServer;
	private Socket cmdSocket;
	private Integer port;
	private boolean continuous;
	
	public ThreadStarter(ExampleDTLSServer dtlsServer, String ipPort, boolean continuous) throws IOException {
		String[] addr = ipPort.split("\\:");
		port = Integer.valueOf(addr[1]);
		InetSocketAddress address = new InetSocketAddress(addr[0], port);		
		this.dtlsServer = dtlsServer;
		srvSocket = new ServerSocket();
		srvSocket.setReuseAddress(true);
		srvSocket.bind(address);
		this.continuous = continuous;
		
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
		System.out.println("Listening at " + srvSocket.getInetAddress() + ":" + srvSocket.getLocalPort());
		do {
			cmdSocket = srvSocket.accept();
			BufferedReader in = new BufferedReader(new InputStreamReader(cmdSocket.getInputStream()));
			BufferedWriter out = new BufferedWriter(new OutputStreamWriter(cmdSocket.getOutputStream()));
			while (true) {
				try {
					String cmd = in.readLine();
					System.out.println("Received: " + cmd);
					if (cmd != null) {
						switch(cmd.trim()) {
							// command for killing the current server thread and spawning a new one
						case "reset":
							// empty space acts as reset, used for debugging purposes
						case "":
							// we stop the server and restart it
							// synchronization is taken care of by the scandium library, meaning we don't have to wait until the server is running
							dtlsServer.stopServer();
							dtlsServer.startServer();
							
							out.write(String.valueOf(dtlsServer.getAddress().getPort()));
							out.newLine();
							out.flush();
							break;
							
							// command for exiting
						case "exit":
							closeAll();
							return;
						}
					} else {
						System.out.println("Received Nothing");
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
		System.out.println("Shutting down thread starter");
		closeData();
		srvSocket.close();
	}
	
	private void closeData() throws IOException{
		if (dtlsServer != null) {
			dtlsServer.stopServer();
		}
		if (cmdSocket != null) {
			cmdSocket.close();
		}
	}
}