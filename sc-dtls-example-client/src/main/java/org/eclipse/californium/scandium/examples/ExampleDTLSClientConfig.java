package org.eclipse.californium.scandium.examples;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.eclipse.californium.scandium.dtls.cipher.CipherSuite;

import com.beust.jcommander.Parameter;

/**
 * Contains the minimal subset of parameters which one should be able to toy around with when testing the scandium DTLS server.
 */
public class ExampleDTLSClientConfig {
	private static final String DEFAULT_STORE_PASSWORD = "student";
	private static final String DEFAULT_STORE_LOCATION = "/ec_secp256r1.jks";
	private static final String DEFAULT_STORE_ALIAS = "tls-attacker";
	private static final int DEFAULT_PORT = 20000; 
	
	@Parameter(names = "-port", required = false, description = "The port the client is connecting to")
	private Integer port = DEFAULT_PORT;
	
	@Parameter(names = "-startTimeout", required = false, description = "The time to wait before starting the client")
	private Integer startTimeout = 0;
	
	@Parameter(names = "-trustLocation", required = false, description = "The location of the trust store to use")
	private String trustLocation;
	
	@Parameter(names = "-trustAlias", required = false, description = "The alias looked up to gather certs from the trust store")
	private String trustAlias = DEFAULT_STORE_ALIAS;
	
	@Parameter(names = "-trustPassword", required = false, description = "The password with which the trust store is protected")
	private String trustPassword = DEFAULT_STORE_PASSWORD;
	
	@Parameter(names = "-keyLocation", required = false, description = "The location of the key store to use")
	private String keyLocation;
	
	@Parameter(names = "-keyAlias", required = false, description = "The alias looked up to gather certs from the key store")
	private String keyAlias = DEFAULT_STORE_ALIAS;
	
	@Parameter(names = "-keyPassword", required = false, description = "The password with which the key store is protected")
	private String keyPassword = DEFAULT_STORE_PASSWORD;
	
	@Parameter(names = "-pskKey", converter=HexStringToBytesConverter.class, required = false, description = "The password (in hex form without the prefix 0x) with which the trust store is protected")
	private byte [] pskKey = new byte [] {0x12, 0x34}; 
	
	@Parameter(names = "-pskIdentity", required = false, description = "The psk identity to use")
	public String pskIdentity = "Client_identity";
	
	@Parameter(names = "-timeout", required = false, description = "The retransmission timeout for the Scandium DTLS implementation")
	private Integer timeout = 20000;
	
	@Parameter(names = "-cipherSuites", required = false, description = "The cipher suites to use")
	private List<CipherSuite> cipherSuites = Arrays.asList(CipherSuite.TLS_PSK_WITH_AES_128_CBC_SHA256,
					CipherSuite.TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256);
	
	@Parameter(names = "-help", required = false, description = "Prints usage")
	private boolean help = false;

	@Parameter(names = "-starterAddress", required = false, description = "Uses a thread starter listening at ip_address:port")
	private String starterAddress = null;
	
	@Parameter(names = "-starterAck", required = false, description = "Configured the thread starter to acknowledge each reset")
	private boolean starterAck = false;
	
	@Parameter(names = "-continuous", required = false, description = "Do not stop thread starter after the learner closes the connection")
	private boolean continuous = false;
	
	@Parameter(names = "-clientAuth", required = false, description = "Defines the authentication method.")
	private ClientAuth clientAuth = ClientAuth.DISABLED;
	
	@Parameter(names = "-operation", required = false, description = "The client's mode of operation.")
	private Operation operation = Operation.FULL;
	
	public Integer getStartTimeout() {
		return startTimeout;
	}
	
	public String getTrustLocation() {
		return trustLocation;
	}
	
	public InputStream getTrustInputStream() throws FileNotFoundException {
		if (trustLocation == null) {
			return this.getClass().getResourceAsStream(DEFAULT_STORE_LOCATION);
		} else {
			return new FileInputStream(trustLocation);
		}
	}

	public String getTrustAlias() {
		return trustAlias;
	}

	public String getTrustPassword() {
		return trustPassword;
	}
	
	public String getKeyLocation() {
		return keyLocation;
	}
	
	public InputStream getKeyInputStream() throws FileNotFoundException {
		if (keyLocation == null) {
			return this.getClass().getResourceAsStream(DEFAULT_STORE_LOCATION);
		} else {
			return new FileInputStream(keyLocation);
		}
	}

	public String getKeyPassword() {
		return keyPassword;
	}
	
	public String getKeyAlias() {
		return keyAlias;
	}
	
	public Integer getTimeout() {
		return timeout;
	}

	public ClientAuth getClientAuth() {
		return clientAuth;
	}
	
	public boolean isHelp() {
		return help;
	}

	public int getPort() {
		return port;
	}
	
	public List<CipherSuite> getCipherSuites() {
		return cipherSuites;
	}
	
	
	public byte[] getPskKey() {
		return pskKey;
	}


	public String getPskIdentity() {
		return pskIdentity;
	}
	
	public String getStarterAddress() {
		return starterAddress;
	}
	
	public boolean isStarterAck() {
		return starterAck;
	}
	
	public boolean isContinuous() {
		return continuous;
	}
	
	public Operation getOperation() {
		return operation;
	}
	
}