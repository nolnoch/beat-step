package com.nolnoch.beattothestep;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
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
	private static final int SELECT_RC = 2020;
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
	public static String ap_song;
	public static String sTitle, sArtist, sArt;


	//private TimerTask stepTask;
	//private Timer stepTimer;
	private ProgressDialog trAnalyzing;
	private SeekBar stepPos;
	private Context context;

	//private SensorEventListener stepListener;
	//private SensorManager sensorManager;
	//private Sensor stepSensor;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_player_main);
		context = getApplicationContext();

		stepStart = new ArrayList<Long>(NUM_TIME_STEPS);
		tick = 0;

		initEchoNestAPI();

		/*
		stepCounting = createStepSensor();
		if (stepCounting) {
			createStepTimer();
			Log.d(DEBUG, "Step Counter loaded.");
		}
		 */

		initUIControls();
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
		case R.id.action_settings:
			startActivityForResult(new Intent(this, SettingsActivity.class), SETTINGS_RC);
			break;
		default:
			return false;
		}

		return true;
	}

	private void initEchoNestAPI() {
		en = new EchoNestAPI(enAPIKey);
	}

	private void loadAudioAsset(String path) {
		File song = new File(path);

		// Display analysis wait animation.
		trAnalyzing = ProgressDialog.show(this, "", "Analyzing track...", true, false);

		// Submit the song for analysis.
		AnalyzeTrackTask att = new AnalyzeTrackTask();
		att.execute(song);
	}

	private void postAnalysis() {
		bpm = trAnalysis.getTempo();
		spm = bpm;
		
		stepPos.setProgress(50);

		TextView tv = (TextView) findViewById(R.id.bpm_count);
		tv.setText("BPM: " + Integer.toString((int) bpm));
		tv = (TextView) findViewById(R.id.spm_count);
		tv.setText("SPM: " + Integer.toString((int) spm));
		tv = (TextView) findViewById(R.id.song_title);
		tv.setText(sTitle);
		tv = (TextView) findViewById(R.id.artist_title);
		tv.setText(sArtist);

		ImageView iv = (ImageView) findViewById(R.id.album_art);
		Drawable albumArt = Drawable.createFromPath(sArt);
		iv.setImageDrawable(albumArt);

		if (apCreated) {
			shutdown();
		}
		
		createEngine();
		createBufferQueueAudioPlayer();
		apCreated = createDecoderPlayer(ap_song);
		
		if (!apCreated)
			Log.e(DEBUG, "DecoderPlayer failed.");
		else {
			Log.d(DEBUG, "DecoderPlayer created.");
			getPlaybackRate();
			setDecodingDecoderPlayer();
		}
	}

	/*
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

				if (!isPlayingUri && tick == 1) {
					playMusic(null);
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

	
	private void initStepEngine() {
		stepTimer.scheduleAtFixedRate(stepTask, 0, TIME_INTERVAL);
	}
	*/

	public void launchSelectSong(View v) {
		if (isPlayingUri) {
			isPlayingUri = false;
	
			ImageButton playPause = (ImageButton) findViewById(R.id.play_pause);
			playPause.setBackgroundResource(R.drawable.media_play);
	
			setPlayingBufferedQueueAudioPlayer(isPlayingUri);
		}
	
		startActivityForResult(new Intent(this, SongSelectActivity.class), SELECT_RC);
	}

	public void playMusic(View v) {
		ImageButton playPause = (ImageButton) findViewById(R.id.play_pause);

		if (spm != 0 && apCreated) {
			isPlayingUri = !isPlayingUri;

			if (isPlayingUri) {
				playPause.setBackgroundResource(R.drawable.media_pause);
			} else {
				playPause.setBackgroundResource(R.drawable.media_play);
			}

			setPlayingBufferedQueueAudioPlayer(isPlayingUri);
		} else {
			Toast.makeText(
					getApplicationContext(),
					"No track loaded to play.",
					Toast.LENGTH_SHORT
					).show();
		}
	}

	public void resetSong(View v) {
		if (apCreated)
			setResetBufferedQueueAudioPlayer();
	}

	public void seekSong(View v) {
		if (apCreated)
			setSeekBufferedQueueAudioPlayer();
	}

	public void initUIControls() {
		// If step counter is not working, use slider to control SPM manually.
		stepPos = (SeekBar) findViewById(R.id.rate_seekbar);
		if (!stepCounting) {
			spm_old = spm;
			stepPos.setVisibility(View.VISIBLE);
			stepPos.setMax(100);
			stepPos.setProgress(50);
			stepPos.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {
				int lastProgress = 50;
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					assert (progress >= 0 && progress <= 100);
					lastProgress = progress;
				}
				public void onStartTrackingTouch(SeekBar seekBar) {
				}
				public void onStopTrackingTouch(SeekBar seekBar) {
					int rate;
					double power, product;

					// Get new spm from slider value in scale (0.5*bpm, 2*bpm)
					power = ((double) lastProgress) / 50.0d;
					spm = power * bpm;
					TextView tv = (TextView) findViewById(R.id.spm_count);
					tv.setText("SPM: " + Integer.toString((int) spm));

					// Get integer rate in scale (500, 2000)
					product = Math.pow(2.0d, power) * 500.0d;
					rate = (int) product;					

					Log.d(DEBUG, "Setting playback rate to: " + rate);
					setPlaybackRate(rate);
				}
			});
		} else {
			stepPos.setVisibility(View.GONE);
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == SELECT_RC) {
			if (resultCode == RESULT_OK) {
				ap_song = data.getDataString();
				sTitle = data.getStringExtra("title");
				sArtist = data.getStringExtra("artist");
				sArt = data.getStringExtra("art");
				loadAudioAsset(ap_song);
				Log.d(DEBUG, ap_song);
			}
		}
	}

	@Override
	protected void onResume() {
		
		/*
		stepCounting = createStepSensor();
		if (stepCounting)
			createStepTimer();
		else {
			// custom algorithm to detect step? (ugh)
		}
		*/

		super.onResume();
	}

	@Override
	protected void onPause()
	{
		// turn off all audio
		if (isPlayingAsset) {
			isPlayingAsset = false;
			setPlayingAssetAudioPlayer(false);
		}
		if (isPlayingUri) {
			isPlayingUri = false;
			setPlayingUriAudioPlayer(false);
		}


		/*
		if (stepCounting) {
			stepTimer.cancel();
			stepTimer.purge();
			sensorManager.unregisterListener(stepListener);
		}
		*/

		super.onPause();
	}

	@Override
	protected void onDestroy()
	{
		shutdown();
		super.onDestroy();
	}


	/*
	private class StepListener implements SensorEventListener {

		@Override
		public void onSensorChanged(SensorEvent event) {
			stepCurrent = (long) event.values[0];
		}

		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {}

	}
	*/

	private class AnalyzeTrackTask extends AsyncTask<File, Integer, Boolean> {

		@Override
		protected Boolean doInBackground(File... params) {
			Boolean ret = false;

			try {
				try {
					Track tr = en.uploadTrack(params[0], true);
					tr.waitForAnalysis(ONE_SECOND * 30);
					if (tr.getStatus() == Track.AnalysisStatus.COMPLETE) {
						trAnalysis = tr.getAnalysis();
						Log.d(DEBUG, "Analysis complete.");
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
			trAnalyzing.dismiss();
			trAnalyzed = result;

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
	public static native void setPlayingBufferedQueueAudioPlayer(boolean isPlaying);
	public static native void setResetBufferedQueueAudioPlayer();
	public static native void setSeekBufferedQueueAudioPlayer();

	public static native boolean createUriAudioPlayer(String uri);
	public static native void setPlayingUriAudioPlayer(boolean isPlaying);

	public static native boolean createDecoderPlayer(String uri);
	public static native void setDecodingDecoderPlayer();

	public static native void setLoopingUriAudioPlayer(boolean isLooping);
	public static native void setChannelMuteUriAudioPlayer(int chan, boolean mute);
	public static native void setChannelSoloUriAudioPlayer(int chan, boolean solo);
	public static native int getNumChannelsUriAudioPlayer();
	public static native void setVolumeUriAudioPlayer(int millibel);
	public static native void setMuteUriAudioPlayer(boolean mute);

	public static native boolean createAssetAudioPlayer(AssetManager assetManager, String filename);
	public static native void setPlayingAssetAudioPlayer(boolean isPlaying);

	public static native void shutdown();

	public static native int getPlaybackRate();
	public static native void setPlaybackRate(int rate);

	/** Load jni .so on initialization */
	static {
		System.loadLibrary("beat-step");
	}

}
