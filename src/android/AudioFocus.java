package com.thenikunj.cordova.plugins;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;

import org.json.JSONArray;
import org.json.JSONException;

public class AudioFocus extends CordovaPlugin {

    private AudioManager mAudioManager;

    @Override
    protected void pluginInitialize() {
        mAudioManager = (AudioManager)this.cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {

        if (mAudioManager == null) {
            callbackContext.error("Audio manager out of memory");
        }

        if (action.equals("requestFocus")) {
            this.requestFocus(callbackContext);
            return true;
        } else if (action.equals("dumpFocus")) {
            this.dumpFocus(callbackContext);
            return true;
        }

        return false;
    }

    private void requestFocus(CallbackContext callbackContext) {

        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            AudioFocusRequest focusRequest =
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(
                                    new AudioManager.OnAudioFocusChangeListener() {
                                        @Override
                                        public void onAudioFocusChange(int i) { }
                                    })
                            .build();
            result = mAudioManager.requestAudioFocus(focusRequest);
        } else {
            result = mAudioManager.requestAudioFocus(null, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            callbackContext.success("Successfully received audio focus.");
        } else {
            callbackContext.error("Getting audio focus failed.");
        }
    }

    private void dumpFocus(CallbackContext callbackContext) {

        int result;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            result = mAudioManager.abandonAudioFocusRequest(
                    new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setOnAudioFocusChangeListener(focusChangeListener)
                            .build());
        } else {
            result = mAudioManager.abandonAudioFocus(focusChangeListener);
        }

        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            callbackContext.success("Abandoned audio focus successfully.");
        } else {
            callbackContext.error("Abandoning audio focus failed.");
        }
    }
}
