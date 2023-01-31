package org.eclipse.californium.scandium.examples;

public enum Operation {
	/**
	 * Basic mode of operation entails performing a single handshake.
	 * No data is exchanged.
	 */
	BASIC,
	
	/**
	 * Basic operation + one echo if the handshake was completed successfully.
	 */
	ONE_ECHO,
	
	/**
	 * Full mode of operation entails a continuous loop of handshaking and echo-ing data.
	 * In this mode the server only terminates if the engine is closed. 
	 */
	FULL,
}
