package com.example.myjarvis;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private EditText inputMessage;
    private TextView chatLog;
    private Switch switchWidget;
    private Switch switchVoice;
    private SharedPreferences prefs;
    private String userName = "";
    private TextToSpeech tts;
    private static final int VOICE_RECOG_REQUEST_CODE = 123;

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

        // Инициализация офлайн синтеза речи (TTS)
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
            }
        });

        // Загрузка сохраненной истории чата
        String savedHistory = prefs.getString("chat_history", "");
        if (!savedHistory.isEmpty()) {
            chatLog.setText(savedHistory);
        } else {
            if (userName.isEmpty()) {
                askForUserName();
            } else {
                appendChat("L.I.R.A.: Привет, " + userName + "! Чем я могу помочь сегодня?");
            }
        }

        // Обработка текстового ввода
        sendButton.setOnClickListener(v -> {
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                processUserCommand(text);
                inputMessage.setText("");
            }
        });

        // Кнопка офлайн голосового ввода
        micButton.setOnClickListener(v -> startVoiceRecognition());

        // Переключатель плавающего виджета
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
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажите команду для L.I.R.A....");
        try {
            startActivityForResult(intent, VOICE_RECOG_REQUEST_CODE);
        } catch (Exception e) {
            Toast.makeText(this, "Голосовой ввод недоступен на этом устройстве", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == VOICE_RECOG_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            java.util.ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                String spokenText = result.get(0);
                processUserCommand(spokenText);
            }
        }
    }

    private void processUserCommand(String text) {
        appendChat(userName + ": " + text);
        String lower = text.toLowerCase();
        String response;

        if (lower.contains("как тебя зовут") || lower.contains("кто ты")) {
            response = "Я L.I.R.A., твой персональный офлайн-ассистент.";
        } else if (lower.contains("как меня зовут")) {
            response = "Твое имя — " + userName + "!";
        } else if (lower.contains("время") || lower.contains("который час")) {
            String time = android.text.format.DateFormat.format("HH:mm", new java.util.Date()).toString();
            response = "Сейчас " + time + ".";
        } else if (lower.contains("открой браузер") || lower.contains("поиск")) {
            response = "Открываю браузер...";
            try {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com"));
                startActivity(browserIntent);
            } catch (Exception e) {
                response = "Не удалось открыть браузер.";
            }
        } else {
            response = "Я поняла тебя, " + userName + "! Локальный режим активен, интернет не требуется.";
        }

        appendChat("L.I.R.A.: " + response);
        speakOut(response);
    }

    private void speakOut(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void appendChat(String message) {
        chatLog.append(message + "\n");
        prefs.edit().putString("chat_history", chatLog.getText().toString()).apply();
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
            String welcome = "Приятно познакомиться, " + userName + "! Чем я могу помочь?";
            appendChat("L.I.R.A.: " + welcome);
            speakOut(welcome);
        });

        builder.setCancelable(false);
        builder.show();
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
