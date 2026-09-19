package com.example.myjarvis;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private SpeechRecognizer speechRecognizer;
    
    private LinearLayout chatLayout;
    private ScrollView chatScrollView;
    private EditText etInput;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean("theme_dark", true);
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnMic = findViewById(R.id.btnMic);
        FloatingActionButton fabGeminiVoice = findViewById(R.id.fabGeminiVoice);
        
        chatLayout = findViewById(R.id.chatLayout);
        chatScrollView = findViewById(R.id.chatScrollView);
        etInput = findViewById(R.id.etInput);

        // Проверяем разрешение на микрофон
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO_PERMISSION);
        }

        // Инициализация TTS
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
                applyVoicePreference();
                isTtsInitialized = true;
                
                boolean isQuietMode = prefs.getBoolean("quiet_mode", false);
                if (!isQuietMode) {
                    speakText("L.I.R.A. активна и готова к работе.");
                }
            }
        });

        // Инициализация распознавания речи
        initSpeechRecognizer();

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                addMessageToChat("Вы: " + text, Gravity.END);
                etInput.setText("");
                processUserCommand(text);
            }
        });

        btnMic.setOnClickListener(v -> startVoiceListening());
        fabGeminiVoice.setOnClickListener(v -> {
            Toast.makeText(this, "Слушаю вас...", Toast.LENGTH_SHORT).show();
            startVoiceListening();
        });

        addMessageToChat("L.I.R.A.: Привет! Я твоя ассистентка. Жду команды или голосовой запрос.", Gravity.START);
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    Toast.makeText(MainActivity.this, "Не удалось распознать речь. Попробуйте еще раз.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void results(Bundle results) {}

                @Override
                public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        addMessageToChat("Вы (голосом): " + spokenText, Gravity.END);
                        processUserCommand(spokenText);
                    }
                }

                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle extras) {}
            });
        }
    }

    private void startVoiceListening() {
        if (speechRecognizer == null) {
            initSpeechRecognizer();
        }
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        try {
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка запуска микрофона", Toast.LENGTH_SHORT).exec();
        }
    }

    private void applyVoicePreference() {
        if (!isTtsInitialized || tts == null) return;
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isFemale = prefs.getBoolean("voice_female", true);
        try {
            for (Locale locale : tts.getAvailableLanguages()) {
                if (locale.getLanguage().equals("ru")) tts.setLanguage(locale);
            }
        } catch (Exception ignored) {}
    }

    // Обработка команд и выполнение реальных действий
    private void processUserCommand(String query) {
        String lower = query.toLowerCase();
        String response = "";
        boolean actionExecuted = false;

        if (lower.contains("привет") || lower.contains("здарова")) {
            response = "Привет, Костя! Рада тебя слышать.";
        } else if (lower.contains("как дела") || lower.contains("как ты")) {
            response = "Системы функционируют в штатном режиме!";
        } else if (lower.contains("время") || lower.contains("час")) {
            String time = android.text.format.DateFormat.format("HH:mm", new java.util.Date()).toString();
            response = "Текущее время: " + time;
        } else if (lower.contains("открой браузер") || lower.contains("зайди в интернет") || lower.contains("гугл")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            startActivity(intent);
            response = "Открываю браузер.";
            actionExecuted = true;
        } else if (lower.contains("камера") || lower.contains("сделай фото")) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (intent.resolveActivity(getPackageManager()) != null) {
                startActivity(intent);
                response = "Открываю камеру.";
            } else {
                response = "Камера недоступна.";
            }
            actionExecuted = true;
        } else if (lower.contains("позвони")) {
            // Извлекаем номер или имя, если есть
            Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:"));
            startActivity(intent);
            response = "Открываю телефонную книгу.";
            actionExecuted = true;
        } else {
            response = "Я получила запрос: \"" + query << "\". Выполняю анализ!";
        }

        addMessageToChat("L.I.R.A.: " + response, Gravity.START);

        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isQuietMode = prefs.getBoolean("quiet_mode", false);
        if (!isQuietMode) {
            speakText(response);
        }
    }

    private void speakText(String text) {
        if (isTtsInitialized && tts != null) {
            applyVoicePreference();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void addMessageToChat(String message, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(15);
        tv.setPadding(14, 10, 14, 10);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = gravity;
        params.setMargins(8, 4, 8, 4);
        tv.setLayoutParams(params);

        chatLayout.addView(tv);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO_PERMISSION) {
            if (!(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                Toast.makeText(this, "Для голосовых команд нужен доступ к микрофону!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyVoicePreference();
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
