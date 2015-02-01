package com.jwatts.rocket;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class LandingZone {
	private LaunchPad launchPad;
	private SelectionKey key;
	private SocketChannel channel;
	private ConcurrentLinkedQueue<RocketBuffer> writeBuffers;
	private ByteBuffer currentWriteBuffer[], currentReadBuffer;
	private ByteBuffer readHeaderBuffer;

	private Map<UUID, RocketStream> readStreams;
	private ConcurrentLinkedQueue<RocketStream> writeStreams;
	private RocketStream writeStream;
	private boolean lastWriteWasStream;

	protected LandingZone() {
		currentWriteBuffer = new ByteBuffer[2];
		currentWriteBuffer[0] = ByteBuffer.allocate(5);
		writeStreams = new ConcurrentLinkedQueue<RocketStream>();
		writeBuffers = new ConcurrentLinkedQueue<RocketBuffer>();
		readStreams = Collections
				.synchronizedMap(new HashMap<UUID, RocketStream>());
		readHeaderBuffer = ByteBuffer.allocate(5);
		lastWriteWasStream = false;
	}

	/**
	 * Reads data that was sent by this LandingZone.
	 * 
	 * @throws IOException
	 */
	protected void read() throws IOException {
		int bytesRead = 0;
		while (readHeaderBuffer.hasRemaining()) {
			bytesRead = channel.read(readHeaderBuffer);
			if (bytesRead == -1) {
				throw new IOException();
			}
			if (bytesRead == 0) {
				key.interestOps(SelectionKey.OP_READ);
				return;
			}
		}
		if (currentReadBuffer == null) {
			int length = readHeaderBuffer.getInt(1);
			currentReadBuffer = ByteBuffer.allocate(length);
		}
		while (currentReadBuffer.hasRemaining()) {
			bytesRead = channel.read(currentReadBuffer);
			if (bytesRead == -1) {
				throw new IOException();
			}
			if (bytesRead == 0) {
				key.interestOps(SelectionKey.OP_READ);
				return;
			}
		}
		/* currentReadBuffer.remaining() == 0 */
		readHeaderBuffer.position(0);
		byte type = readHeaderBuffer.get(0);
		currentReadBuffer.flip();
		switch (type) {
		case Rocket.HEADER_BYTE_TYPE:
			launchPad.land(new Rocket().parse(this, currentReadBuffer));
			break;
		case RocketStream.HEADER_BYTE_TYPE:
		case RocketStream.HEADER_BYTE_TYPE_FINISHED:
			boolean finished = false;
			if (type == RocketStream.HEADER_BYTE_TYPE_FINISHED) {
				finished = true;
			}
			long msb = currentReadBuffer.getLong();
			long lsb = currentReadBuffer.getLong();
			UUID uuid = new UUID(msb, lsb);
			RocketStream rs = null;
			if (readStreams.containsKey(uuid)) {
				rs = readStreams.get(uuid);
			} else {
				rs = new RocketStream(uuid);
				readStreams.put(uuid, rs);
			}
			rs.data(currentReadBuffer, finished);
			break;
		}
		currentReadBuffer = null;
	}

	/**
	 * Writes data to this LandingZone.
	 * 
	 * @throws IOException
	 */
	protected void write() throws IOException {
		if (!needsWrite()) {
			key.interestOps(SelectionKey.OP_READ);
			return;
		}
		if (isDoneWritingCurrentBuffer()) {
			getNewBufferFromQueue();
		}
		if (!isDoneWritingCurrentBuffer()) {
			channel.write(currentWriteBuffer);
			if (needsWrite()) {
				key.interestOps(SelectionKey.OP_WRITE);
			} else {
				key.interestOps(SelectionKey.OP_READ);
			}
		}
	}

	protected void sendBuffer(RocketBuffer buffer) {
		writeBuffers.offer(buffer);
		key.interestOps(SelectionKey.OP_WRITE);
		launchPad.getSelector().wakeup();
	}

	protected void sendStream(RocketStream stream) {
		writeStreams.offer(stream);
		key.interestOps(SelectionKey.OP_WRITE);
		launchPad.getSelector().wakeup();
	}

	protected RocketStream registerStream(RocketStream stream) {
		if (readStreams.containsKey(stream.getUUID())) {
			return readStreams.get(stream.getUUID());
		}
		readStreams.put(stream.getUUID(), stream);
		return stream;
	}

	private void getNewBufferFromQueue() {
		if ((lastWriteWasStream || (writeStream == null && writeStreams
				.isEmpty())) && !writeBuffers.isEmpty()) {
			RocketBuffer buffer = writeBuffers.poll();
			currentWriteBuffer[0].position(0);
			currentWriteBuffer[0].put(buffer.getType());
			currentWriteBuffer[0].putInt(buffer.getBuffer().limit());
			currentWriteBuffer[0].flip();
			currentWriteBuffer[1] = buffer.getBuffer();
			lastWriteWasStream = false;
		} else if (writeStream != null || !writeStreams.isEmpty()) {
			if (writeStream == null) {
				writeStream = writeStreams.poll();
			}
			RocketBuffer buffer = writeStream.getBuffer();
			currentWriteBuffer[0].position(0);
			currentWriteBuffer[0].put(buffer.getType());
			currentWriteBuffer[0].putInt(buffer.getBuffer().limit());
			currentWriteBuffer[0].flip();
			currentWriteBuffer[1] = buffer.getBuffer();
			if (writeStream.isFinishedWriting()) {
				writeStream = null;
			}
			lastWriteWasStream = true;
		}
	}

	private boolean isDoneWritingCurrentBuffer() {
		if (currentWriteBuffer[1] == null) {
			return true;
		}
		for (ByteBuffer buf : currentWriteBuffer) {
			if (buf.hasRemaining()) {
				return false;
			}
		}
		return true;
	}

	protected boolean needsWrite() {
		return !isDoneWritingCurrentBuffer() || !writeBuffers.isEmpty()
				|| !writeStreams.isEmpty()
				|| (writeStream != null && !writeStream.isFinishedWriting());
	}

	protected static LandingZone wrap(LaunchPad pad, SelectionKey key) {
		LandingZone zone = new LandingZone();
		zone.key = key;
		zone.channel = (SocketChannel) key.channel();
		zone.launchPad = pad;
		return zone;
	}
}
