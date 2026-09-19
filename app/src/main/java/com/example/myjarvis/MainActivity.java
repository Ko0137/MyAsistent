package com.example.myjarvis;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
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
        chatLayout = findViewById(R.id.chatLayout);
        chatScrollView = findViewById(R.id.chatScrollView);
        etInput = findViewById(R.id.etInput);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
                applyVoicePreference();
                isTtsInitialized = true;
                
                boolean isQuietMode = prefs.getBoolean("quiet_mode", false);
                if (!isQuietMode) {
                    speakText("Система LIRA активна и готова к работе.");
                }
            }
        });

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

        addMessageToChat("L.I.R.A.: Привет! Я твой ассистент. Чем могу помочь?", Gravity.START);
    }

    private void applyVoicePreference() {
        if (!isTtsInitialized || tts == null) return;
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isFemale = prefs.getBoolean("voice_female", true);
        
        try {
            for (java.util.Locale locale : tts.getAvailableLanguages()) {
                if (locale.getLanguage().equals("ru")) {
                    tts.setLanguage(locale);
                }
            }
            for (android.speech.tts.Voice voice : tts.getVoices()) {
                if (voice.getLocale().getLanguage().equals("ru")) {
                    String name = voice.getName().toLowerCase();
                    if (isFemale && (name.contains("female") || name.contains("woman") || name.contains("ru-ru-x-rub#female"))) {
                        tts.setVoice(voice);
                        break;
                    } else if (!isFemale && (name.contains("male") || name.contains("man") || name.contains("ru-ru-x-ruc#male"))) {
                        tts.setVoice(voice);
                        break;
                    }
                }
            }
        } catch (Exception e) {
            // Игнорируем, если кастомный голос не поддерживается
        }
    }

    private void processUserCommand(String query) {
        String lower = query.toLowerCase();
        String response;

        if (lower.contains("привет")) {
            response = "Привет, Костя! Рада тебя слышать.";
        } else if (lower.contains("как дела")) {
            response = "Системы работают стабильно, всё в порядке!";
        } else if (lower.contains("время") || lower.contains("час")) {
            String time = android.text.format.DateFormat.format("HH:mm", new java.util.Date()).toString();
            response = "Текущее время: " + time;
        } else {
            response = "Я получила твое сообщение: \"" + query + "\". Работаем дальше!";
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
        tv.setTextSize(15); // Исправлено: убран некорректный суффикс sp
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
    protected void onResume() {
        super.onResume();
        applyVoicePreference();
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
