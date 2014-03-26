package com.nolnoch.beattothestep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.echonest.api.v4.EchoNestAPI;
import com.echonest.api.v4.EchoNestException;
import com.echonest.api.v4.Track;
import com.echonest.api.v4.TrackAnalysis;



public class PlayerActivity extends Activity {

	private static final int ONE_SECOND = 1000;
	private static final int DEFAULT_RATE = 1000;
	private static final int NUM_TIME_STEPS = 15;
	private static final int TIME_INTERVAL = 60 / NUM_TIME_STEPS * ONE_SECOND;
	private static final int SETTINGS_RC = 1080;
	private static final double MIN_SPM = 30.0d;
	private static final double MAX_SPM = 180.0d;
	private static final String DEBUG = "DEBUG";

	static final int CLIP_NONE = 0;

	static boolean isPlayingAsset = false;
	static boolean isPlayingUri = false;

	private ArrayList<Long> stepStart;
	private long stepCurrent, stepDelta;
	private int tick;
	static double spm, bpm;
	static int spm_old, spm_delta;
	
	private String enAPIKey = "AWC7MGBH4CCN9Z8QX"; 
	private static EchoNestAPI en;
	private TrackAnalysis trAnal;
	private AssetManager am;
	private String demo_song;

	private TimerTask stepTask;
	private Timer stepTimer;

	private SensorEventListener stepListener;
	private SensorManager sensorManager;
	private Sensor stepSensor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_player_main);

		stepStart = new ArrayList<Long>(NUM_TIME_STEPS);
		tick = 0;

		initEchoNestAPI();
		try {
			loadAudioAsset();
		} catch (EchoNestException e) {
			Log.d(DEBUG, "EchoNest Error: " + e);
		}
		createStepTimer();
		createStepSensor();
		
		// XXX Call this on PLAY.
		initStepEngine();
	}
	
	private void initEchoNestAPI() {
		en = new EchoNestAPI(enAPIKey);
	}
	
	private void loadAudioAsset() throws EchoNestException {
		am = getAssets();
		File song = new File(demo_song);
		
		try {
			Track tr = en.uploadTrack(song);
			tr.waitForAnalysis(ONE_SECOND * 10);
			if (tr.getStatus() == Track.AnalysisStatus.COMPLETE) {
				trAnal = tr.getAnalysis();
				bpm = trAnal.getTempo();
			} else {
				Log.d(DEBUG, "Track Status Error: " + tr.getStatus());
			}
		} catch (IOException e) {
			Log.d(DEBUG, "IO Error: " + e);
		}
	}

	private void createStepTimer() {
		stepTask = new TimerTask() {

			@Override
			public void run() {
				if (stepStart.get(tick) == 0) {
					stepStart.set(tick, stepCurrent);
				} else {
					int newRate;
					
					stepDelta = stepCurrent - stepStart.get(tick);
					spm = stepDelta / 60.0d;
					spm = Math.max(MIN_SPM, Math.min(MAX_SPM, spm));
					Log.d(DEBUG, "Current SPM: " + spm);
					
					newRate = (int) ((spm / bpm) * DEFAULT_RATE);
					
					setPlaybackRate(newRate);

					/* Until beat detection is working, use variable step rate.
					if (spm_old != 0) {
						spm_delta = (spm - spm_old) / spm * 1000 + 1000;
						setPlaybackRate(spm_delta);
					}
					spm_old = spm;
					*/
				}

				if (tick++ >= NUM_TIME_STEPS) {
					tick = 0;
				}
			}

		};

		stepTimer = new Timer("stepTimer", true);
	}

	private void createStepSensor() {
		stepListener = new StepListener();
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		stepSensor = (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
		assert(stepSensor == null);
		sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
	}

	private void initStepEngine() {
		stepTimer.scheduleAtFixedRate(stepTask, 0, TIME_INTERVAL);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.player_main, menu);
		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.playlists:
			startActivity(new Intent(this, PlaylistActivity.class));
			break;
		case R.id.action_settings:
			startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_RC);
			break;
		default:
			return false;
		}
		
		return true;
	}

	/* Called when the activity is about to be destroyed. */
	@Override
	protected void onPause()
	{
		// turn off all audio
		selectClip(CLIP_NONE, 0);
		isPlayingAsset = false;
		setPlayingAssetAudioPlayer(false);
		isPlayingUri = false;
		setPlayingUriAudioPlayer(false);

		stepTimer.cancel();
		stepTimer.purge();
		sensorManager.unregisterListener(stepListener);

		super.onPause();
	}

	/* Called when the activity is about to be destroyed. */
	@Override
	protected void onDestroy()
	{
		shutdown();

		stepTimer.cancel();
		stepTimer.purge();
		sensorManager.unregisterListener(stepListener);

		super.onDestroy();
	}


	private class StepListener implements SensorEventListener {

		@Override
		public void onSensorChanged(SensorEvent event) {
			stepCurrent = (long) event.values[0];
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	}


	/* Native methods, implemented in jni folder */

	// Basic functions from NDK sample.
	public static native void createEngine();
	public static native void createBufferQueueAudioPlayer();
	public static native boolean createAssetAudioPlayer(AssetManager assetManager, String filename);
	public static native void setPlayingAssetAudioPlayer(boolean isPlaying);
	public static native boolean createUriAudioPlayer(String uri);
	public static native void setPlayingUriAudioPlayer(boolean isPlaying);
	public static native void setLoopingUriAudioPlayer(boolean isLooping);
	public static native void setChannelMuteUriAudioPlayer(int chan, boolean mute);
	public static native void setChannelSoloUriAudioPlayer(int chan, boolean solo);
	public static native int getNumChannelsUriAudioPlayer();
	public static native void setVolumeUriAudioPlayer(int millibel);
	public static native void setMuteUriAudioPlayer(boolean mute);
	public static native void enableStereoPositionUriAudioPlayer(boolean enable);
	public static native void setStereoPositionUriAudioPlayer(int permille);
	public static native boolean selectClip(int which, int count);
	public static native void shutdown();

	// TODO Implement functions for time stretching.
	public static native int getPlaybackRate();
	public static native void setPlaybackRate(int rate);

	/** Load jni .so on initialization */
	static {
		System.loadLibrary("native-audio-jni");
	}

}
