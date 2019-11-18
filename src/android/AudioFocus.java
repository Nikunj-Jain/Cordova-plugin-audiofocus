package com.thenikunj.cordova.plugins;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.util.Log;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

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

    @Override
    protected void pluginInitialize() {
        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
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
            }
        });
    }

    @Override
    public boolean execute(String action, final JSONArray args, CallbackContext callbackContext) {

        if (mAudioManager == null) {
            String errorMessage = "Audio manager out of memory";
            Log.e(TAG, errorMessage);
            callbackContext.error(errorMessage);
            return true;
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                int audioMode = 0;
                try { audioMode = args.getInt(0); }
                catch (JSONException e) {}
                setAudioMode(audioMode);
            }
        });

        if (action.equals("requestFocus")) {
            requestFocus(callbackContext);
            return true;
        } else if (action.equals("dumpFocus")) {
            dumpFocus(callbackContext);
            return true;
        }

        return false;
    }

    private void requestFocus(final CallbackContext callbackContext) {

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result = mAudioManager.requestAudioFocus(mAudioFocusRequest);
                } else {
                    result = mAudioManager.requestAudioFocus(mFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }

                if (callbackContext == null) {
                    return;
                }

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    String str = "Successfully received audio focus.";
                    Log.i(TAG, str);
                    callbackContext.success(str);
                } else {
                    String str = "Getting audio focus failed.";
                    Log.e(TAG, str);
                    callbackContext.error(str);
                }
            }
        });

    }

    private void dumpFocus(final CallbackContext callbackContext) {

        cordova.getActivity().runOnUiThread(new Runnable() {
            public void run() {

                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    result = mAudioManager.abandonAudioFocusRequest(mAudioFocusRequest);
                } else {
                    result = mAudioManager.abandonAudioFocus(mFocusChangeListener);
                }

                if (callbackContext == null) {
                    return;
                }

                if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    String str = "Abandoned audio focus successfully.";
                    Log.i(TAG, str);
                    callbackContext.success(str);
                } else {
                    String str = "Abandoning audio focus failed.";
                    Log.e(TAG, str);
                    callbackContext.error(str);
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        dumpFocus(null);
    }

    private void setAudioMode(int audioMode) {

        switch (audioMode) {
            case MODE_IN_COMMUNICATION:
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mAudioManager.setSpeakerphoneOn(false);
                Log.i(TAG, "setAudioMode: MODE_IN_COMMUNICATION");
                break;

            case MODE_RINGTONE:
                mAudioManager.setMode(AudioManager.MODE_RINGTONE);
                mAudioManager.setSpeakerphoneOn(true);
                Log.i(TAG, "setAudioMode: MODE_RINGTONE");
                break;

            case MODE_NORMAL:
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                mAudioManager.setSpeakerphoneOn(true);
                Log.i(TAG, "setAudioMode: MODE_RINGTONE");
                break;

            default:
                break;
        }
    }

}

class onFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
    @Override
    public void onAudioFocusChange(int i) {
        // TODO: Implement this
    }
}