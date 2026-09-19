package com.example.myjarvis;

import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;
    private TextToSpeech tts;
    private static final int REQUEST_CODE_STT = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        Button micButton = findViewById(R.id.micButton);
        ImageButton settingsButton = findViewById(R.id.settingsButton);

        // Безопасная инициализация TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                appendLog("[USER] " + text);
                inputField.setText("");
                processCommand(text);
            }
        });

        micButton.setOnClickListener(v -> startVoiceRecognition());

        settingsButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        appendLog("[L.I.R.A.] Привет! Я Лира. Готова к работе.");
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        try {
            startActivityForResult(intent, REQUEST_CODE_STT);
        } catch (Exception e) {
            appendLog("[System] Голосовой ввод недоступен.");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_STT && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                appendLog("[USER] " + spokenText);
                processCommand(spokenText);
            }
        }
    }

    private void processCommand(String command) {
        String lower = command.toLowerCase();
        String response;

        if (lower.contains("фонарик")) {
            response = "Фонарик переключается.";
        } else if (lower.contains("музык") || lower.contains("яндекс")) {
            response = "Включаю музыку.";
        } else if (lower.contains("камер")) {
            response = "Открываю камеру.";
        } else {
            response = "Команда принята: " + command;
        }

        appendLog("[L.I.R.A.] " + response);
        if (tts != null) {
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void appendLog(String text) {
        chatDisplay.append(text + "\n");
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
