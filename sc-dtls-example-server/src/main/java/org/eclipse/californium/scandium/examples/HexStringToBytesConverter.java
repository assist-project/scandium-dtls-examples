package org.eclipse.californium.scandium.examples;

import java.math.BigInteger;

import com.beust.jcommander.IStringConverter;

public class HexStringToBytesConverter implements IStringConverter<byte []>{

	@Override
	public byte[] convert(String value) {
		long longVal = Long.parseUnsignedLong(value, 16);
		BigInteger bitIntVal = BigInteger.valueOf(longVal);
		return bitIntVal.toByteArray();
	}
	
}
