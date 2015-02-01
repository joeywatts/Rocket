package com.jwatts.rocket;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class Rocket {
	public static final byte HEADER_BYTE_TYPE = 0;
	public static final String DEFAULT_TAG = "default";
	private String tag;
	private HashMap<String, Object> data;
	private LandingZone origin;

	protected Rocket(String tag) {
		this.tag = tag;
		data = new HashMap<String, Object>();
	}

	protected Rocket() {
		this(DEFAULT_TAG);
	}

	/**
	 * Attaches data to the rocket. (Key/Value Pair)
	 * 
	 * @param key
	 *            the unique key for this value.
	 * @param value
	 *            the value.
	 * @return this Rocket.
	 */
	public Rocket attach(String key, Object value) {
		/* TODO: type check value. */
		data.put(key, value);
		return this;
	}

	public Object get(String key) {
		return data.get(key);
	}

	public String getString(String key) {
		return (String) get(key);
	}

	public int getInt(String key) {
		return (int) get(key);
	}

	public short getShort(String key) {
		return (short) get(key);
	}

	public long getLong(String key) {
		return (long) get(key);
	}

	public float getFloat(String key) {
		return (float) get(key);
	}

	public double getDouble(String key) {
		return (double) get(key);
	}

	public byte getByte(String key) {
		return (byte) get(key);
	}

	public byte[] getBytes(String key) {
		return (byte[]) get(key);
	}

	public RocketStream getRocketStream(String key) {
		return (RocketStream) get(key);
	}

	public Set<Map.Entry<String, Object>> entrySet() {
		return data.entrySet();
	}

	public Collection<Object> values() {
		return data.values();
	}

	public Set<String> keySet() {
		return data.keySet();
	}
	
	public boolean containsKey(String key) {
		return data.containsKey(key);
	}
	
	public boolean containsValue(Object val) {
		return data.containsValue(val);
	}
	
	public int size() {
		return data.size();
	}

	public String getTag() {
		return tag;
	}

	/**
	 * Launch this Rocket at a LandingZones.
	 * 
	 * @param zone
	 *            the LandingZone.
	 */
	public void launch(LandingZone zone) {
		RocketBuffer rb = toRocketBuffer();
		zone.sendBuffer(rb.readOnlyClone());
		for (Object o : values()) {
			if (o instanceof RocketStream) {
				zone.sendStream((RocketStream) o);
			}
		}
	}

	/**
	 * Converts this Rocket's data into a ByteBuffer.
	 * 
	 * @return the ByteBuffer.
	 */
	private RocketBuffer toRocketBuffer() {
		int totalSize = 0;
		RocketValue rocketTag = RocketValue.wrap(tag);
		totalSize += rocketTag.getSize();
		HashMap<RocketValue, RocketValue> values = new HashMap<RocketValue, RocketValue>();
		for (Entry<String, Object> entry : data.entrySet()) {
			String key = entry.getKey();
			Object value = entry.getValue();
			RocketValue rocketKey = RocketValue.wrap(key);
			RocketValue rocketValue = RocketValue.wrap(value);
			values.put(rocketKey, rocketValue);
			totalSize += rocketKey.getSize() + rocketValue.getSize();
		}
		ByteBuffer buffer = ByteBuffer.allocate(totalSize);
		rocketTag.write(buffer);
		for (Entry<RocketValue, RocketValue> entry : values.entrySet()) {
			RocketValue key = entry.getKey();
			key.write(buffer);
			RocketValue value = entry.getValue();
			value.write(buffer);
		}
		buffer.flip();
		RocketBuffer rb = new RocketBuffer(HEADER_BYTE_TYPE, buffer);
		return rb;
	}

	public Rocket parse(LandingZone zone, ByteBuffer buffer) {
		origin = zone;
		tag = null;
		while (buffer.hasRemaining()) {
			if (tag == null) {
				tag = (String) RocketValue.parse(buffer);
			} else {
				String key = (String) RocketValue.parse(buffer);
				Object value = RocketValue.parse(buffer);
				if (value instanceof RocketStream) {
					value = zone.registerStream((RocketStream) value);
				}
				attach(key, value);
			}
		}
		return this;
	}

	/**
	 * Gets the origin of this Rocket.
	 * 
	 * @return the LandingZone where this Rocket came from, if it was created
	 *         and hasn't been sent anywhere returns null.
	 */
	public LandingZone getOrigin() {
		return origin;
	}

}
