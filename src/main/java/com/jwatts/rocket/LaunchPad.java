package com.jwatts.rocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A LaunchPad is the object from which all your Rockets are launched. From the
 * other side, your LaunchPad is viewed as a LandingZone for others, i.e, a
 * place to which Rockets can be launched.
 * 
 * @author joeywatts
 *
 */
public class LaunchPad {

	public static interface LZCommunicationListener {
		public void onLZOnline(LaunchPad launchPad, LandingZone zone);

		public void onLZOffline(LaunchPad launchPad, LandingZone zone);
	}

	public static interface LandingListener {
		public void onLanding(LaunchPad launchPad, Rocket rocket);
	}

	public static interface ErrorListener {
		public void onError(LaunchPad launchPad, Exception e);
	}

	private Thread thread;
	private Selector selector;
	private LZCommunicationListener lzListener;
	private ErrorListener error;
	private ArrayList<LandingZone> landingZones;
	private HashMap<String, LandingListener> landingListeners;

	protected LaunchPad() {
		landingZones = new ArrayList<LandingZone>();
		landingListeners = new HashMap<String, LandingListener>();
	}

	/**
	 * Sets the ErrorListener for this LaunchPad.
	 * 
	 * @param err
	 *            the error listener.
	 * @return your LaunchPad.
	 */
	public LaunchPad onError(ErrorListener err) {
		error = err;
		return this;
	}

	/**
	 * Sets the Rocket landing listener.
	 * 
	 * @param landing
	 *            the LandingListener
	 * @return your LaunchPad.
	 */
	public LaunchPad onLanding(LandingListener landing) {
		return onLanding(landing, Rocket.DEFAULT_TAG);
	}

	/**
	 * Sets the Rocket landing listener.
	 * 
	 * @param landing
	 *            the LandingListener
	 * @param tag
	 *            the tag filter.
	 * @return your LaunchPad.
	 */
	public LaunchPad onLanding(LandingListener landing, String tag) {
		landingListeners.put(tag, landing);
		return this;
	}
	
	public LaunchPad removeLandingListener(String tag) {
		landingListeners.remove(tag);
		return this;
	}

	/**
	 * Sets the LandingZone listener.
	 * 
	 * @param lzListener
	 *            the LandingZone listener.
	 * @return your LaunchPad
	 */
	public LaunchPad onCommunicationWithLandingZone(
			LZCommunicationListener lzListener) {
		this.lzListener = lzListener;
		return this;
	}

	/**
	 * Initializes a new Rocket that can be sent from this LaunchPad to any
	 * LandingZone.
	 * 
	 * @param tag
	 *            the Tag
	 * 
	 * @return the Rocket.
	 */
	public Rocket prepareForLaunch(String tag) {
		return new Rocket(tag);
	}

	/**
	 * Initializes a new Rocket that can be sent from this LaunchPad to any
	 * LandingZone.
	 * 
	 * @return the Rocket.
	 */
	public Rocket prepareForLaunch() {
		return prepareForLaunch(Rocket.DEFAULT_TAG);
	}

	/**
	 * Gets a List of all available LandingZones.
	 * 
	 * @return the List of LandingZones.
	 */
	public List<LandingZone> getAvailableLandingZones() {
		return landingZones;
	}

	/**
	 * Shutdown the LaunchPad.
	 */
	public void shutdown() {
		stopThread();
		try {
			selector.close();
		} catch (IOException e) {
			error(e);
		}
		selector = null;
	}

	/**
	 * Connects with another LandingZone.
	 * 
	 * @param hostname
	 *            the LZ's hostname
	 * @param port
	 *            the LZ's port number
	 * @return your LaunchPad
	 */
	public static LaunchPad connect(String hostname, int port) {
		InetSocketAddress addr = new InetSocketAddress(hostname, port);
		LaunchPad pad = new LaunchPad();
		try {
			pad.selector = Selector.open();

			SocketChannel channel = SocketChannel.open();
			channel.configureBlocking(false);
			channel.register(pad.selector, SelectionKey.OP_CONNECT);
			channel.connect(addr);
			pad.startThread();
			return pad;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	/**
	 * Serves a LandingZone.
	 * 
	 * @param port
	 *            the port number.
	 * @return your LaunchPad.
	 */
	public static LaunchPad serve(int port) {
		LaunchPad pad = new LaunchPad();
		try {
			pad.selector = Selector.open();

			ServerSocketChannel channel = ServerSocketChannel.open();
			channel.configureBlocking(false);
			channel.socket().bind(new InetSocketAddress(port));
			SelectionKey key = channel.register(pad.selector,
					SelectionKey.OP_ACCEPT);
			key.attach(pad);

			pad.startThread();
			return pad;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private Runnable background = new Runnable() {
		@Override
		public void run() {
			while (!Thread.interrupted() && selector.isOpen()) {
				try {
					selector.select();
				} catch (IOException e1) {
					error(e1);
					try {
						selector.close();
					} catch (IOException e) {
						error(e);
					}
					continue;
				}
				Set<SelectionKey> keys = selector.selectedKeys();
				Iterator<SelectionKey> iter = keys.iterator();
				while (iter.hasNext()) {
					SelectionKey key = iter.next();
					iter.remove();
					if (!key.isValid()) {
						removeLandingZone(key);
						continue;
					}
					if (key.isAcceptable()) {
						if (handleAcceptConnection(key)) {
							continue;
						}

					} else {
						if (key.isConnectable()) {
							if (handleConnect(key)) {
								continue;
							}
						}
						// if (key.isReadable()) {
						if (handleRead(key)) {
							continue;
						}
						// }
						if (handleWrite(key)) {
							continue;
						}
					}
				}
			}
		}
	};

	/**
	 * Accepts a connection from another LandingZone.
	 * 
	 * @param key
	 *            the SelectionKey for the LandingZone.
	 */
	private boolean handleAcceptConnection(SelectionKey key) {
		/* We must accept connection from server. */
		ServerSocketChannel channel = (ServerSocketChannel) key.channel();
		SocketChannel client;
		try {
			client = channel.accept();
			if (client != null) {
				client.configureBlocking(false);
				client.socket().setTcpNoDelay(true);
				SelectionKey clientKey = client.register(selector,
						SelectionKey.OP_READ);
				addLandingZone(clientKey);
			}
		} catch (IOException e) {
			error(e);
			removeLandingZone(key);
			return true;
		}
		return false;
	}

	/**
	 * Finishes the connection to a LandingZone.
	 * 
	 * @param key
	 *            the SelectionKey for the LandingZone.
	 */
	private boolean handleConnect(SelectionKey key) {
		SocketChannel channel = (SocketChannel) key.channel();
		try {
			channel.configureBlocking(false);
			channel.finishConnect();
			key.interestOps(SelectionKey.OP_READ);
			addLandingZone(key);
		} catch (IOException e) {
			error(e);
			removeLandingZone(key);
			return true;
		}
		return false;
	}

	/**
	 * Handles the read operation.
	 * 
	 * @param key
	 *            the SelectionKey for the LandingZone.
	 */
	private boolean handleRead(SelectionKey key) {
		Object attachment = key.attachment();
		if (!(attachment instanceof LandingZone)) {
			addLandingZone(key);
		}
		LandingZone lz = (LandingZone) key.attachment();
		try {
			lz.read();
		} catch (IOException e) {
			error(e);
			removeLandingZone(key);
			return true;
		}
		return false;
	}

	/**
	 * Handles the write operation.
	 * 
	 * @param key
	 *            the SelectionKey for the LandingZone.
	 */
	private boolean handleWrite(SelectionKey key) {
		Object attachment = key.attachment();
		if (!(attachment instanceof LandingZone)) {
			addLandingZone(key);
		}
		LandingZone lz = (LandingZone) key.attachment();
		try {
			lz.write();
		} catch (IOException e) {
			error(e);
			removeLandingZone(key);
			return true;
		}
		return false;
	}

	/**
	 * Adds a LandingZone to the list of the LandingZones and dispatches the
	 * onLZOnline event.
	 * 
	 * @param key
	 *            the SelectionKey for the LandingZone's connection.
	 */
	private void addLandingZone(SelectionKey key) {
		final LandingZone lz = LandingZone.wrap(LaunchPad.this, key);
		landingZones.add(lz);
		if (lzListener != null) {
			new Thread(new Runnable() {
				@Override
				public void run() {
					lzListener.onLZOnline(LaunchPad.this, lz);
				}
			}).start();
		}
		key.attach(lz);
	}

	private void removeLandingZone(SelectionKey key) {
		Object a = key.attachment();
		if (a instanceof LandingZone) {
			final LandingZone lz = (LandingZone) a;
			landingZones.remove(lz);
			if (lzListener != null) {
				new Thread(new Runnable() {
					@Override
					public void run() {
						lzListener.onLZOffline(LaunchPad.this, lz);
					}
				}).start();
			}
		}
		key.cancel();
	}

	/**
	 * Gets the LaunchPad's selector.
	 * 
	 * @return the selector.
	 */
	protected Selector getSelector() {
		return selector;
	}

	/**
	 * Starts the LaunchPad's background thread.
	 */
	private void startThread() {
		thread = new Thread(background);
		thread.start();
	}

	/**
	 * Stops this LaunchPad's background thread.
	 */
	private void stopThread() {
		if (thread != null && thread.isAlive()) {
			thread.interrupt();
			try {
				thread.join();
			} catch (InterruptedException e) {
				error(e);
			}
		}
	}

	/**
	 * Dispatches an OnError event.
	 * 
	 * @param e
	 *            the Exception thrown
	 */
	private void error(Exception e) {
		if (error != null) {
			error.onError(this, e);
		}
	}

	/**
	 * Notifies listeners that a Rocket has impacted this LZ.
	 * 
	 * @param rocket
	 *            the Rocket
	 */
	protected void land(final Rocket rocket) {
		if (landingListeners.containsKey(rocket.getTag())) {
			final LandingListener i = landingListeners.get(rocket.getTag());
			new Thread(new Runnable() {
				@Override
				public void run() {
					i.onLanding(LaunchPad.this, rocket);
				}
			}).start();
		} else if (landingListeners.containsKey(Rocket.DEFAULT_TAG)) {
			final LandingListener i = landingListeners.get(Rocket.DEFAULT_TAG);
			new Thread(new Runnable() {
				@Override
				public void run() {
					i.onLanding(LaunchPad.this, rocket);
				}
			}).start();
		}
	}
}
