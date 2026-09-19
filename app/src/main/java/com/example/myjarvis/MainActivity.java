package com.example.myjarvis;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText inputMessage;
    private TextView chatLog;
    private Switch switchWidget;
    private Switch switchVoice;
    private SharedPreferences prefs;
    private String userName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputMessage = findViewById(R.id.inputMessage);
        chatLog = findViewById(R.id.chatLog);
        Button sendButton = findViewById(R.id.sendButton);
        Button micButton = findViewById(R.id.micButton);
        switchWidget = findViewById(R.id.switchWidget);
        switchVoice = findViewById(R.id.switchVoice);

        prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        userName = prefs.getString("user_name", "");

        // Проверяем, знакомы ли мы с пользователем
        if (userName.isEmpty()) {
            askForUserName();
        } else {
            chatLog.setText("L.I.R.A.: Привет, " + userName + "! Чем я могу помочь сегодня?\n");
        }

        // Обработка текстового ввода
        sendButton.setOnClickListener(v -> {
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                chatLog.append(userName + ": " + text + "\n");
                
                // Простая логика обработки команд
                String lower = text.toLowerCase();
                if (lower.contains("открой") || lower.contains("запусти")) {
                    chatLog.append("L.I.R.A.: Выполняю запрос...\n");
                    // Здесь можно добавить открытие приложений или URL
                } else {
                    chatLog.append("L.I.R.A.: Я услышала вас, " + userName + "! Обрабатываю...\n");
                }
                
                inputMessage.setText("");
            }
        });

        // Кнопка микрофона
        micButton.setOnClickListener(v -> {
            chatLog.append("L.I.R.A.: Голосовой ввод активирован (слушаю)...\n");
            Toast.makeText(this, "Слушаю ваш голос...", Toast.LENGTH_SHORT).show();
        });

        // Управление плавающим виджетом через настройки
        if (switchWidget != null) {
            switchWidget.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Разрешите наложение поверх других окон", Toast.LENGTH_LONG).show();
                        Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                        switchWidget.setChecked(false);
                    } else {
                        startService(new Intent(this, LiraBackgroundService.class));
                        Toast.makeText(this, "Плавающий виджет включен", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    stopService(new Intent(this, LiraBackgroundService.class));
                    Toast.makeText(this, "Плавающий виджет выключен", Toast.LENGTH_SHORT).show();
                }
            });
        }

        // Управление голосовой активацией
        if (switchVoice != null) {
            switchVoice.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    Toast.makeText(this, "Голосовая активация включена", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Голосовая активация выключена", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void askForUserName() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Знакомство");
        builder.setMessage("Привет! Я ассистент L.I.R.A. Как вас зовут?");

        final EditText input = new EditText(this);
        builder.setView(input);

        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            userName = input.getText().toString().trim();
            if (userName.isEmpty()) {
                userName = "Друг";
            }
            prefs.edit().putString("user_name", userName).apply();
            chatLog.setText("L.I.R.A.: Приятно познакомиться, " + userName + "! Чем я могу помочь?\n");
        });

        builder.setCancelable(false);
        builder.show();
    }
}
