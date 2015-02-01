package com.jwatts.rocket.sample;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import com.jwatts.rocket.LaunchPad;
import com.jwatts.rocket.LaunchPad.ErrorListener;
import com.jwatts.rocket.LaunchPad.LandingListener;
import com.jwatts.rocket.Rocket;
import com.jwatts.rocket.RocketStream;
import com.jwatts.rocket.RocketStream.StreamReader;

public class SampleServer {
	public static void main(String[] args) {
		LaunchPad.serve(1234).onError(new ErrorListener() {
			@Override
			public void onError(LaunchPad launchPad, Exception e) {
				e.printStackTrace();
			}
		}).onLanding(new LandingListener() {
			
			@Override
			public void onLanding(LaunchPad launchPad, Rocket rocket) {
				launchPad.prepareForLaunch("factorial").attach("result", factorial(rocket.getInt("factorial"))).launch(rocket.getOrigin());
			}
		}, "factorial").onLanding(new LandingListener() {
			@Override
			public void onLanding(LaunchPad launchPad, Rocket rocket) {
				RocketStream stream = rocket.getRocketStream("file");
				final String dest = rocket.getString("dest");
				try {
					stream.openStream(new StreamReader() {
						FileOutputStream fos = new FileOutputStream(dest);
						
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
		}, "sendfile");
	}
	
	public static int factorial(int x) {
		if (x < 0)
			return Integer.MIN_VALUE;
		if (x == 0) {
			return 1;
		}
		return x * factorial(x-1);
	}
}
