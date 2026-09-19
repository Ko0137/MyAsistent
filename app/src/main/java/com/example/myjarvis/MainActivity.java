package com.example.myjarvis;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewChat;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList = new ArrayList<>();

    private EditText inputMessage;
    private TextView statusText;
    private ImageButton micButton, btnSettings, btnHelp;
    private Button sendButton;

    private SharedPreferences prefs;
    private String userName = "";
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Vibrator vibrator;

    private boolean isListening = false;
    private boolean isVoiceEnabled = true;
    private boolean isTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDark = prefs.getBoolean("dark_theme", true);
        AppCompatDelegate.setDefaultNightMode(isDark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recyclerViewChat = findViewById(R.id.recyclerViewChat);
        inputMessage = findViewById(R.id.inputMessage);
        statusText = findViewById(R.id.statusText);
        micButton = findViewById(R.id.micButton);
        btnSettings = findViewById(R.id.btnSettings);
        btnHelp = findViewById(R.id.btnHelp);
        sendButton = findViewById(R.id.sendButton);

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        userName = prefs.getString("user_name", "Пользователь");
        isVoiceEnabled = prefs.getBoolean("voice_enabled", true);

        chatAdapter = new ChatAdapter(this, messageList);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        recyclerViewChat.setLayoutManager(layoutManager);
        recyclerViewChat.setAdapter(chatAdapter);

        checkPermissions();
        checkFirstLaunchPrivacy();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
            }
        });

        setupSpeechRecognizer();
        loadHistory();

        if (messageList.isEmpty()) {
            addMessage("L.I.R.A.: Все системы активны. Нажми '?' сверху для просмотра списка команд!", false);
        }

        sendButton.setOnClickListener(v -> {
            vibrate(30);
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                processCommand(text);
                inputMessage.setText("");
            }
        });

        micButton.setOnClickListener(v -> {
            vibrate(50);
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, 1);
                return;
            }
            if (!isListening) {
                startListening();
            } else {
                stopListening();
            }
        });

        btnSettings.setOnClickListener(v -> {
            vibrate(30);
            showSettingsDialog();
        });

        btnHelp.setOnClickListener(v -> {
            vibrate(30);
            showHelpDialog();
        });
    }

    private void checkFirstLaunchPrivacy() {
        boolean accepted = prefs.getBoolean("privacy_accepted", false);
        if (!accepted) {
            new AlertDialog.Builder(this)
                .setTitle("🔒 Конфиденциальность L.I.R.A.")
                .setMessage("Все ваши данные, голос и заметки обрабатываются строго локально на вашем устройстве и НЕ передаются на сторонние серверы.\n\nПродолжая, вы соглашаетесь с Политикой конфиденциальности.")
                .setPositiveButton("Принять", (dialog, which) -> {
                    prefs.edit().putBoolean("privacy_accepted", true).apply();
                })
                .setCancelable(false)
                .show();
        }
    }

    private void startListening() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            speechRecognizer.startListening(intent);
            isListening = true;
            statusText.setText("LISTENING");
            statusText.setBackgroundColor(0xFFD32F2F);
            micButton.setBackgroundResource(R.drawable.bg_mic_active);
        }
    }

    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
            statusText.setText("READY");
            statusText.setBackgroundColor(0xFF1B4D3E);
            micButton.setBackgroundResource(R.drawable.bg_mic_btn);
        }
    }

    private void setupSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { stopListening(); }
                @Override public void onError(int error) { stopListening(); }
                @Override public void onResults(Bundle results) {
                    stopListening();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        processCommand(matches.get(0));
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void processCommand(String text) {
        addMessage(text, true);
        String lower = text.toLowerCase().trim();
        String response = "";

        try {
            if (lower.startsWith("открой ") || lower.startsWith("запусти ")) {
                String appQuery = lower.replaceFirst("^(открой|запусти)\\s+", "").trim();
                String launched = openAppByName(appQuery);
                if (launched != null) {
                    response = launched;
                } else if (appQuery.contains("камера")) {
                    startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    response = "Открываю камеру.";
                } else if (appQuery.contains("браузер")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
                    response = "Открываю браузер.";
                } else {
                    response = "Приложение \"" + appQuery + "\" не найдено.";
                }
            } else if (containsAny(lower, "фонарик", "свет", "подсвети", "вспышка")) {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String cameraId = camManager.getCameraIdList()[0];
                isTorchOn = !isTorchOn;
                camManager.setTorchMode(cameraId, isTorchOn);
                response = isTorchOn ? "Фонарик включен." : "Фонарик выключен.";
            } else if (containsAny(lower, "батарея", "заряд", "аккумулятор")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                response = "Текущий уровень заряда аккумулятора: " + level + "%.";
            } else if (containsAny(lower, "пауза", "музыка", "плей", "стоп трек")) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                response = "Управление медиаплеером выполнено.";
            } else if (lower.contains("прочитай заметки") || lower.contains("покажи заметки")) {
                String notes = prefs.getString("notes_list", "Заметок пока нет.");
                response = "Сохраненные заметки:\n" + notes;
            } else if (lower.contains("очисти заметки") || lower.contains("удали заметки")) {
                prefs.edit().remove("notes_list").apply();
                response = "Все заметки удалены.";
            } else if (lower.startsWith("заметка")) {
                String newNote = text.replaceFirst("(?i)^заметка", "").trim();
                if (!newNote.isEmpty()) {
                    String existing = prefs.getString("notes_list", "");
                    String updated = existing.isEmpty() ? "• " + newNote : existing + "\n• " + newNote;
                    prefs.edit().putString("notes_list", updated).apply();
                    response = "Добавлена заметка: \"" + newNote + "\"";
                } else {
                    response = "Скажи: 'Заметка [твой текст]' чтобы добавить.";
                }
            } else if (containsAny(lower, "привет", "здравствуй", "хей")) {
                response = "Привет, " + userName + "! Я на связи.";
            } else {
                response = "Команда принята: " + text;
            }
        } catch (Exception e) {
            response = "Ошибка выполнения: " + e.getMessage();
        }

        addMessage(response, false);
        if (isVoiceEnabled && tts != null) {
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private boolean containsAny(String input, String... keywords) {
        for (String kw : keywords) {
            if (input.contains(kw)) return true;
        }
        return false;
    }

    private String openAppByName(String query) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = getPackageManager().queryIntentActivities(mainIntent, 0);

        for (ResolveInfo ri : pkgAppsList) {
            String appName = ri.loadLabel(getPackageManager()).toString().toLowerCase();
            if (appName.contains(query)) {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(ri.activityInfo.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    return "Запускаю " + ri.loadLabel(getPackageManager());
                }
            }
        }
        return null;
    }

    private void addMessage(String text, boolean isUser) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messageList.add(new ChatMessage(text, isUser, time));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerViewChat.smoothScrollToPosition(messageList.size() - 1);
        saveHistory();
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messageList) {
            sb.append(msg.isUser() ? "1" : "0")
              .append(";")
              .append(msg.getTime())
              .append(";")
              .append(msg.getMessage().replace("\n", " "))
              .append("\n");
        }
        prefs.edit().putString("chat_history_v2", sb.toString()).apply();
    }

    private void loadHistory() {
        String history = prefs.getString("chat_history_v2", "");
        if (!history.isEmpty()) {
            messageList.clear();
            String[] lines = history.split("\n");
            for (String line : lines) {
                String[] parts = line.split(";", 3);
                if (parts.length == 3) {
                    boolean isUser = parts[0].equals("1");
                    messageList.add(new ChatMessage(parts[2], isUser, parts[1]));
                }
            }
            chatAdapter.notifyDataSetChanged();
        }
    }

    private void showHelpDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_help, null);
        Button btnClose = view.findViewById(R.id.btnCloseHelp);
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.setContentView(view);
        dialog.show();
    }

    private void showSettingsDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(this);
        View view = getLayoutInflater().inflate(R.layout.dialog_settings, null);

        Switch switchTheme = view.findViewById(R.id.switchTheme);
        Switch switchVoice = view.findViewById(R.id.switchVoice);
        Switch switchWidget = view.findViewById(R.id.switchWidget);
        EditText etUserName = view.findViewById(R.id.etUserName);
        Button btnClearChat = view.findViewById(R.id.btnClearChat);
        Button btnSave = view.findViewById(R.id.btnSaveSettings);

        switchTheme.setChecked(prefs.getBoolean("dark_theme", true));
        switchVoice.setChecked(isVoiceEnabled);
        switchWidget.setChecked(prefs.getBoolean("widget_enabled", false));
        etUserName.setText(userName);

        btnClearChat.setOnClickListener(v -> {
            messageList.clear();
            chatAdapter.notifyDataSetChanged();
            saveHistory();
            dialog.dismiss();
            Toast.makeText(this, "История очищена", Toast.LENGTH_SHORT).show();
        });

        switchWidget.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Включите разрешение 'Отображение поверх других окон'", Toast.LENGTH_LONG).show();
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                }
            }
        });

        btnSave.setOnClickListener(v -> {
            boolean dark = switchTheme.isChecked();
            boolean widgetEnabled = switchWidget.isChecked();

            prefs.edit().putBoolean("dark_theme", dark).apply();
            prefs.edit().putBoolean("voice_enabled", switchVoice.isChecked()).apply();
            prefs.edit().putBoolean("widget_enabled", widgetEnabled).apply();
            isVoiceEnabled = switchVoice.isChecked();

            userName = etUserName.getText().toString().trim();
            if (userName.isEmpty()) userName = "Пользователь";
            prefs.edit().putString("user_name", userName).apply();

            if (widgetEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    startService(new Intent(this, LiraBackgroundService.class));
                }
            } else {
                stopService(new Intent(this, LiraBackgroundService.class));
            }

            AppCompatDelegate.setDefaultNightMode(dark ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            dialog.dismiss();
        });

        dialog.setContentView(view);
        dialog.show();
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA}, 1);
        }
    }

    private void vibrate(long durationMs) {
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(durationMs);
            }
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
