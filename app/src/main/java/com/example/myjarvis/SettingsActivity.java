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
import androidx.appcompat.app.AppCompatDelegate;

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

        privacyPolicyLink.setPaintFlags(privacyPolicyLink.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);

        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean("theme_dark", true);
        boolean isFemaleVoice = prefs.getBoolean("voice_female", true);
        boolean isQuietMode = prefs.getBoolean("quiet_mode", false);

        themeSwitch.setChecked(isDarkTheme);
        voiceSwitch.setChecked(isFemaleVoice);
        quietSwitch.setChecked(isQuietMode);

        // Мгновенная смена темы при переключении тумблера
        themeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("theme_dark", isChecked);
            editor.apply();

            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        voiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("voice_female", isChecked);
            editor.apply();
        });

        quietSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean("quiet_mode", isChecked);
            editor.apply();
        });

        privacyPolicyLink.setOnClickListener(v -> showPrivacyPolicyDialog());

        backButton.setOnClickListener(v -> {
            Toast.makeText(this, "Настройки сохранены!", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void showPrivacyPolicyDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_privacy_policy);
        
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
