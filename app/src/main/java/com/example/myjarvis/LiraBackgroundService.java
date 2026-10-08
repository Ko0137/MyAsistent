package com.example.myjarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.android.StorageService;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LiraBackgroundService extends Service {

    private static final String TAG = "LiraBackgroundService";
    private static final String CHANNEL_ID = "LiraVoiceServiceChannel";
    private static final int NOTIFICATION_ID = 1337;

    private static final int SAMPLE_RATE = 16000;
    private static final int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord audioRecord;
    private boolean isListening = false;
    private ExecutorService audioExecutor;
    private Model model;
    private Recognizer recognizer;

    @Override
    public void onCreate() {
        super.onCreate();
        LibVosk.setLogLevel(LogLevel.DEBUG);
        createNotificationChannel();
        Executors.newSingleThreadExecutor().execute(this::initVoskModel);
    }

    private void initVoskModel() {
        try {
            StorageService.unpack(this, "model-ru", "model-ru",
                    modelDir -> {
                        try {
                            model = new Model(modelDir.getAbsolutePath());
                            recognizer = new Recognizer(model, SAMPLE_RATE);
                            Log.d(TAG, "Vosk model loaded successfully.");
                        } catch (IOException e) {
                            Log.e(TAG, "Error creating Vosk Recognizer", e);
                        }
                    },
                    exception -> Log.e(TAG, "Failed to unpack Vosk model", exception)
            );
        } catch (Exception e) {
            Log.e(TAG, "Init Vosk error", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundServiceNotification();
        startListening();
        return START_STICKY;
    }

    private void startForegroundServiceNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("L.I.R.A. Активна")
                .setContentText("Фоновый режим включен")
                .setSmallIcon(R.drawable.ic_lira_logo)
                .setContentIntent(pendingIntent)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

   private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Lira Voice Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private void startListening() {
        if (isListening) return;

        int bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
        if (bufferSize <= 0) {
            bufferSize = SAMPLE_RATE * 2;
        }

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT,
                    bufferSize
            );
        } catch (SecurityException e) {
            Log.e(TAG, "Microphone permission denied", e);
            return;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord initialization failed");
            return;
        }

        isListening = true;
        audioRecord.startRecording();
        audioExecutor = Executors.newSingleThreadExecutor();

        audioExecutor.execute(() -> {
            byte[] buffer = new byte[4096];
            while (isListening) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0 && recognizer != null) {
                    boolean completed = recognizer.acceptWaveForm(buffer, read);
                    if (completed) {
                        handleResult(recognizer.getResult());
                    }
                }
            }
        });
    }

    private void handleResult(String jsonResult) {
        try {
            org.json.JSONObject obj = new org.json.JSONObject(jsonResult);
            String text = obj.optString("text", "").trim();
            if (!text.isEmpty()) {
                Log.d(TAG, "Recognized: " + text);
                Intent intent = new Intent("com.example.myjarvis.VOICE_COMMAND");
                intent.putExtra("command", text);
                sendBroadcast(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "JSON parse error", e);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isListening = false;
        if (audioRecord != null) {
            audioRecord.stop();
            audioRecord.release();
            audioRecord = null;
        }
        if (audioExecutor != null) {
            audioExecutor.shutdownNow();
        }
        if (recognizer != null) recognizer.close();
        if (model != null) model.close();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

