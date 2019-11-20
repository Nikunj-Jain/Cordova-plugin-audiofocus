package com.thenikunj.cordova.plugins;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
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

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

public class AudioFocus extends CordovaPlugin {

    private final static String TAG = "AudioFocus";

    private static final int MODE_IN_COMMUNICATION = 100;
    private static final int MODE_NORMAL = 101;
    private static final int MODE_RINGTONE = 102;
    private static final String NOTIFICATION_CHANNEL_ID = TAG + ":NotificationChannel";
    private static final int INCOMING_CALL_NOTIFICATION_ID = 1001;

    private AudioManager mAudioManager;

    private onFocusChangeListener mFocusChangeListener;
    private AudioFocusRequest mAudioFocusRequest;
    private CallbackContext savedCallbackContext;
    private MediaPlayer mMediaPlayer;
    private int audioModeToSet = 0;

    private boolean isAppInForeground = false;

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
                    .setOnAudioFocusChangeListener(mFocusChangeListener)
                    .build();


            CharSequence name = "Incoming calls";
            String description = "Show incoming calls notification";
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_MAX);
            channel.setSound(null, null);
            channel.setDescription(description);
            NotificationManager notificationManager = (NotificationManager)cordova.getActivity().getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(channel);
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
        } else if (action.equals("showCallNotification")) {
            showNotification(args);
            return true;
        } else if (action.equals("dismissCallNotification")) {
            dismissNotification();
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

                releaseMediaPlayer();

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
    public void onResume(boolean multitasking) {
        isAppInForeground = true;
    }

    @Override
    public void onPause(boolean multitasking) {
        isAppInForeground = false;
    }

    @Override
    public void onDestroy() {
        dumpFocus();
        releaseMediaPlayer();
    }

    private void setUpMediaPlayer() {

        if (mMediaPlayer == null) {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            mMediaPlayer = new MediaPlayer();
            try {mMediaPlayer.setDataSource(cordova.getContext(), alert);} catch (Exception e) {Log.e(TAG, e.getMessage());}

            if (mAudioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                mMediaPlayer.setLooping(true);
            }
        }

    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void bringAppToFront() {
        if (isAppInForeground) {
            return;
        }
//        Intent intent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
//        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        cordova.getContext().startActivity(intent);
    }

    private void setAudioMode() {

        switch (audioModeToSet) {
            case MODE_IN_COMMUNICATION:
                releaseMediaPlayer();
                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                Log.i(TAG, "setAudioMode: MODE_IN_COMMUNICATION successful");
                break;

            case MODE_RINGTONE:
                mAudioManager.setMode(AudioManager.MODE_RINGTONE);

                if (mMediaPlayer != null) {
                    if (mMediaPlayer.isPlaying()) {
                        mMediaPlayer.stop();
                        mMediaPlayer.start();
                    }
                } else {
                    setUpMediaPlayer();
                }

                if (!mMediaPlayer.isPlaying()) {
                    try {
                        mMediaPlayer.prepare();
                        mMediaPlayer.start();
                    } catch (Exception e) {
                        Log.e(TAG, e.getMessage());
                        break;
                    }
                }

                bringAppToFront();

                Log.i(TAG, "setAudioMode: MODE_RINGTONE successful");
                break;

            case MODE_NORMAL:
                releaseMediaPlayer();
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
                Log.i(TAG, "setAudioMode: MODE_NORMAL successful");
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

    private void showNotification(JSONArray args) {

        String from = null;
        Log.i(TAG, "showNotification: " + args.toString());
        try {
            from = args.getString(0);
        } catch (JSONException e) {
            Log.i(TAG, "showNotification: no from name provided. Using default.");
        }

        if (from == null || from.equals("") || from.equals("null") || from.equals("nil")) {
            from = "Incoming call";
        }

        Intent fullScreenIntent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(cordova.getContext(), 0, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT);


        NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getContext(), NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(androidx.appcompat.R.drawable.notification_icon_background)
                .setContentTitle(from)
                .setContentText("Incoming voice call")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setFullScreenIntent(fullScreenPendingIntent, true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(cordova.getContext());
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build());

    }

    private void dismissNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(cordova.getContext());
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
    }

}

class onFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
    @Override
    public void onAudioFocusChange(int i) {
        // TODO: Implement this
    }
}