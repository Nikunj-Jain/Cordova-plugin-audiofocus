package com.thenikunj.cordova.plugins;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;

public class AudioFocus extends CordovaPlugin {

    private final static String TAG = "AudioFocus";

    private static final int MODE_IN_COMMUNICATION = 100;
    private static final int MODE_NORMAL = 101;
    private static final int MODE_RINGTONE = 102;

    private AudioManager mAudioManager;
    private onFocusChangeListener mFocusChangeListener;
    private AudioFocusRequest mAudioFocusRequest;
    private CallbackContext savedCallbackContext;
    private MediaPlayer mMediaPlayer;
    private int audioModeToSet = 0;

    @Override
    protected void pluginInitialize() {
        mAudioManager = (AudioManager)cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mFocusChangeListener = new onFocusChangeListener();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            AudioAttributes mAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();

            mAudioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(mAudioAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(mFocusChangeListener)
                    .build();
        }

        Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        mMediaPlayer = new MediaPlayer();
        try {mMediaPlayer.setDataSource(cordova.getContext(), alert);} catch (Exception e) {Log.e(TAG, e.getMessage());}

        if (mAudioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
            mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
            mMediaPlayer.setLooping(true);
        }
    }

    @Override
    public boolean execute(String action, final JSONArray args, CallbackContext callbackContext) {

        if (mAudioManager == null) {
            String errorMessage = "Audio manager out of memory";
            Log.e(TAG, errorMessage);
            callbackContext.sendPluginResult(new PluginResult(PluginResult.Status.ERROR, errorMessage));
            callbackContext.error(errorMessage);
            return true;
        }

        try { audioModeToSet = args.getInt(0); }
        catch (JSONException e) {}

        if (action.equals("requestFocus")) {
            savedCallbackContext = callbackContext;
            requestFocus();
            return true;
        } else if (action.equals("dumpFocus")) {
            savedCallbackContext = callbackContext;
            dumpFocus();
            return true;
        }

        return false;
    }

    private void requestFocus() {

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
                } else {
                    result = mAudioManager.requestAudioFocus(mFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
                }

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    String str = "Successfully received audio focus.";
                    Intent i = new Intent("com.android.music.musicservicecommand");

                    i.putExtra("command", "pause");
                    cordova.getActivity().getApplicationContext().sendBroadcast(i);
                    setAudioMode();
                    returnCallback(true, str);
                } else {
                    String str = "Getting audio focus failed.";
                    returnCallback(false, str);
                }
            }
        });

    }

    private void dumpFocus() {

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result = mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
                } else {
                    result = mAudioManager.abandonAudioFocus(mFocusChangeListener);
                }

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    String str = "Abandoned audio focus successfully.";
                    returnCallback(true, str);
                } else {
                    String str = "Abandoning audio focus failed.";
                    returnCallback(false, str);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        dumpFocus();
    }

    private void setAudioMode() {

        switch (audioModeToSet) {
            case MODE_IN_COMMUNICATION:
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.i(TAG, "setAudioMode: MODE_IN_COMMUNICATION");
                break;

            case MODE_RINGTONE:
                mAudioManager.setMode(AudioManager.MODE_RINGTONE);
                try {mMediaPlayer.prepare();} catch (Exception e) {Log.e(TAG, e.getMessage());}
                mMediaPlayer.start();
                Log.i(TAG, "setAudioMode: MODE_RINGTONE");
                break;

            case MODE_NORMAL:
                if (mMediaPlayer.isPlaying()) {
                    mMediaPlayer.stop();
                }
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                Log.i(TAG, "setAudioMode: MODE_NORMAL");
                break;

            default:
                break;
        }

        audioModeToSet = 0;
    }

    private void sendUpdate(String message, boolean success) {
        if (savedCallbackContext != null) {
            PluginResult result = new PluginResult(success ? PluginResult.Status.OK : PluginResult.Status.ERROR, message);
            result.setKeepCallback(true);
            savedCallbackContext.sendPluginResult(result);
        }
    }

    private void returnCallback(boolean success, String message) {

        if (savedCallbackContext == null) {
            return;
        }

        if (message == null) {
            message = "";
        }

        if (success) {
            Log.i(TAG, message);
            savedCallbackContext.success(message);
        } else {
            Log.e(TAG, message);
            savedCallbackContext.error(message);
        }
        savedCallbackContext = null;
    }

}

class onFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
    @Override
    public void onAudioFocusChange(int i) {
        // TODO: Implement this
    }
}