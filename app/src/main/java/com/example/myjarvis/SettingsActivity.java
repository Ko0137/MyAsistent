package com.example.myjarvis;

import android.os.Bundle;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch themeSwitch = findViewById(R.id.themeSwitch);
        Switch voiceSwitch = findViewById(R.id.voiceSwitch);
        Switch quietSwitch = findViewById(R.id.quietSwitch);

        // Восстанавливаем состояние из SharedPreferences
        android.content.SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        themeSwitch.setChecked(prefs.getBoolean("isDarkTheme", true));
        voiceSwitch.setChecked(prefs.getBoolean("isFemaleVoice", false));
        quietSwitch.setChecked(prefs.getBoolean("isQuietMode", false));

        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isDarkTheme", isChecked).apply();
            AppCompatDelegate.setDefaultNightMode(isChecked ? 
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
        });

        voiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isFemaleVoice", isChecked).apply();
        });

        quietSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("isQuietMode", isChecked).apply();
        });
    }
}
