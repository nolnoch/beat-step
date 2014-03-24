package com.example.beattothestep;

import android.os.Bundle;
import android.app.Activity;
import android.content.res.AssetManager;
import android.view.Menu;

public class PlayerActivity extends Activity {
	
	static final int CLIP_NONE = 0;
	
	static boolean isPlayingAsset = false;
    static boolean isPlayingUri = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_player_main);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.player_main, menu);
		return true;
	}

	/** Called when the activity is about to be destroyed. */
    @Override
    protected void onPause()
    {
        // turn off all audio
        selectClip(CLIP_NONE, 0);
        isPlayingAsset = false;
        setPlayingAssetAudioPlayer(false);
        isPlayingUri = false;
        setPlayingUriAudioPlayer(false);
        super.onPause();
    }

    /** Called when the activity is about to be destroyed. */
    @Override
    protected void onDestroy()
    {
        shutdown();
        super.onDestroy();
    }

    /** Native methods, implemented in jni folder */
    
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
    
    // TODO Added functions for time stretching.

    /** Load jni .so on initialization */
    static {
         System.loadLibrary("native-audio-jni");
    }
    
}
