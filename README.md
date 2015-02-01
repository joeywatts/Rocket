# Rocket
==============

A simple Java socket library that wraps the complex non-blocking Java Socket API.

## Building
=================

*Rocket* uses Gradle, and is completely self-contained. Just clone the repo, and run `gradle jar`.

## Installation
=====================

[Download]() or build the *Rocket* jar file, and link it with your project.

## Usage
==============

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
launchPad.prepareForLaunch().attach("file", RocketStream.createStreamFromFile(new File("file.txt"))).launch(lz);
```

### Receiving Data

To receive `Rocket`s that are sent to your `LaunchPad`, you must use a `LandingListener`.

```java
launchPad.onLanding(new LandingListener() {
	@Override
	public void onLanding(LaunchPad launchPad, Rocket rocket) {
		String text = rocket.getString(text);
	}
});
```

`Rocket`s can be sent with tags to help process messages with multiple `LandingListener`s.

```
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

