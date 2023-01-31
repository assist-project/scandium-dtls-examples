package org.eclipse.californium.scandium.examples;

import org.junit.jupiter.api.Test;

class DtlsClientServerTest {
	
	// Not a very useful test case since handshake is not completed unless we run it through a debugger

	@Test
	void test() throws InterruptedException {
		final DtlsClientServerConfig serverConfig = new DtlsClientServerConfig();
		serverConfig.setPort(50000);
		serverConfig.setClient(false);
		final DtlsClientServerConfig clientConfig = new DtlsClientServerConfig();
		clientConfig.setClient(true);
		clientConfig.setPort(50000);
		clientConfig.setOperation(Operation.BASIC);
		DtlsClientServer server = new DtlsClientServer(serverConfig);
		server.start();
		while (!server.isRunning()) {
			Thread.sleep(10);
		}
		clientConfig.setPort(server.getAddress().getPort());
		DtlsClientServer client = new DtlsClientServer(clientConfig);
		client.start();
	}
}
