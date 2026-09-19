package com.example.myjarvis;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        Switch themeSwitch = findViewById(R.id.themeSwitch);
        Switch voiceSwitch = findViewById(R.id.voiceSwitch);
        Switch quietSwitch = findViewById(R.id.quietSwitch);
        TextView privacyPolicyLink = findViewById(R.id.privacyPolicyLink);
        Button backButton = findViewById(R.id.backButton);

        // Делаем текст ссылки подчеркнутым программно
        privacyPolicyLink.setPaintFlags(privacyPolicyLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        // Загружаем сохраненные настройки
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        themeSwitch.setChecked(prefs.getBoolean("theme_dark", true));
        voiceSwitch.setChecked(prefs.getBoolean("voice_female", true));
        quietSwitch.setChecked(prefs.getBoolean("quiet_mode", false));

        // Кликабельная политика конфиденциальности
        privacyPolicyLink.setOnClickListener(v -> {
            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/Ko0137/MyAsistent"));
            startActivity(browserIntent);
        });

        backButton.setOnClickListener(v -> {
            // Сохраняем состояние переключателей
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("theme_dark", themeSwitch.isChecked());
            editor.putBoolean("voice_female", voiceSwitch.isChecked());
            editor.putBoolean("quiet_mode", quietSwitch.isChecked());
            editor.apply();

            Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
