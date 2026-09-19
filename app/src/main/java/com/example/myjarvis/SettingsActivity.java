package com.example.myjarvis;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
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
        Button backButton = findViewById(R.id.backButton);

        backButton.setOnClickListener(v -> {
            Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
