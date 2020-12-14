/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Stefan Jucker - DTLS implementation
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 ******************************************************************************/
package org.eclipse.californium.scandium.examples;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;
import org.eclipse.californium.scandium.dtls.pskstore.InMemoryPskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;


/**
 * A restartable DTLS server.
 */
public class ExampleDTLSServer extends Thread{

	private static final Logger LOG = LoggerFactory
			.getLogger(ExampleDTLSServer.class.getName());

	private DTLSConnector dtlsConnector;

	public ExampleDTLSServer(ExampleDTLSServerConfig config) {
		InMemoryPskStore pskStore = new InMemoryPskStore();
		// put in the PSK store the default identity/psk for tinydtls tests
		pskStore.setKey(config.getPskIdentity(), config.getPskKey());
		
		try {
			// load the trust store
			KeyStore trustStore = KeyStore.getInstance("JKS");
			InputStream inTrust = new FileInputStream(config.getTrustLocation()); 
			trustStore.load(inTrust, config.getTrustPassword().toCharArray());
			
			// load the key store
			KeyStore keyStore = KeyStore.getInstance("JKS");
			InputStream inKey = new FileInputStream(config.getKeyLocation());
			keyStore.load(inKey, config.getKeyPassword().toCharArray());

			DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
			builder.setPskStore(pskStore);
			if (config.getStarterAddress() == null) {
				builder.setAddress(new InetSocketAddress(config.getPort()));
			}
			builder.setIdentity((PrivateKey)keyStore.getKey(config.getKeyAlias(), config.getKeyPassword().toCharArray()),
					keyStore.getCertificateChain(config.getKeyAlias()), CertificateType.X_509);
			builder.setSupportedCipherSuites(config.getCipherSuites().toArray(new CipherSuite [config.getCipherSuites().size()]));
			builder.setRetransmissionTimeout(config.getTimeout());
			builder.setMaxConnections(config.getMaxConnections());
			builder.setReceiverThreadCount(1);
			
			// You can load multiple certificates if needed
			Certificate[] trustedCertificates = new Certificate[1];
			trustedCertificates[0] = trustStore.getCertificate(config.getTrustAlias());
			builder.setTrustStore(trustedCertificates);
			
			switch(config.getClientAuth()) {
			case NEEDED:
				builder.setClientAuthenticationRequired(true);
				break;
			case WANTED:
				builder.setClientAuthenticationRequired(false);
				builder.setClientAuthenticationWanted(true);
				break;
			case DISABLED:
				builder.setClientAuthenticationRequired(false);
				builder.setClientAuthenticationWanted(false);
			}
			dtlsConnector = new DTLSConnector(builder.build());
			dtlsConnector.setRawDataReceiver(new RawDataChannelImpl(dtlsConnector));
		} catch (GeneralSecurityException | IOException e) {
			LOG.error("Could not load the keystore", e);
		}

	}

	public void startServer() {
		try {
			dtlsConnector.start();
			LOG.info("DTLS example server started");
		} catch (IOException e) {
			throw new IllegalStateException(
					"Unexpected error starting the DTLS UDP server", e);
		}
	}
	
	public void stopServer() {
		// we (hopefully) destroy any leftover state
		dtlsConnector.destroy();
		LOG.info("DTLS example server stopped");
	}
	
	public void run() {
		startServer();
		try {
			for (;;) {
				Thread.sleep(10);
			}
		} catch (InterruptedException e) {
			stopServer();
		}
	}
	
	public boolean isRunning() {
		return dtlsConnector.isRunning();
	}
	
	public InetSocketAddress getAddress() {
		return dtlsConnector.getAddress();
	}


	private class RawDataChannelImpl implements RawDataChannel {

		private Connector connector;

		public RawDataChannelImpl(Connector con) {
			this.connector = con;
		}

		@Override
		public void receiveData(final RawData raw) {
			if (LOG.isInfoEnabled()) {
				LOG.info("Received request: {}", new String(raw.getBytes()));
			}
			RawData response = RawData.outbound("ACK".getBytes(),
					raw.getEndpointContext(), null, false);
			connector.send(response);
		}
	}

	public static void main(String[] args) {
		ExampleDTLSServerConfig config = new ExampleDTLSServerConfig();
		JCommander commander = new JCommander(config);
		commander.parse(args);
		if (config.isHelp()) {
			commander.usage();
			return;
		}
	
		final ExampleDTLSServer server = new ExampleDTLSServer(config);
		if (config.getStarterAddress() == null) {
			server.run();
		} else {
			try {
				ThreadStarter ts = new ThreadStarter(server, 
						config.getStarterAddress(),
						config.isContinuous());
				ts.run();
			} catch (SocketException e) {
				e.printStackTrace();
				server.stopServer();
			} catch (IOException e) {
				e.printStackTrace();
				server.stopServer();
			};
		}
	}
}
