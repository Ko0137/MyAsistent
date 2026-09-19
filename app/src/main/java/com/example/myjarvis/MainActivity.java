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

        // Запрос прав на микрофон
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        // Инициализация говорилки (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru"));
                speak("Здравствуйте, Костя. Я готов к работе.");
            }
        });

        // Инициализация слушалки (SpeechRecognizer)
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { inputField.setHint("Слушаю..."); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { inputField.setHint("Команда..."); }
            @Override public void onError(int error) { appendMessage("Система", "Ошибка распознавания"); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    appendMessage("Ты (Голос)", recognizedText);
                    processCommand(recognizedText);
                }
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                appendMessage("Ты", text);
                inputField.setText("");
                processCommand(text);
            }
        });

        micButton.setOnClickListener(v -> speechRecognizer.startListening(speechIntent));
    }

    private void processCommand(String command) {
        String lowerCmd = command.toLowerCase();
        String response = "Команда не распознана.";

        if (lowerCmd.contains("привет")) {
            response = "Приветствую! Чем могу помочь?";
        } else if (lowerCmd.contains("как дела") || lowerCmd.contains("статус")) {
            response = "Системы работают в штатном режиме. Заряд в норме.";
        } else if (lowerCmd.contains("спасибо")) {
            response = "Всегда к вашим услугам.";
        } else if (lowerCmd.contains("пока") || lowerCmd.contains("отключись")) {
            response = "Отключаю питание. До свидания.";
        } else {
            response = "Я услышал: " + command + ". Но я пока не умею это выполнять.";
        }

        appendMessage("Jarvis", response);
        speak(response);
    }

    private void speak(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void appendMessage(String sender, String message) {
        String currentText = chatDisplay.getText().toString();
        chatDisplay.setText(currentText + "\n" + sender + ": " + message + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
