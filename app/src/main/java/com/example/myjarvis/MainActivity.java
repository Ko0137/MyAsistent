package com.example.myjarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText inputMessage;
    private TextView chatLog, statusText;
    private ScrollView chatScroll;
    private Switch switchTheme, switchWidget, switchVoice;
    private Button micButton, sendButton;

    private SharedPreferences prefs;
    private String userName = "";
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    private boolean isListening = false;
    private boolean isVoiceEnabled = true;
    private boolean isTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_theme", true);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputMessage = findViewById(R.id.inputMessage);
        chatLog = findViewById(R.id.chatLog);
        chatScroll = findViewById(R.id.chatScroll);
        statusText = findViewById(R.id.statusText);
        switchTheme = findViewById(R.id.switchTheme);
        switchWidget = findViewById(R.id.switchWidget);
        switchVoice = findViewById(R.id.switchVoice);
        micButton = findViewById(R.id.micButton);
        sendButton = findViewById(R.id.sendButton);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        userName = prefs.getString("user_name", "");
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true);

        switchTheme.setChecked(isDark);
        switchVoice.setChecked(isVoiceEnabled);

        checkPermissions();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
            }
        });

        setupSpeechRecognizer();

        String history = prefs.getString("chat_history", "");
        if (!history.isEmpty()) {
            chatLog.setText(history);
        } else if (userName.isEmpty()) {
            askForUserName();
        } else {
            appendChat("L.I.R.A.: Привет, " + userName + "! Система готова к работе.");
        }

        sendButton.setOnClickListener(v -> {
            vibrate(30);
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                processCommand(text);
                inputMessage.setText("");
            }
        });

        micButton.setOnClickListener(v -> {
            vibrate(50);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                return;
            }
            if (!isListening) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
                speechRecognizer.startListening(intent);
                micButton.setText("🔴");
                statusText.setText("LISTENING...");
            } else {
                speechRecognizer.stopListening();
                micButton.setText("🎙️");
                statusText.setText("ONLINE");
            }
        });

        switchTheme.setOnCheckedChangeListener((btn, isChecked) -> {
            prefs.edit().putBoolean("dark_theme", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        switchVoice.setOnCheckedChangeListener((btn, isChecked) -> {
            isVoiceEnabled = isChecked;
            prefs.edit().putBoolean("voice_enabled", isChecked).apply();
        });

        switchWidget.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Предоставьте разрешение поверх других окон", Toast.LENGTH_LONG).show();
                    startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
                    switchWidget.setChecked(false);
                } else {
                    startService(new Intent(this, LiraBackgroundService.class));
                }
            } else {
                stopService(new Intent(this, LiraBackgroundService.class));
            }
        });
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { 
                    isListening = false; 
                    micButton.setText("🎙️");
                    statusText.setText("ONLINE");
                }
                @Override public void onError(int error) { 
                    isListening = false; 
                    micButton.setText("🎙️");
                    statusText.setText("ONLINE");
                }
                @Override public void onResults(Bundle results) {
                    isListening = false; 
                    micButton.setText("🎙️");
                    statusText.setText("ONLINE");
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processCommand(matches.get(0));
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 1);
        }
    }

    private void vibrate(long durationMs) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        }
    }

    private void processCommand(String text) {
        appendChat((userName.isEmpty() ? "Вы" : userName) + ": " + text);
        String lower = text.toLowerCase();
        String response = "";

        try {
            if (lower.contains("фонарик")) {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String cameraId = camManager.getCameraIdList()[0];
                isTorchOn = !isTorchOn;
                camManager.setTorchMode(cameraId, isTorchOn);
                response = isTorchOn ? "Фонарик включен." : "Фонарик выключен.";
            } else if (lower.contains("батарея") || lower.contains("заряд")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                response = "Текущий уровень заряда батареи: " + level + "%.";
            } else if (lower.contains("пауза") || lower.contains("музыка") || lower.contains("плей")) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                response = "Сигнал воспроизведения/паузы отправлен.";
            } else if (lower.contains("браузер")) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("[https://google.com](https://google.com)")));
                response = "Открываю браузер.";
            } else if (lower.contains("камера") || lower.contains("фото")) {
                startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                response = "Открываю камеру.";
            } else if (lower.contains("заметка")) {
                String note = text.replaceFirst("(?i).*заметка", "").trim();
                if (!note.isEmpty()) {
                    prefs.edit().putString("quick_note", note).apply();
                    response = "Заметка сохранена: \"" + note + "\"";
                } else {
                    String saved = prefs.getString("quick_note", "Заметок пока нет.");
                    response = "Сохраненная заметка: " + saved;
                }
            } else if (lower.contains("очисти")) {
                chatLog.setText("");
                prefs.edit().remove("chat_history").apply();
                response = "История чата очищена.";
            } else if (lower.contains("привет")) {
                response = "Привет, " + userName + "! Чем могу помочь?";
            } else {
                response = "Принято: " + text;
            }
        } catch (Exception e) {
            response = "Ошибка выполнения: " + e.getMessage();
        }

        appendChat("L.I.R.A.: " + response);
        if (isVoiceEnabled && tts != null) {
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void appendChat(String message) {
        chatLog.append(message + "\n\n");
        prefs.edit().putString("chat_history", chatLog.getText().toString()).apply();
        chatScroll.post(() -> chatScroll.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void askForUserName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Инициализация L.I.R.A.");
        builder.setMessage("Введите ваше имя:");
        final EditText input = new EditText(this);
        builder.setView(input);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            userName = input.getText().toString().trim();
            if (userName.isEmpty()) userName = "Пользователь";
            prefs.edit().putString("user_name", userName).apply();
            appendChat("L.I.R.A.: Приятно познакомиться, " + userName + "!");
        });
        builder.setCancelable(false);
        builder.show();
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
