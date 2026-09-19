package com.example.myjarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;
    private Button micButton;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Animation pulseAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);

        // Проверка прав на микрофон
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Проверка прав на плавающее окно
        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 2);
        } else {
            startService(new Intent(this, FloatingWidgetService.class));
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru"));
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { 
                inputField.setHint("Слушаю..."); 
                micButton.startAnimation(pulseAnimation);
                micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF0000)); // Красный при записи
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { stopMicAnim(); }
            @Override public void onError(int error) { stopMicAnim(); appendMessage("Система", "Ошибка распознавания"); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                stopMicAnim();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    appendMessage("USER", recognizedText);
                    processCommand(recognizedText);
                }
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                appendMessage("USER", text);
                inputField.setText("");
                processCommand(text);
            }
        });

        micButton.setOnClickListener(v -> speechRecognizer.startListening(speechIntent));
        
        handleAutoListen(getIntent());
    }

    private void stopMicAnim() {
        micButton.clearAnimation();
        micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF)); // Возврат к неоновому
        inputField.setHint("Текстовая команда...");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAutoListen(intent);
    }

    private void handleAutoListen(Intent intent) {
        if (intent != null && intent.getBooleanExtra("AUTO_LISTEN", false)) {
            micButton.performClick();
        }
    }

    private void processCommand(String command) {
        String lowerCmd = command.toLowerCase();
        
        if (lowerCmd.contains("открой") || lowerCmd.contains("запусти")) {
            if (lowerCmd.contains("телеграм")) {
                openApp("org.telegram.messenger", "Телеграм");
            } else if (lowerCmd.contains("ютуб") || lowerCmd.contains("youtube")) {
                openApp("com.google.android.youtube", "YouTube");
            } else if (lowerCmd.contains("камеру")) {
                openApp("com.google.android.GoogleCamera", "Камера");
            } else if (lowerCmd.contains("настройки")) {
                openApp("com.android.settings", "Настройки");
            } else {
                respond("Приложение не найдено.");
            }
            return;
        }
        if (lowerCmd.contains("привет")) { respond("Приветствую, сэр."); } 
        else { respond("Команда не распознана: " + command); }
    }
    
    private void openApp(String packageName, String appName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            respond("Открываю " + appName);
            startActivity(intent);
        } else {
            respond("Не удалось найти " + appName);
        }
    }

    private void respond(String text) {
        appendMessage("J.A.R.V.I.S.", text);
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void appendMessage(String sender, String message) {
        String currentText = chatDisplay.getText().toString();
        chatDisplay.setText(currentText + "\n[" + sender + "] " + message);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
