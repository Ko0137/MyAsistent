package com.example.myjarvis;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Применяем сохраненную темную тему при старте
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
        TextView tvStatus = findViewById(R.id.tvStatus);

        // Инициализация синтеза речи (TTS) с учетом выбранного голоса и тихого режима
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
                isTtsInitialized = true;
                checkAndSpeakWelcome();
            }
        });

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
    }

    private void checkAndSpeakWelcome() {
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isQuietMode = prefs.getBoolean("quiet_mode", false);

        if (!isQuietMode && isTtsInitialized) {
            tts.speak("Система LIRA активна и готова к работе.", TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // При возвращении из настроек можно дополнительно обновить поведение, если нужно
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
