package com.jwatts.rocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;

public class RocketStream {

	public static final byte HEADER_BYTE_TYPE = 1;
	public static final byte HEADER_BYTE_TYPE_FINISHED = 2;

	public static interface StreamData {
		public ByteBuffer getBuffer();

		public boolean isFinished();
	}

	public static interface StreamReader {
		public void onStreamData(ByteBuffer data);

		public void onStreamClosed();
	}

	/*
	 * We must cache all the ByteBuffers that are received before the stream is
	 * opened.
	 */
	private volatile boolean isOpened, isFinished;
	private static final int STREAM_BUFFER_SIZE = 8192;
	private ByteBuffer streamBuffer;
	private StreamReader reader;
	private StreamData data;
	private ArrayList<ByteBuffer> bufferCache;
	private UUID uuid;

	protected RocketStream(UUID uuid) {
		this.uuid = uuid;
		bufferCache = new ArrayList<ByteBuffer>();
		isOpened = false;
	}

	private ByteBuffer currentWriteBuffer;

	protected RocketBuffer getBuffer() {
		if (streamBuffer == null) {
			streamBuffer = ByteBuffer.allocate(STREAM_BUFFER_SIZE);
		}
		if (currentWriteBuffer == null
				|| currentWriteBuffer.position() == currentWriteBuffer
						.capacity()) {
			currentWriteBuffer = data.getBuffer();
		}
		streamBuffer.position(0);
		streamBuffer.putLong(uuid.getMostSignificantBits()).putLong(
				uuid.getLeastSignificantBits());
		int length = Math.min(currentWriteBuffer.capacity()
				- currentWriteBuffer.position(), streamBuffer.remaining());
		currentWriteBuffer.limit(currentWriteBuffer.position() + length);
		streamBuffer.put(currentWriteBuffer);
		streamBuffer.flip();
		if (currentWriteBuffer.limit() == currentWriteBuffer.capacity()
				&& data.isFinished()) {
			return new RocketBuffer(HEADER_BYTE_TYPE_FINISHED, streamBuffer);
		}
		return new RocketBuffer(HEADER_BYTE_TYPE, streamBuffer);
	}

	protected boolean isFinishedWriting() {
		return (currentWriteBuffer == null || currentWriteBuffer.position() == currentWriteBuffer
				.capacity()) && data.isFinished();
	}

	protected void data(ByteBuffer buffer, boolean finished) {
		bufferCache.add(buffer);
		isFinished = finished;
		if (isOpened) {
			Iterator<ByteBuffer> iter = bufferCache.iterator();
			while (iter.hasNext()) {
				reader.onStreamData(iter.next());
				iter.remove();
			}
			if (finished) {
				reader.onStreamClosed();
			}
		}
	}

	public void openStream(StreamReader reader) {
		this.reader = reader;
		isOpened = true;
		if (isFinished) {
			Iterator<ByteBuffer> iter = bufferCache.iterator();
			while (iter.hasNext()) {
				ByteBuffer buffer = iter.next();
				reader.onStreamData(buffer);
				iter.remove();
			}
			reader.onStreamClosed();
		}
	}

	public UUID getUUID() {
		return uuid;
	}

	public static RocketStream createStream(StreamData streamData) {
		RocketStream stream = new RocketStream(UUID.randomUUID());
		stream.data = streamData;
		return stream;
	}

	public static RocketStream createStreamFromFile(final File file)
			throws FileNotFoundException {
		StreamData data = new StreamData() {
			ByteBuffer buffer = ByteBuffer.allocate(8192 * 4);
			FileInputStream fis = new FileInputStream(file);
			int bytesRead = 0;

			@Override
			public boolean isFinished() {
				return bytesRead == -1;
			}

			@Override
			public ByteBuffer getBuffer() {
				try {
					buffer.position(0);
					bytesRead = fis.read(buffer.array());
					if (bytesRead == -1) {
						buffer.limit(0);
					} else {
						buffer.limit(bytesRead);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				return buffer;
			}
		};
		return createStream(data);
	}
}
