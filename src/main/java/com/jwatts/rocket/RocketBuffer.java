package com.jwatts.rocket;

import java.nio.ByteBuffer;

public class RocketBuffer {
	private byte type;
	private ByteBuffer buffer;
	
	public RocketBuffer(byte type, ByteBuffer buffer) {
		this.type = type;
		this.buffer = buffer;
	}
	
	public byte getType() {
		return type;
	}
	
	public ByteBuffer getBuffer() {
		return buffer;
	}
	
	public RocketBuffer readOnlyClone() {
		return new RocketBuffer(type, buffer.asReadOnlyBuffer());
	}
}
