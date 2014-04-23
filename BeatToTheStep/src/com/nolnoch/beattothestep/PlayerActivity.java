package com.nolnoch.beattothestep;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

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
	private static final int CLIP_NONE = 0;
	private static final double MIN_SPM = 30.0d;
	private static final double MAX_SPM = 180.0d;
	private static final String DEBUG = "DEBUG";

	static boolean isPlayingAsset = false;
	static boolean isPlayingUri = false;
	private boolean stepCounting = false;
	private boolean apCreated = false;
	private Boolean trAnalyzed = false;


	private ArrayList<Long> stepStart;
	private long stepCurrent, stepDelta;
	private int tick;
	static double spm, bpm;
	static double spm_old, spm_delta;

	private static EchoNestAPI en;
	private String enAPIKey = "AWC7MGBH4CCN9Z8QX"; 
	private TrackAnalysis trAnalysis;
	public static AssetManager am;
	public static String demo_song = "une_seule_asset.mp3";

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
		
		setupDemoTrack();

		createEngine();
		createBufferQueueAudioPlayer();

		initEchoNestAPI();
		loadAudioAsset();

		stepCounting = createStepSensor();
		if (stepCounting)
			createStepTimer();
		else {
			// custom algorithm to detect step? (ugh)
		}

		// initUIControls();
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
	
	private void setupDemoTrack() {
		TextView tv;
		
		tv = (TextView) findViewById(R.id.song_title);
		tv.setText("Une Seule Vie");
		tv = (TextView) findViewById(R.id.artist_title);
		tv.setText("De Palmas");
	}

	private void initEchoNestAPI() {
		en = new EchoNestAPI(enAPIKey);
	}

	private void loadAudioAsset() {
		// Unpack the asset track to a temporary file.
		File song = new File(getCacheDir()+ "/" + demo_song);
		if (!song.exists()) try {

			InputStream is = getAssets().open(demo_song);
			int size = is.available();
			byte[] buffer = new byte[size];
			is.read(buffer);
			is.close();


			FileOutputStream fos = new FileOutputStream(song);
			fos.write(buffer);
			fos.close();
		} catch (Exception e) { throw new RuntimeException(e); }

		// Submit the song for analysis.
		AnalyzeTrackTask att = new AnalyzeTrackTask();
		att.execute(song);
		try {
			trAnalyzed = att.get();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// Display result.
		if (trAnalyzed) {
			// stuff
		}
	}

	private void postAnalysis() {
		bpm = trAnalysis.getTempo();
		spm = bpm;
		
		TextView tv = (TextView) findViewById(R.id.bpm_count);
		tv.setText("" + bpm);
		tv = (TextView) findViewById(R.id.spm_count);
		tv.setText("" + spm);
	}

	private void createStepTimer() {
		stepTask = new TimerTask() {

			@Override
			public void run() {
				Log.d(DEBUG, "Steps: " + stepCurrent);

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
				}

				if (tick++ >= NUM_TIME_STEPS) {
					tick = 0;
				}

				if (!isPlayingAsset && tick == 1) {
					playDemoAsset();
				}
			}

		};

		stepTimer = new Timer("stepTimer", true);
	}

	private boolean createStepSensor() {
		stepListener = new StepListener();
		sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		stepSensor = (Sensor) sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);

		if (stepSensor != null)
			return sensorManager.registerListener(stepListener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
		else
			return false;
	}
	
	private void updateSPMandPlayback() {
		// Until beat detection is working, use variable step rate.
		if (spm_old != 0) {
			spm_delta = ((spm - spm_old) / spm_old * 1000) + 1000;
			setPlaybackRate((int) spm_delta);
		}
		spm_old = spm;
	}

	private void initStepEngine() {
		stepTimer.scheduleAtFixedRate(stepTask, 0, TIME_INTERVAL);
	}

	private void playDemoAsset() {
		if (!apCreated) {
			am = getAssets();
			apCreated = createAssetAudioPlayer(am, demo_song);
			Log.d(DEBUG, "AssetPlayer created.");
		}

		setPlayingAssetAudioPlayer(isPlayingAsset);
	}

	public void countButton(View v) {
		initStepEngine();
	}

	public void playMusic(View v) {
		ImageButton playPause = (ImageButton) findViewById(R.id.play_pause);

		isPlayingAsset = !isPlayingAsset;

		if (isPlayingAsset) {
			playPause.setImageResource(R.drawable.media_pause);
		} else {
			playPause.setImageResource(R.drawable.media_play);
		}

		playDemoAsset();
	}

	public void initUIControls() {
		// Enable seeking through track.
		SeekBar trackPos = (SeekBar) findViewById(R.id.song_seekbar);
		trackPos.setMax(100);
		trackPos.setProgress(0);
		trackPos.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
			int lastProgress = 100;
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				assert (progress >= 0 && progress <= 100);
				lastProgress = progress;
			}
			public void onStartTrackingTouch(SeekBar seekBar) {
			}
			public void onStopTrackingTouch(SeekBar seekBar) {
				int permille = (lastProgress - 50) * 20;
				setStereoPositionUriAudioPlayer(permille);
			}
		});
		
		// If step counter is not working, use slider to control SPM manually.
		SeekBar stepPos = (SeekBar) findViewById(R.id.rate_seekbar);
		if (!stepCounting) {
			stepPos.setVisibility(View.VISIBLE);
			stepPos.setMax(100);
			stepPos.setProgress(50);
			stepPos.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				int lastProgress = 50;
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					assert (progress >= 0 && progress <= 100);
					spm = ((double) progress) / 50.0d * bpm; 
					lastProgress = progress;
					updateSPMandPlayback();
				}
				public void onStartTrackingTouch(SeekBar seekBar) {
				}
				public void onStopTrackingTouch(SeekBar seekBar) {
					int permille = (lastProgress - 50) * 20;
					setStereoPositionUriAudioPlayer(permille);
				}
			});
		} else {
			stepPos.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onResume() {
		stepCounting = createStepSensor();
		if (stepCounting)
			createStepTimer();
		else {
			// custom algorithm to detect step? (ugh)
		}

		super.onResume();
	}

	@Override
	protected void onPause()
	{
		// turn off all audio
		selectClip(CLIP_NONE, 0);
		if (isPlayingAsset) {
			isPlayingAsset = false;
			setPlayingAssetAudioPlayer(false);
		}
		if (isPlayingUri) {
			isPlayingUri = false;
			setPlayingUriAudioPlayer(false);
		}


		if (stepCounting) {
			stepTimer.cancel();
			stepTimer.purge();
			sensorManager.unregisterListener(stepListener);
		}

		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		shutdown();
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

	private class AnalyzeTrackTask extends AsyncTask<File, Integer, Boolean> {

		@Override
		protected Boolean doInBackground(File... params) {
			Boolean ret = false;

			try {
				try {
					Track tr = en.uploadTrack(params[0], true);
					tr.waitForAnalysis(ONE_SECOND * 10);
					if (tr.getStatus() == Track.AnalysisStatus.COMPLETE) {
						trAnalysis = tr.getAnalysis();
						Log.d(DEBUG, "Track BPM: " + bpm);
						ret = true;
					} else {
						Log.d(DEBUG, "Track Status Error: " + tr.getStatus());
					}
				} catch (IOException e) {
					Log.d(DEBUG, "IO Error: " + e);
				}
			} catch (EchoNestException e) {
				Log.d(DEBUG, "EchoNest Error: " + e);
			}
			return ret;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				postAnalysis();
			} else {
				Toast.makeText(
						getApplicationContext(),
						"Error analyzing track.",
						Toast.LENGTH_SHORT
						).show();
			}
		}
	}


	/*
	 *  Native methods, implemented in jni folder
	 */

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
		System.loadLibrary("beat-step");
	}

}
