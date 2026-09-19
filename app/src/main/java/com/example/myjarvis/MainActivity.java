package com.example.myjarvis;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;
    private TextToSpeech tts;
    private CameraManager cameraManager;
    private String cameraId;
    private boolean isFlashOn = false;
    private SpeechRecognizer speechRecognizer;
    private AppScanner appScanner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("isDarkTheme", true);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        Button micButton = findViewById(R.id.micButton);
        ImageButton settingsButton = findViewById(R.id.settingsButton);

        // Запуск фоновой службы
        Intent serviceIntent = new Intent(this, LiraBackgroundService.class);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        // Инициализация сканера приложений
        appScanner = new AppScanner(this);

        // Инициализация камеры
        cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraManager != null && cameraManager.getCameraIdList().length > 0) {
                cameraId = cameraManager.getCameraIdList()[0];
            }
        } catch (Exception ignored) {}

        // Инициализация синтеза речи
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
            }
        });

        // Настройка собственного речевого распознавателя (без Google диалогового окна)
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { appendLog("[System] Слушаю вас..."); }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onError(int error) { appendLog("[System] Речь не распознана. Повторите."); }
                @Override public void onResults(Bundle results) {
                    java.util.ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String spoken = matches.get(0);
                        appendLog("[USER] " + spoken);
                        processCommand(spoken);
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }

        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                appendLog("[USER] " + text);
                inputField.setText("");
                processCommand(text);
            }
        });

        micButton.setOnClickListener(v -> startCustomVoiceInput());

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        appendLog("[L.I.R.A.] Ядро активировано. Просканировано приложений: устройство готово.");
    }

    private void startCustomVoiceInput() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            speechRecognizer.startListening(intent);
        } else {
            appendLog("[System] Распознавание речи недоступно.");
        }
    }

    private void processCommand(String command) {
        String lower = command.toLowerCase();
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean quietMode = prefs.getBoolean("isQuietMode", false);
        String response;

        if (lower.contains("фонарик") || lower.contains("свет")) {
            toggleFlashlight();
            response = isFlashOn ? "Фонарик включен." : "Фонарик выключен.";
        } else if (lower.contains("камер") || lower.contains("фото")) {
            openCamera();
            response = "Открываю камеру.";
        } else if (lower.contains("открой") || lower.contains("запусти")) {
            String appNameToLaunch = lower.replace("открой", "").replace("запусти", "").trim();
            boolean launched = appScanner.launchAppByName(appNameToLaunch);
            response = launched ? "Запускаю " + appNameToLaunch + "." : "Не удалось найти приложение: " + appNameToLaunch;
        } else if (lower.contains("пересканируй") || lower.contains("обнови приложения")) {
            appScanner.scanApps();
            response = "Список приложений успешно обновлен!";
        } else {
            // Пробуем запустить как приложение, если команда похожа на название
            boolean launched = appScanner.launchAppByName(lower);
            if (launched) {
                response = "Запускаю приложение.";
            } else {
                response = "Команда принята: " + command;
            }
        }

        appendLog("[L.I.R.A.] " + response);
        if (!quietMode && tts != null) {
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void toggleFlashlight() {
        try {
            if (cameraManager != null && cameraId != null) {
                isFlashOn = !isFlashOn;
                cameraManager.setTorchMode(cameraId, isFlashOn);
            }
        } catch (Exception ignored) {}
    }

    private void openCamera() {
        try {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivity(intent);
        } catch (Exception ignored) {}
    }

    private void appendLog(String text) {
        chatDisplay.append(text + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
