package com.example.myjarvis;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private EditText inputMessage;
    private TextView chatLog;
    private Switch switchWidget;
    private Switch switchVoice;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputMessage = findViewById(R.id.inputMessage);
        chatLog = findViewById(R.id.chatLog);
        Button sendButton = findViewById(R.id.sendButton);
        switchWidget = findViewById(R.id.switchWidget);
        switchVoice = findViewById(R.id.switchVoice);

        // Стандартное приветствие при обычном открытии приложения
        chatLog.setText("L.I.R.A.: Привет, Костя! Чем я могу помочь сегодня?\n");

        // Обработка отправки текста по кнопке
        sendButton.setOnClickListener(v -> {
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                chatLog.append("Костя: " + text + "\n");
                chatLog.append("L.I.R.A.: Обрабатываю ваш запрос...\n");
                inputMessage.setText("");
            }
        });

        // Управление плавающим виджетом через настройки (выключен по умолчанию)
        if (switchWidget != null) {
            switchWidget.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                        Toast.makeText(this, "Пожалуйста, разрешите наложение поверх других окон", Toast.LENGTH_LONG).show();
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

        // Управление опцией голосовой активации (опционально)
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
}
