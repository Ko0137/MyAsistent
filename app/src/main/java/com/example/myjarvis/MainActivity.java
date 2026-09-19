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
import android.util.Log;
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
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private Random random = new Random();

    private boolean isListening = false;
    private boolean isVoiceEnabled = true;
    private boolean isTorchOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED 
                               | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
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
        userName = prefs.getString("user_name", "Костя");
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
            addMessage("L.I.R.A.: Все системы активны. Готова к работе, " + userName + ".", false, true);
        }
        addMessage("Инфо: Найдено " + appCount + " приложений, доступных для голосового запуска.", false, false);

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

    private String getRandomPhrase(String... phrases) {
        return phrases[random.nextInt(phrases.length)];
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
            String customResponse = checkCustomCommands(lower);
            if (customResponse != null) {
                response = customResponse;
            }
            else if (lower.startsWith("создай команду ")) {
                Matcher m = Pattern.compile("создай команду (.*) ответ (.*)").matcher(lower);
                if (m.find()) {
                    String trigger = m.group(1).trim();
                    String answer = m.group(2).trim();
                    prefs.edit().putString("cmd_" + trigger, answer).apply();
                    response = getRandomPhrase(
                            "Запомнила. Теперь на фразу '" + trigger + "' я отвечу соответствующе.",
                            "Команда сохранена в базу, " + userName + ".",
                            "Готово. Добавила новую команду в свой арсенал."
                    );
                } else {
                    response = "Формат неправильный. Скажи: 'Создай команду [фраза] ответ [текст ответа]'.";
                }
            } 
            else if (lower.contains("будильник")) {
                Matcher mAlarm = Pattern.compile("будильник.*?на (\\d{1,2})[\\s:](\\d{2})").matcher(lower);
                if (mAlarm.find()) {
                    int hour = Integer.parseInt(mAlarm.group(1));
                    int min = Integer.parseInt(mAlarm.group(2));
                    Intent i = new Intent(AlarmClock.ACTION_SET_ALARM);
                    i.putExtra(AlarmClock.EXTRA_HOUR, hour);
                    i.putExtra(AlarmClock.EXTRA_MINUTES, min);
                    i.putExtra(AlarmClock.EXTRA_SKIP_UI, true); 
                    startActivity(i);
                    response = getRandomPhrase(
                            "Будильник заведен на " + hour + ":" + String.format("%02d", min) + ".", 
                            "Сделано. Разбужу в " + hour + ":" + String.format("%02d", min) + ".",
                            "Готово, " + userName + ". Будильник активирован."
                    );
                } else {
                    response = "Не совсем поняла время. Скажи, например: 'поставь будильник на 7 30'.";
                }
            }
            else if (lower.startsWith("открой ") || lower.startsWith("запусти ")) {
                String appQuery = lower.replaceFirst("^(открой|запусти)\\s+", "").trim();
                String launched = openAppByName(appQuery);
                if (launched != null) {
                    response = launched;
                } else if (appQuery.contains("камера")) {
                    startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    response = getRandomPhrase("Открываю камеру.", "Камера запущена.", "Включаю объектив.");
                } else if (appQuery.contains("браузер")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
                    response = getRandomPhrase("Открываю браузер.", "Запускаю веб-поиск.", "Секунду, открываю интернет.");
                } else {
                    response = "Приложение \"" + appQuery + "\" не найдено в системе.";
                }
            } 
            else if (containsAny(lower, "фонарик", "свет", "подсвети", "вспышка")) {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String cameraId = camManager.getCameraIdList()[0];
                isTorchOn = !isTorchOn;
                camManager.setTorchMode(cameraId, isTorchOn);
                response = isTorchOn ? 
                        getRandomPhrase("Да будет свет!", "Включаю фонарик.", "Освещаю путь, " + userName + ".") : 
                        getRandomPhrase("Фонарик выключен.", "Свет погашен.", "Отключаю вспышку.");
            } 
            else if (containsAny(lower, "батарея", "заряд", "аккумулятор")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                response = getRandomPhrase(
                        "Заряд батареи: " + level + "%.", 
                        "Осталось " + level + "% энергии.", 
                        "Аккумулятор заряжен на " + level + "%."
                );
            } 
            else if (containsAny(lower, "пауза", "музыка", "плей", "стоп трек")) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                response = getRandomPhrase("Управление плеером выполнено.", "Переключаю воспроизведение.", "Сделано.");
            } 
            else if (lower.contains("прочитай заметки") || lower.contains("покажи заметки")) {
                String notes = prefs.getString("notes_list", "Заметок пока нет.");
                response = "Ваши записи:\n" + notes;
            } else if (lower.contains("очисти заметки") || lower.contains("удали заметки")) {
                prefs.edit().remove("notes_list").apply();
                response = getRandomPhrase("Все заметки удалены.", "Память очищена.", "Записи стерты.");
            } else if (lower.startsWith("заметка")) {
                String newNote = text.replaceFirst("(?i)^заметка", "").trim();
                if (!newNote.isEmpty()) {
                    String existing = prefs.getString("notes_list", "");
                    String updated = existing.isEmpty() ? "• " + newNote : existing + "\n• " + newNote;
                    prefs.edit().putString("notes_list", updated).apply();
                    response = getRandomPhrase("Добавила: \"" + newNote + "\"", "Сохранила заметку.", "Записала.");
                } else {
                    response = "Скажи: 'Заметка [твой текст]' чтобы я могла её сохранить.";
                }
            } 
            else if (containsAny(lower, "привет", "здравствуй", "хей", "добрый день")) {
                response = getRandomPhrase(
                        "Привет, " + userName + "! Я на связи.",
                        "Здравствуйте! Чем займемся?",
                        "Системы активны. Что нужно сделать?",
                        "Привет! Я готова помочь."
                );
            } else {
                response = getRandomPhrase(
                        "Команда принята: " + text,
                        "Услышала: " + text + ". Но пока не знаю, что с этим делать.",
                        "Я записала: " + text
                );
            }
        } catch (Exception e) {
            response = "Ошибка при выполнении: " + e.getMessage();
        }

        addMessage(response, false, true);
        if (isVoiceEnabled && tts != null) {
            tts.speak(response, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }
    
    private String checkCustomCommands(String input) {
        Map<String, ?> allEntries = prefs.getAll();
        for (Map.Entry<String, ?> entry : allEntries.entrySet()) {
            if (entry.getKey().startsWith("cmd_")) {
                String trigger = entry.getKey().substring(4);
                if (input.equals(trigger)) {
                    return entry.getValue().toString();
                }
            }
        }
        return null;
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
                    return getRandomPhrase(
                            "Запускаю " + ri.loadLabel(getPackageManager()) + ".", 
                            "Открываю " + ri.loadLabel(getPackageManager()) + ".",
                            "Секунду, запускаю приложение."
                    );
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
            if (msg.getMessage().startsWith("Инфо: Найдено")) continue;
            
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
            if (userName.isEmpty()) userName = "Костя";
            prefs.edit().putString("user_name", userName).apply();

            if (widgetEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    try {
                        startService(new Intent(this, LiraBackgroundService.class));
                    } catch (Exception e) {
                        Toast.makeText(this, "Ошибка запуска виджета: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        Log.e("LIRA", "Ошибка запуска сервиса", e);
                    }
                }
            } else {
                try {
                    stopService(new Intent(this, LiraBackgroundService.class));
                } catch (Exception e) {
                    Log.e("LIRA", "Ошибка остановки сервиса", e);
                }
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
    protected void onResume() {
        super.onResume();
        if (prefs != null && prefs.getBoolean("widget_enabled", false)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                try {
                    startService(new Intent(this, LiraBackgroundService.class));
                } catch (Exception e) {
                    Log.e("LIRA", "Сбой при возобновлении виджета: " + e.getMessage());
                }
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
