package com.example.myjarvis;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.Window;
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

        // Показываем красивое всплывающее окно с политикой конфиденциальности
        privacyPolicyLink.setOnClickListener(v -> showPrivacyPolicyDialog());

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

    private void showPrivacyPolicyDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_privacy_policy);
        
        // Делаем фон диалога прозрачным, чтобы были видны закругления нашего макета
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        TextView tvClose = dialog.findViewById(R.id.tvClose);
        Button btnOk = dialog.findViewById(R.id.btnOk);

        tvClose.setOnClickListener(v -> dialog.dismiss());
        btnOk.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }
}
