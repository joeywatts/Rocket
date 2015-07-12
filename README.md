# Rocket

A simple Java socket library that wraps the complex non-blocking Java Socket API.

## Building

*Rocket* uses Gradle, and is completely self-contained. Just clone the repo, and run `gradle jar`.

## Installation

[Download](build/libs/rocket-0.1.jar) or build the *Rocket* jar file, and link it with your project.

**OR**

Use [*JitPack*](https://jitpack.io/#joeywatts/Rocket/v0.1-alpha) to add it directly as a dependency to your Gradle or Maven project.

## Usage

In order to use *Rocket*, you must open a `LaunchPad`. A `LaunchPad` is an object from which you can launch `Rocket`s. To other computers, your `LaunchPad` is perceived as a `LandingZone`, because it is a potential landing zone for their `Rocket`s.

```java
// For clients
String hostname = "127.0.0.1"; // the server hostname
int port = 1234; // the server port
LaunchPad launchPad = LaunchPad.connect(hostname, port);


// For servers
int port = 1234; // the server port
LaunchPad launchPad = LaunchPad.serve(port);
```

You can listen for communication with other `LandingZone`s.

```java
launchPad.onCommunicationWithLandingZone(new LZCommunicationListener() {
	@Override
	public void onLZOnline(LaunchPad launchPad, LandingZone lz) {
		// Do something with this LandingZone.
	}
	@Override
	public void onLZOffline(LaunchPad launchPad, LandingZone lz) {
		// Stop doing things with this LandingZone.
	}
});
```
### Sending Data

To exchange information with a `LandingZone`, you must launch a `Rocket`. You can attach any `String`, `byte` Array, or primitive type with a `Rocket`.

```java
launchPad.prepareForLaunch("RocketTag").attach("key", "Value").launch(lz);
```

For larger transfers, you can also attach a `RocketStream`, which is `byte` data that will be transferred in chunks so it doesn't block other `Rocket`s from launching.

```java
// Convenience method for sending large files.
launchPad.prepareForLaunch().attach("file", 
	RocketStream.createStreamFromFile(new File("file.txt")))
	.launch(lz);

// Custom RocketStream
launchPad.prepareForLaunch("stream").attach("stream", 
	RocketStream.createStream(new StreamData() {
		@Override
		public ByteBuffer getBuffer() {
			// return the next buffer
			return ByteBuffer.allocate(length).put(data).flip();
		}

		@Override
		public boolean isFinished() {
			// return true if there are no more buffers to send.
			return true;
		}
	})).launch(lz);
```

### Receiving Data

To receive `Rocket`s that are sent to your `LaunchPad`, you must use a `LandingListener`.

```java
launchPad.onLanding(new LandingListener() {
	@Override
	public void onLanding(LaunchPad launchPad, Rocket rocket) {
		String text = rocket.getString("key");
	}
});
```

`Rocket`s can be sent with tags to help process messages with multiple `LandingListener`s.

```java
// Launching with a tag
launchPad.prepareForLaunch("this is my tag").attach("data", 124565).launch(lz);

// Receiving with a tag
launchPad.onLanding(new LandingListener() {
	@Override
	public void onLanding(LaunchPad launchPad, Rocket rocket) {
		int data = rocket.getInt("data");
	}
}, "this is my tag");
```

Larger data can be streamed in with a `RocketStream`.

```java
launchPad.onLanding(new LandingListener() {
	@Override
	public void onLanding(LaunchPad launchPad, Rocket rocket) {
		RocketStream stream = rocket.getRocketStream("stream");
		// let's write this stream to a file.
		try {
			final FileOutputStream fos = new FileOutputStream("data.txt");
			stream.openStream(new StreamReader() {
				@Override
				public void onStreamData(ByteBuffer data) {
					try {
						fos.write(data.array(), data.position(), data.remaining());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				@Override
				public void onStreamClosed() {
					try {
						fos.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
}, "stream");
```

