/*******************************************************************************
 * Copyright (c) 2015, 2017 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Stefan Jucker - DTLS implementation
 *    Achim Kraus (Bosch Software Innovations GmbH) - add support for multiple clients
 *                                                    exchange multiple messages
 *    Achim Kraus (Bosch Software Innovations GmbH) - add client statistics
 *    Bosch Software Innovations GmbH - migrate to SLF4J
 *    Achim Kraus (Bosch Software Innovations GmbH) - add argument for payload length
 ******************************************************************************/
package org.eclipse.californium.scandium.examples;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;

import org.eclipse.californium.elements.AddressEndpointContext;
import org.eclipse.californium.elements.RawData;
import org.eclipse.californium.elements.RawDataChannel;
import org.eclipse.californium.scandium.DTLSConnector;
import org.eclipse.californium.scandium.config.DtlsConnectorConfig;
import org.eclipse.californium.scandium.dtls.CertificateType;
import org.eclipse.californium.scandium.dtls.cipher.CipherSuite.CertificateKeyAlgorithm;
import org.eclipse.californium.scandium.dtls.pskstore.PskStore;
import org.eclipse.californium.scandium.dtls.pskstore.StaticPskStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;

public class ExampleDTLSClient implements Runnable {
	private static final int DEFAULT_PORT = 5684;
	private static final String MESSAGE="HELLO";
	private static final Logger LOG = LoggerFactory.getLogger(ExampleDTLSClient.class);

	private static Integer port = DEFAULT_PORT;
	
	private DTLSConnector dtlsConnector;
	private Operation operation;
	
	
	public ExampleDTLSClient(ExampleDTLSClientConfig config) {
		operation = config.getOperation();
		try {
			DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();
			
			// Allows us to use cipher suites such as TLS_PSK_WITH_AES_128_CBC_SHA256
			// Only necessary in later (post 2.0.0) versions of Scandium.
			builder.setRecommendedCipherSuitesOnly(false);
			builder.setSupportedCipherSuites(config.getCipherSuites());
			
			if (config.getCipherSuites().stream().anyMatch(cs -> cs.isPskBased())) {
				PskStore pskStore = new StaticPskStore(config.getPskIdentity(), config.getPskKey());
				builder.setPskStore(pskStore);
			}
			if (config.getCipherSuites().stream().anyMatch(cs -> !cs.getCertificateKeyAlgorithm().equals(CertificateKeyAlgorithm.NONE))) {
				// load the trust store
				KeyStore trustStore = KeyStore.getInstance("JKS");
				InputStream inTrust = config.getTrustInputStream();  
				trustStore.load(inTrust, config.getTrustPassword().toCharArray());
							
				// load the key store
				KeyStore keyStore = KeyStore.getInstance("JKS");
				InputStream inKey = config.getKeyInputStream();
				keyStore.load(inKey, config.getKeyPassword().toCharArray());

				builder.setIdentity((PrivateKey)keyStore.getKey(config.getKeyAlias(), config.getKeyPassword().toCharArray()),
						keyStore.getCertificateChain(config.getKeyAlias()), CertificateType.X_509);
				

				// You can load multiple certificates if needed
				Certificate[] trustedCertificates = new Certificate[1];
				trustedCertificates[0] = trustStore.getCertificate(config.getTrustAlias());
				builder.setTrustStore(trustedCertificates);
			}

			builder.setRetransmissionTimeout(config.getTimeout());
			
			builder.setConnectionThreadCount(1);
			builder.setReceiverThreadCount(1);
			
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
			dtlsConnector.setRawDataReceiver(new RawDataChannel() {

				@Override
				public void receiveData(RawData raw) {
					if (dtlsConnector.isRunning()) {
						receive(raw);
					}
				}
			});

		} catch (GeneralSecurityException | IOException e) {
			LOG.error("Could not load the keystore", e);
		}
	}

	private void receive(RawData raw) {
		LOG.info("Received response: ", new String(raw.getBytes()));
		RawData.outbound(raw.getBytes(), raw.getEndpointContext(), null, false);
		RawData data = RawData.outbound(raw.getBytes(), raw.getEndpointContext(), null, false);
		if (operation == Operation.FULL || operation == Operation.ONE_ECHO) {
			dtlsConnector.send(data);
		}
		if (operation == Operation.ONE_ECHO || operation == Operation.ONE_MESSAGE) {
			stopClient();
		}
	}

	private void startClient() {
		try {
			dtlsConnector.start();
			startTest(new InetSocketAddress(InetAddress.getLoopbackAddress(), port));
		} catch (IOException e) {
			LOG.error("Cannot start connector", e);
		}
	}
	
	public void stopClient() {
		if (dtlsConnector.isRunning()) {
			dtlsConnector.destroy();
		}
		LOG.info("Client stopped");
	}
	
	private void startTest(InetSocketAddress peer) {
		byte [] message = {};
		if (operation == Operation.ONE_MESSAGE) {
			message = MESSAGE.getBytes();
		}
		RawData data = RawData.outbound(message, new AddressEndpointContext(peer), null, false);
		dtlsConnector.send(data);
	}

	
	public void run() {
		startClient();
		try {
			for (;;) {
				Thread.sleep(10);
			}
		} catch (InterruptedException e) {
			stopClient();
		}
	}
	
	public boolean isRunning() {
		return dtlsConnector.isRunning();
	}
	
	public InetSocketAddress getAddress() {
		return dtlsConnector.getAddress();
	}

	public static void main(String[] args) throws InterruptedException {
		ExampleDTLSClientConfig config = new ExampleDTLSClientConfig();
		JCommander commander = new JCommander(config);
		try {
			commander.parse(args);
		} catch(ParameterException e) {
			LOG.error("Could not parse provided parameters. ", e.getLocalizedMessage());
			commander.usage();
			return;
		}
		
		if (config.isHelp()) {
			commander.usage();
			return;
		}
		
		port = config.getPort();
	
		final ExampleDTLSClient client = new ExampleDTLSClient(config);
		if (config.getStarterAddress() == null) {
			LOG.info("Waiting {} ms", config.getStartTimeout());
			Thread.sleep(config.getStartTimeout());
			client.run();
		} else {
			try {
				ThreadStarter ts = new ThreadStarter(() -> 
						client, 
						config.getStarterAddress(),
						config.isContinuous(),
						config.getStartTimeout());
				ts.run();
			} catch (SocketException e) {
				LOG.error(e.getLocalizedMessage());
				client.stopClient();
			} catch (IOException e) {
				LOG.error(e.getLocalizedMessage());
				client.stopClient();
			};
		}
	}
}
