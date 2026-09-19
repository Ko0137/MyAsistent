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
import android.provider.AlarmClock;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
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
        // Поддержка работы поверх экрана блокировки
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }

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

        int appCount = getLaunchableAppsCount();
        if (messageList.isEmpty()) {
            addMessage("L.I.R.A.: Все системы активны. Нажми '?' сверху для справки!", false, true);
        }
        addMessage("Инфо: Доступно для запуска приложений: " + appCount, false, false);

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

    private int getLaunchableAppsCount() {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgAppsList = getPackageManager().queryIntentActivities(mainIntent, 0);
        return pkgAppsList != null ? pkgAppsList.size() : 0;
    }

    private void checkFirstLaunchPrivacy() {
        boolean accepted = prefs.getBoolean("privacy_accepted", false);
        if (!accepted) {
            new AlertDialog.Builder(this)
                .setTitle("🔒 Конфиденциальность L.I.R.A.")
                .setMessage("Все данные обрабатываются локально на устройстве.")
                .setPositiveButton("Принять", (dialog, which) -> prefs.edit().putBoolean("privacy_accepted", true).apply())
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
        } else {
            setupSpeechRecognizer();
            startListening();
        }
    }

    private void stopListening() {
        isListening = false;
        statusText.setText("READY");
        statusText.setBackgroundColor(0xFF1B4D3E);
        micButton.setBackgroundResource(R.drawable.bg_mic_btn);
        
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { stopListening(); }
                @Override public void onError(int error) { 
                    stopListening();
                    setupSpeechRecognizer();
                }
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
        addMessage(text, true, true);
        String lower = text.toLowerCase().trim();
        String response = "";

        try {
            // 1. Проверка пользовательских кастомных команд
            String customResponse = getCustomCommandResponse(lower);
            if (customResponse != null) {
                response = customResponse;
            } 
            // 2. Будильник ("поставь будильник на 7:30" / "разбуди в 8 утра")
            else if (lower.contains("будильник") || lower.contains("разбуди")) {
                response = parseAndSetAlarm(lower);
            }
            // 3. Открытие приложений с живыми синонимами
            else if (lower.startsWith("открой ") || lower.startsWith("запусти ") || lower.startsWith("включи приложение ")) {
                String appQuery = lower.replaceFirst("^(открой|запусти|включи приложение)\\s+", "").trim();
                String launched = openAppByName(appQuery);
                if (launched != null) {
                    response = launched;
                } else if (appQuery.contains("камера")) {
                    startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    response = "Запускаю камеру.";
                } else if (appQuery.contains("браузер") || appQuery.contains("интернет")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
                    response = "Открываю браузер.";
                } else {
                    response = "Приложение \"" + appQuery + "\" не найдено.";
                }
            } 
            // 4. Фонарик (расширенные живые фразы)
            else if (containsAny(lower, "фонарик", "свет", "подсвети", "вспышка", "темно", "вруби свет")) {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String cameraId = camManager.getCameraIdList()[0];
                isTorchOn = !isTorchOn;
                camManager.setTorchMode(cameraId, isTorchOn);
                response = isTorchOn ? "Фонарик активирован." : "Фонарик выключен.";
            } 
            // 5. Батарея
            else if (containsAny(lower, "батарея", "заряд", "аккумулятор", "сколько осталось", "энергия")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                response = "Уровень заряда батареи: " + level + "%.";
            } 
            // 6. Плеер / Музыка
            else if (containsAny(lower, "пауза", "музыка", "плей", "трек", "стоп музыка")) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                response = "Медиаплеер переключен.";
            } 
            // 7. Заметки
            else if (lower.contains("прочитай заметки") || lower.contains("мои заметки")) {
                String notes = prefs.getString("notes_list", "Заметок пока нет.");
                response = "Ваши заметки:\n" + notes;
            } else if (lower.contains("очисти заметки")) {
                prefs.edit().remove("notes_list").apply();
                response = "Все заметки стерты.";
            } else if (lower.startsWith("заметка ") || lower.startsWith("запомни ")) {
                String newNote = text.replaceFirst("(?i)^(заметка|запомни)\\s+", "").trim();
                if (!newNote.isEmpty()) {
                    String existing = prefs.getString("notes_list", "");
                    String updated = existing.isEmpty() ? "• " + newNote : existing + "\n• " + newNote;
                    prefs.edit().putString("notes_list", updated).apply();
                    response = "Записано: \"" + newNote + "\"";
                } else {
                    response = "Что именно записать?";
                }
            } 
            // 8. Приветствия
            else if (containsAny(lower, "привет", "здравствуй", "хей", "как дела", "добрый день")) {
                response = "Приветствую, " + userName + "! Чем зайдемся?";
            } else {
                response = "Команда принята: " + text;
            }
        } catch (Exception e) {
            response = "Ошибка выполнения: " + e.getMessage();
        }

        addMessage(response, false, true);
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

    private String parseAndSetAlarm(String lower) {
        try {
            // Простейший поиск часов и минут в тексте (например, "на 7:30" или "на 8")
            String[] words = lower.split("\\s+");
            int hour = 7;
            int minute = 0;
            boolean foundTime = false;

            for (String w : words) {
                if (w.contains(":")) {
                    String[] parts = w.split(":");
                    hour = Integer.parseInt(parts[0].replaceAll("[^0-9]", ""));
                    minute = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                    foundTime = true;
                    break;
                }
            }

            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "L.I.R.A. Будильник");
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);

            return "Устанавливаю будильник на " + hour + ":" + (minute < 10 ? "0" + minute : minute);
        } catch (Exception e) {
            // Если не удалось распарсить точное время, просто открываем приложение будильника
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            return "Открываю настройки будильника.";
        }
    }

    private String getCustomCommandResponse(String lower) {
        String customCommands = prefs.getString("custom_commands_map", "");
        if (customCommands.isEmpty()) return null;

        String[] pairs = customCommands.split("---");
        for (String pair : pairs) {
            String[] kv = pair.split("::");
            if (kv.length == 2) {
                if (lower.contains(kv[0].trim().toLowerCase())) {
                    return kv[1].trim();
                }
            }
        }
        return null;
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

    private void addMessage(String text, boolean isUser, boolean saveToHistory) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messageList.add(new ChatMessage(text, isUser, time));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerViewChat.smoothScrollToPosition(messageList.size() - 1);
        if (saveToHistory) {
            saveHistory();
        }
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messageList) {
            if (msg.getMessage().startsWith("Инфо: Доступно")) continue;
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
        
        // Поле для добавления кастомной команды прямо в настройках
        EditText etCustomKey = view.findViewById(R.id.etCustomKey);
        EditText etCustomVal = view.findViewById(R.id.etCustomVal);
        Button btnAddCustom = view.findViewById(R.id.btnAddCustom);

        switchTheme.setChecked(prefs.getBoolean("dark_theme", true));
        switchVoice.setChecked(isVoiceEnabled);
        switchWidget.setChecked(prefs.getBoolean("widget_enabled", false));
        etUserName.setText(userName);

        if (btnAddCustom != null && etCustomKey != null && etCustomVal != null) {
            btnAddCustom.setOnClickListener(vCustom -> {
                String k = etCustomKey.getText().toString().trim();
                String val = etCustomVal.getText().toString().trim();
                if (!k.isEmpty() && !val.isEmpty()) {
                    String existing = prefs.getString("custom_commands_map", "");
                    String updated = existing.isEmpty() ? k + "::" + val : existing + "---" + k + "::" + val;
                    prefs.edit().putString("custom_commands_map", updated).apply();
                    Toast.makeText(this, "Команда добавлена!", Toast.LENGTH_SHORT).show();
                    etCustomKey.setText("");
                    etCustomVal.setText("");
                }
            });
        }

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
                    Intent intent = new Intent(Intent.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
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
