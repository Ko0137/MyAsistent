package com.example.myjarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
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
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        Button micButton = findViewById(R.id.micButton);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru"));
            }
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { inputField.setHint("Слушаю..."); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { inputField.setHint("Ожидание..."); }
            @Override public void onError(int error) { appendMessage("Система", "Ошибка распознавания"); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
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
                respond("Приложение не найдено в моей базе.");
            }
            return;
        }

        if (lowerCmd.contains("привет")) {
            respond("Приветствую, сэр.");
        } else if (lowerCmd.contains("статус")) {
            respond("Все системы в норме.");
        } else {
            respond("Команда не распознана: " + command);
        }
    }
    
    private void openApp(String packageName, String appName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            respond("Открываю " + appName);
            startActivity(intent);
        } else {
            respond("Не удалось найти " + appName + " на устройстве.");
        }
    }

    private void respond(String text) {
        appendMessage("J.A.R.V.I.S.", text);
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
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
