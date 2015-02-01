package com.jwatts.rocket.sample;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import com.jwatts.rocket.LandingZone;
import com.jwatts.rocket.LaunchPad;
import com.jwatts.rocket.LaunchPad.LZCommunicationListener;
import com.jwatts.rocket.LaunchPad.LandingListener;
import com.jwatts.rocket.Rocket;
import com.jwatts.rocket.RocketStream;
import com.jwatts.rocket.RocketStream.StreamData;

public class SampleClient {
	public static void main(String[] args) {
		LaunchPad.connect("127.0.0.1", 1234).onLanding(new LandingListener() {
			@Override
			public void onLanding(LaunchPad launchPad, Rocket rocket) {
				System.out.println("Result is " + rocket.getInt("result"));
			}
		}).onCommunicationWithLandingZone(new LZCommunicationListener() {
			@Override
			public void onLZOnline(LaunchPad launchPad, LandingZone zone) {
				/* Read in files */
				final InputStream reader = new BufferedInputStream(SampleClient.class.getResourceAsStream("IMG_0009.JPG"));
				final ByteArrayOutputStream baos = new ByteArrayOutputStream();
				byte[] buffer = new byte[8192];
				int len = 0;
				try {
					while ((len = reader.read(buffer)) != -1) {
						baos.write(buffer, 0, len);
					}
					buffer = null;
				} catch (IOException e1) {
					e1.printStackTrace();
				}
				RocketStream stream = RocketStream.createStream(new StreamData() {
					@Override
					public boolean isFinished() {
						return true;
					}
					
					@Override
					public ByteBuffer getBuffer() {
						return ByteBuffer.wrap(baos.toByteArray());
					}
				});
				launchPad.prepareForLaunch("sendfile").attach("file", stream).attach("dest", "IMG.JPG").launch(zone);
				launchPad.prepareForLaunch("factorial").attach("factorial", 5)
						.launch(zone);
				StringBuffer s = new StringBuffer();
				for (int j = 1; j <= 50; j++) {
					s.append(j + " ");
				}
				launchPad.prepareForLaunch("println")
						.attach("text", s.toString()).launch(zone);
			}

			@Override
			public void onLZOffline(final LaunchPad launchPad, LandingZone zone) {
				if (launchPad.getAvailableLandingZones().isEmpty()) {
					new Thread(new Runnable() {
						@Override
						public void run() {
							launchPad.shutdown();
						}
					}).start();
				}
			}
		});

	}
}
