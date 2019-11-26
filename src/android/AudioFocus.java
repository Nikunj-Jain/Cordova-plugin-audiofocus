package com.thenikunj.cordova.plugins;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;

public class AudioFocus extends CordovaPlugin {

    private final static String TAG = "AudioFocus";

    private static final int AUDIO_REQUEST_TYPE_RING = 200;
    private static final int AUDIO_REQUEST_TYPE_COMMUNICATION = 201;

    private static final int MEDIA_PLAYER_MODE_OUTGOING_RING = 1000;
    private static final int MEDIA_PLAYER_MODE_INCOMING_RING = 1001;

    private static final String NOTIFICATION_CHANNEL_ID = TAG + ":NotificationChannel";
    private static final int INCOMING_CALL_NOTIFICATION_ID = 1001;

    private final static String WAKE_LOCK_TAG = TAG + ":proximityWakeLockTag";

    private PowerManager mPowerManager;
    private PowerManager.WakeLock mWakeLock;

    private AudioManager mAudioManager;
    private onFocusChangeListener mFocusChangeListener;
    private CallbackContext savedCallbackContext;
    private MediaPlayer mMediaPlayer;

    private AudioFocusRequest mActiveAudioFocusRequest;

    private boolean isAppInForeground = false;
    private boolean isScreenTurnOnEnabled = false;

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
        deactivateProximitySensor();
        mAudioManager.setMode(AudioManager.MODE_NORMAL);
        dumpFocus();
    }

    @Override
    protected void pluginInitialize() {

        mPowerManager = (PowerManager) cordova.getActivity().getApplicationContext().getSystemService(Context.POWER_SERVICE);

        mAudioManager = (AudioManager)cordova.getActivity().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        mFocusChangeListener = new onFocusChangeListener();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Incoming calls";
            String description = "Show incoming calls notification";
            NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, name, NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableVibration(false);
            channel.enableLights(true);
            channel.setSound(null, null);
            channel.setDescription(description);
            channel.setShowBadge(false);
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

        if (action.equals("setModeInCommunication")) {
            savedCallbackContext = callbackContext;
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    setModeInCommunication();
                }
            });
            return true;
        } else if (action.equals("playOutgoingRing")) {
            savedCallbackContext = callbackContext;
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    playOutgoingRing();
                }
            });
            return true;
        } else if (action.equals("playIncomingRing")) {
            savedCallbackContext = callbackContext;
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    playIncomingRing();
                }
            });
            return true;
        } else if (action.equals("dumpFocus")) {
            toggleScreenTurnOnAndShowWhenLocked(false);
            deactivateProximitySensor();
            cordova.getThreadPool().execute(new Runnable() {
                @Override
                public void run() {
                    dumpFocus();
                }
            });
            return true;
        }

        return false;
    }

    private void playOutgoingRing() {

        dumpFocus();

        if (!setUpMediaPlayer(MEDIA_PLAYER_MODE_OUTGOING_RING)) {
            Log.e(TAG, "playOutgoingRing: Unable to setup media player");
            returnCallback(false, "Unable to setup media player");
            return;
        }

        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {

            Log.e(TAG, "playOutgoingRing: ", e.getCause());
            returnCallback(false, "Unable to start playing media. Exception occurred: " + e.getLocalizedMessage());
            return;
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mMediaPlayer == null) {
                    Log.e(TAG, "run playOutgoingRing: mMediaPlayer is null");
                    returnCallback(false, "Unable to play media");
                    return;
                }

                int result;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioFocusRequest audioFocusRequest = createAudioRequest(AUDIO_REQUEST_TYPE_COMMUNICATION);
                    result = mAudioManager.requestAudioFocus(audioFocusRequest);
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        mActiveAudioFocusRequest = audioFocusRequest;
                    }
                } else {
                    result = mAudioManager.requestAudioFocus(mFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
                }

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.e(TAG, "run playOutgoingRing: Failed to get audio focus");
                    returnCallback(false, "Failed to get audio focus");
                    return;
                }

                activateProximitySensor();
                mAudioManager.setSpeakerphoneOn(false);
                mMediaPlayer.start();

                if (!isScreenTurnOnEnabled) {
                    toggleScreenTurnOnAndShowWhenLocked(true);
                }

                returnCallback(true, "Successfully started playing outgoing ring");
            }
        });
    }

    private void playIncomingRing() {

        dumpFocus();

        if (!setUpMediaPlayer(MEDIA_PLAYER_MODE_INCOMING_RING)) {
            Log.e(TAG, "playIncomingRing: Unable to setup media player");
            returnCallback(false, "Unable to setup media player");
            return;
        }

        try {
            mMediaPlayer.prepare();
        } catch (Exception e) {
            Log.e(TAG, "playIncomingRing: ", e.getCause());
            returnCallback(false, "Unable to start playing media. Exception occurred: " + e.getLocalizedMessage());
            return;
        }

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (mMediaPlayer == null) {
                    Log.e(TAG, "run playIncomingRing: mMediaPlayer is null");
                    returnCallback(false, "Unable to play media");
                    return;
                }

                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioFocusRequest audioFocusRequest = createAudioRequest(AUDIO_REQUEST_TYPE_RING);
                    result = mAudioManager.requestAudioFocus(audioFocusRequest);
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        mActiveAudioFocusRequest = audioFocusRequest;
                    }
                } else {
                    result = mAudioManager.requestAudioFocus(mFocusChangeListener, AudioManager.STREAM_RING, AudioManager.AUDIOFOCUS_GAIN);
                }

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Log.e(TAG, "run playIncomingRing: Failed to get audio focus");
                    returnCallback(false, "Failed to get audio focus");
                    return;
                }

                mAudioManager.setSpeakerphoneOn(true);
                mMediaPlayer.start();

                if (!isScreenTurnOnEnabled) {
                    toggleScreenTurnOnAndShowWhenLocked(true);
                }
                bringAppToFront();

                returnCallback(true, "Successfully started playing incoming ring");
            }
        });

    }

    private void setModeInCommunication() {

        dumpFocus();

        cordova.getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                int result;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioFocusRequest audioFocusRequest = createAudioRequest(AUDIO_REQUEST_TYPE_COMMUNICATION);
                    result = mAudioManager.requestAudioFocus(audioFocusRequest);
                    if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                        mActiveAudioFocusRequest = audioFocusRequest;
                    }
                } else {
                    result = mAudioManager.requestAudioFocus(mFocusChangeListener, AudioManager.STREAM_VOICE_CALL, AudioManager.AUDIOFOCUS_GAIN);
                }

                if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    returnCallback(false, "Failed to get audio focus");
                    return;
                }

                mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
                mAudioManager.setSpeakerphoneOn(false);
                returnCallback(true, "Successfully set mode");
            }
        });
    }

    private void toggleScreenTurnOnAndShowWhenLocked(boolean enabled) {

        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.O_MR1) {
            Activity parentActivity = cordova.getActivity();
            parentActivity.setTurnScreenOn(enabled);
            parentActivity.setShowWhenLocked(enabled);
        } else {
            Window window = cordova.getActivity().getWindow();
            int flags = WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON;
            if (enabled) {
                window.addFlags(flags);
            } else {
                window.clearFlags(flags);
            }
        }

        isScreenTurnOnEnabled = enabled;
    }

    private boolean setUpMediaPlayer(int mode) {

        boolean success = false;

        releaseMediaPlayer();

        mMediaPlayer = new MediaPlayer();

        if (mode == MEDIA_PLAYER_MODE_OUTGOING_RING) {
            try {
                AssetFileDescriptor afd = cordova.getActivity().getAssets().openFd("www/assets/sounds/ringback.ogg");
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
                mMediaPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                mMediaPlayer.setLooping(true);
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "setUpMediaPlayer: ", e.getCause());
            }

        } else if (mode == MEDIA_PLAYER_MODE_INCOMING_RING) {
            Uri alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
            try {
                mMediaPlayer.setDataSource(cordova.getContext(), alert);
                success = true;
            } catch (Exception e) {
                Log.e(TAG, "setUpMediaPlayer: ", e.getCause());
            }

            if (mAudioManager.getStreamVolume(AudioManager.STREAM_RING) != 0) {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_RING);
                mMediaPlayer.setLooping(true);
            }
        }

        return success;
    }

    private void dumpFocus() {

        releaseMediaPlayer();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (mActiveAudioFocusRequest != null) {
                mAudioManager.abandonAudioFocusRequest(mActiveAudioFocusRequest);
            }
        } else {
            mAudioManager.abandonAudioFocus(mFocusChangeListener);
        }

    }

    private void releaseMediaPlayer() {
        if (mMediaPlayer != null) {

            if (mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }

            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void bringAppToFront() {
        if (isAppInForeground) {
            return;
        }
        Intent intent = new Intent(cordova.getContext(), cordova.getActivity().getClass());
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        cordova.getContext().startActivity(intent);
    }


    @TargetApi(26)
    private AudioFocusRequest createAudioRequest(int type) {
        AudioAttributes mAudioAttributes;

        if (type == AUDIO_REQUEST_TYPE_COMMUNICATION) {
            mAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .build();
        } else {
            mAudioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .build();
        }

        return new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mAudioAttributes)
                .setOnAudioFocusChangeListener(mFocusChangeListener)
                .build();
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(cordova.getContext(), NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(androidx.appcompat.R.drawable.notification_icon_background)
                .setDefaults(Notification.DEFAULT_LIGHTS)
                .setContentTitle(from)
                .setContentText("Incoming voice call");

        Intent acceptCallIntent = new Intent(cordova.getContext(), cordova.getActivity().getClass());

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(cordova.getContext());
        notificationManager.notify(INCOMING_CALL_NOTIFICATION_ID, builder.build());

    }

    private void dismissNotification() {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(cordova.getContext());
        notificationManager.cancel(INCOMING_CALL_NOTIFICATION_ID);
    }

    private void activateProximitySensor() {

        if (mWakeLock == null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, WAKE_LOCK_TAG);
            } else {
                mWakeLock = mPowerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG);
            }
        }
        if (!mWakeLock.isHeld()) {
            mWakeLock.acquire(3600000);
        }
    }

    private void deactivateProximitySensor() {
        if (mWakeLock != null && mWakeLock.isHeld()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mWakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
            } else {
                mWakeLock.release();
            }
        }
    }

}

class onFocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
    @Override
    public void onAudioFocusChange(int i) {
        // TODO: Implement this
    }
}