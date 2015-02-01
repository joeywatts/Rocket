package com.jwatts.rocket;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.UUID;

public class RocketValue {
	private static final byte TYPE_STRING = 'S', TYPE_BYTE = 'b',
			TYPE_BYTE_ARRAY = 'B', TYPE_INT = 'I', TYPE_SHORT = 's',
			TYPE_LONG = 'L', TYPE_FLOAT = 'F', TYPE_DOUBLE = 'D',
			TYPE_ROCKET_STREAM = 'R';

	private byte type;
	private byte[] data;

	protected RocketValue() {
	}

	public static RocketValue wrap(Object o) {
		if (o instanceof byte[]) {
			return wrap((byte[]) o);
		} else if (o instanceof Byte) {
			return wrap((byte) o);
		} else if (o instanceof String) {
			return wrap((String) o);
		} else if (o instanceof Short) {
			return wrap((short) o);
		} else if (o instanceof Long) {
			return wrap((long) o);
		} else if (o instanceof Integer) {
			return wrap((int) o);
		} else if (o instanceof Float) {
			return wrap((float) o);
		} else if (o instanceof Double) {
			return wrap((double) o);
		} else if (o instanceof RocketStream) {
			return wrap((RocketStream) o);
		}
		return null;
	}

	public static RocketValue wrap(byte[] array) {
		RocketValue val = new RocketValue();
		val.type = TYPE_BYTE_ARRAY;
		val.data = array;
		return val;
	}

	public static RocketValue wrap(byte b) {
		RocketValue val = new RocketValue();
		val.type = TYPE_BYTE;
		val.data = new byte[] { b };
		return val;
	}

	public static RocketValue wrap(String s) {
		RocketValue val = new RocketValue();
		val.type = TYPE_STRING;
		val.data = s.getBytes(Charset.forName("UTF-8"));
		return val;
	}

	public static RocketValue wrap(float f) {
		RocketValue val = new RocketValue();
		val.type = TYPE_FLOAT;
		val.data = ByteBuffer.allocate(4).putFloat(f).array();
		return val;
	}

	public static RocketValue wrap(int i) {
		RocketValue val = new RocketValue();
		val.type = TYPE_INT;
		val.data = ByteBuffer.allocate(4).putInt(i).array();
		return val;
	}

	public static RocketValue wrap(short s) {
		RocketValue val = new RocketValue();
		val.type = TYPE_SHORT;
		val.data = ByteBuffer.allocate(2).putShort(s).array();
		return val;
	}

	public static RocketValue wrap(long l) {
		RocketValue val = new RocketValue();
		val.type = TYPE_LONG;
		val.data = ByteBuffer.allocate(8).putLong(l).array();
		return val;
	}

	public static RocketValue wrap(double d) {
		RocketValue val = new RocketValue();
		val.type = TYPE_DOUBLE;
		val.data = ByteBuffer.allocate(8).putDouble(d).array();
		return val;
	}
	
	public static RocketValue wrap(RocketStream r) {
		RocketValue val = new RocketValue();
		val.type = TYPE_ROCKET_STREAM;
		val.data = ByteBuffer.allocate(16).putLong(r.getUUID().getMostSignificantBits()).putLong(r.getUUID().getLeastSignificantBits()).array();
		return val;
	}

	public final int getSize() {
		// one byte for type.
		// 4 bytes for size (int)
		// the data.
		return 1 + 4 + data.length;
	}

	/**
	 * Serialize this RocketValue into a ByteBuffer.
	 * 
	 * @precondition buffer has getSize() bytes remaining.
	 * @param buffer
	 *            the ByteBuffer to which this value will be written.
	 */
	public void write(ByteBuffer buffer) {
		buffer.putInt(getSize() - 5);
		buffer.put(type);
		buffer.put(data);
	}

	/**
	 * Parse the value from the ByteBuffer.
	 * 
	 * @param buffer
	 *            the ByteBuffer
	 * @param length
	 *            the length of the value in bytes
	 * @return the object parsed
	 */
	public static Object parse(ByteBuffer buffer) {
		int length = buffer.getInt();
		byte type = buffer.get();
		byte[] data;
		switch (type) {
		case TYPE_ROCKET_STREAM:
			long msb = buffer.getLong();
			long lsb = buffer.getLong();
			UUID uuid = new UUID(msb, lsb);
			RocketStream rs = new RocketStream(uuid);
			return rs;
		case TYPE_INT:
			return buffer.getInt();
		case TYPE_BYTE:
			return buffer.get();
		case TYPE_DOUBLE:
			return buffer.getDouble();
		case TYPE_FLOAT:
			return buffer.getFloat();
		case TYPE_SHORT:
			return buffer.getShort();
		case TYPE_LONG:
			return buffer.getLong();
		case TYPE_STRING:
			data = new byte[length];
			buffer.get(data);
			return new String(data, Charset.forName("UTF-8"));
		case TYPE_BYTE_ARRAY:
		default:
			data = new byte[length];
			buffer.get(data);
			return data;
		}
	}

	@Override
	public int hashCode() {
		return Arrays.hashCode(data);
	}
}
