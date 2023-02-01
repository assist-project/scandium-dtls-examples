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
import org.eclipse.californium.elements.Connector;
import org.eclipse.californium.elements.EndpointContext;
import org.eclipse.californium.elements.MessageCallback;
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

/**
 * A restartable echo-capable DTLS client/server.
 */
public class DtlsClientServer {

	private static final Logger LOG = LoggerFactory.getLogger(DtlsClientServer.class.getName());

	private DTLSConnector dtlsConnector;
	private Operation operation;
	private boolean client;
	private Integer port;

	public DtlsClientServer(DtlsClientServerConfig config) {
		operation = config.getOperation();
		client = config.isClient();
		port = config.getPort();

		try {
			DtlsConnectorConfig.Builder builder = new DtlsConnectorConfig.Builder();

			builder.setSupportedCipherSuites(config.getCipherSuites());

			if (config.getCipherSuites().stream().anyMatch(cs -> cs.isPskBased())) {
				PskStore pskStore = new StaticPskStore(config.getPskIdentity(), config.getPskKey());
				builder.setPskStore(pskStore);
			}
			if (config.getCipherSuites().stream()
					.anyMatch(cs -> !cs.getCertificateKeyAlgorithm().equals(CertificateKeyAlgorithm.NONE))) {
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
			builder.setMaxConnections(config.getMaxConnections());

			builder.setReceiverThreadCount(2);
			builder.setConnectionThreadCount(1);

			switch (config.getClientAuth()) {
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

	public void start() {
		try {
			dtlsConnector.start();
			LOG.info("DTLS {} started", role());
			if (client) {
				InetSocketAddress peer = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
				byte[] message = {};
				RawData data = RawData.outbound(message, new AddressEndpointContext(peer), null, false);
				dtlsConnector.send(data);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unexpected error starting the DTLS " + role(), e);
		}
	}

	public void stop() {
		// we (hopefully) destroy any leftover state
		dtlsConnector.destroy();
		LOG.info("DTLS {} stopped", role());
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
			LOG.info("Received message: {}", new String(raw.getBytes()));
			MessageCallback callback = null;
			if (operation == Operation.ONE_ECHO) {
				callback = new MessageCallback() {
					@Override
					public void onSent() {
						stop();
					}

					@Override
					public void onError(Throwable error) {
					}

					@Override
					public void onDtlsRetransmission(int flight) {
					}

					@Override
					public void onContextEstablished(EndpointContext context) {

					}

					@Override
					public void onConnecting() {
					}
				};
			}
			RawData data = RawData.outbound(raw.getBytes(), raw.getEndpointContext(), callback, false);
			if (operation == Operation.FULL || operation == Operation.ONE_ECHO) {
				connector.send(data);
			}
		}
	}
	
	public String role() {
		return client ? "client" : "server";
	}
	
	public static void main(String[] args) {
		DtlsClientServerConfig config = new DtlsClientServerConfig();
		JCommander commander = new JCommander(config);
		try {
			commander.parse(args);
		} catch (ParameterException e) {
			LOG.error("Could not parse provided parameters.");
			LOG.error(e.getLocalizedMessage());
			commander.usage();
			return;
		}

		if (config.isHelp()) {
			commander.usage();
			return;
		}

		final DtlsClientServer clientServer = new DtlsClientServer(config);
		if (config.getStarterAddress() == null) {
			clientServer.start();
		} else {
			try {
				ThreadStarter ts = new ThreadStarter(() -> new DtlsClientServer(config), config.getStarterAddress());
				ts.run();
			} catch (SocketException e) {
				LOG.error(e.getLocalizedMessage());
				clientServer.stop();
			} catch (IOException e) {
				LOG.error(e.getLocalizedMessage());
				clientServer.stop();
			}
			;
		}
	}
}
