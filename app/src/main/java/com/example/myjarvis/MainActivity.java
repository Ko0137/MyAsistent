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

        int appCount = getLaunchableAppsCount();
        if (messageList.isEmpty()) {
            addMessage("L.I.R.A.: Все системы активны. Нажми '?' сверху для списка команд!", false, true);
        }
        addMessage("Инфо: Найдено " + appCount + " приложений для голосового запуска.", false, false);

        sendButton.setOnClickListener(v -> {
            vibrate(30);
            String text = inputMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                handleRawInput(text);
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

        btnSettings.setOnClickListener(v -> { vibrate(30); showSettingsDialog(); });
        btnHelp.setOnClickListener(v -> { vibrate(30); showHelpDialog(); });
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
                .setMessage("Все данные обрабатываются локально.\nПродолжая, вы соглашаетесь с Политикой.")
                .setPositiveButton("Принять", (dialog, which) -> prefs.edit().putBoolean("privacy_accepted", true).apply())
                .setCancelable(false).show();
        }
    }

    private void startListening() {
        if (speechRecognizer != null) {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
            speechRecognizer.startListening(intent);
            isListening = true;
            statusText.setText("LISTENING...");
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
        if (speechRecognizer != null) speechRecognizer.stopListening();
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) { isListening = true; }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() { stopListening(); }
                @Override public void onError(int error) { stopListening(); setupSpeechRecognizer(); }
                @Override public void onResults(Bundle results) {
                    stopListening();
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        handleRawInput(matches.get(0));
                    }
                }
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}
            });
        }
    }

    private void handleRawInput(String text) {
        addMessage(text, true, true);
        String lower = text.toLowerCase().trim();
        
        if (lower.contains(" и ") && !lower.startsWith("заметка") && !lower.startsWith("запиши")) {
            String[] commands = lower.split(" и ");
            StringBuilder combinedResponse = new StringBuilder();
            for (String cmd : commands) {
                String res = processSingleCommand(cmd.trim());
                if (!res.isEmpty()) combinedResponse.append(res).append(". ");
            }
            respond(combinedResponse.toString().trim());
        } else {
            String res = processSingleCommand(lower);
            respond(res);
        }
    }

    private String processSingleCommand(String lower) {
        try {
            if (containsAny(lower, "открой", "запусти", "включи", "старт", "давай посмотрим")) {
                String appQuery = lower.replaceAll("^(открой|запусти|включи|старт|давай посмотрим)\\s+", "").trim();
                String launched = openAppByName(appQuery);
                if (launched != null) return launched;
                
                if (containsAny(appQuery, "камеру", "фотку", "снимай")) {
                    startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));
                    return "Открываю камеру";
                } else if (containsAny(appQuery, "браузер", "интернет", "хром")) {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://google.com")));
                    return "Открываю браузер";
                }
                return "Приложение \"" + appQuery + "\" не найдено";
            }
            else if (containsAny(lower, "будильник", "разбуди", "заведи", "поставь на")) {
                Pattern p = Pattern.compile("(\\d{1,2})[:\\s-](\\d{2})|(\\d{1,2})\\s+(часов|часа|час)");
                Matcher m = p.matcher(lower);
                if (m.find()) {
                    int hour = Integer.parseInt(m.group(1) != null ? m.group(1) : m.group(3));
                    int minute = m.group(2) != null ? Integer.parseInt(m.group(2)) : 0;
                    
                    Intent alarmIntent = new Intent(AlarmClock.ACTION_SET_ALARM);
                    alarmIntent.putExtra(AlarmClock.EXTRA_HOUR, hour);
                    alarmIntent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
                    alarmIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                    alarmIntent.putExtra(AlarmClock.EXTRA_MESSAGE, "Будильник L.I.R.A.");
                    
                    if (alarmIntent.resolveActivity(getPackageManager()) != null) {
                        startActivity(alarmIntent);
                        return "Будильник установлен на " + String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
                    }
                    return "В системе нет приложения часов.";
                }
                return "Не поняла время. Скажи 'Будильник на 7:30' или 'Разбуди в 8 часов'.";
            }
            else if (containsAny(lower, "фонарик", "свет", "подсвети", "вспышка", "люмос", "темно")) {
                CameraManager camManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
                String cameraId = camManager.getCameraIdList()[0];
                isTorchOn = !isTorchOn;
                camManager.setTorchMode(cameraId, isTorchOn);
                return isTorchOn ? "Свет включен" : "Свет выключен";
            }
            else if (containsAny(lower, "батарея", "заряд", "аккумулятор", "сколько процентов", "питание")) {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                int level = batteryStatus != null ? batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) : -1;
                return "Уровень заряда: " + level + "%";
            }
            else if (containsAny(lower, "пауза", "музыка", "плей", "стоп трек", "играй", "заткнись")) {
                AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                audioManager.dispatchMediaKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE));
                return "Медиаплеер обновлен";
            }
            else if (containsAny(lower, "прочитай заметки", "покажи заметки", "что я записал", "мои записи")) {
                return "Твои записи:\n" + prefs.getString("notes_list", "Пусто.");
            }
            else if (containsAny(lower, "очисти заметки", "удали заметки", "сотри блокнот", "забудь все")) {
                prefs.edit().remove("notes_list").apply();
                return "Блокнот очищен";
            }
            else if (containsAny(lower, "заметка", "запиши", "добавь в блокнот")) {
                String newNote = lower.replaceFirst("(?i)^(заметка|запиши|добавь в блокнот)\\s+", "").trim();
                if (!newNote.isEmpty()) {
                    String existing = prefs.getString("notes_list", "");
                    String updated = existing.isEmpty() ? "• " + newNote : existing + "\n• " + newNote;
                    prefs.edit().putString("notes_list", updated).apply();
                    return "Сохранила: " + newNote;
                }
                return "Что именно записать?";
            }
            else if (containsAny(lower, "привет", "здравствуй", "хей", "лира", "ты тут")) {
                return "Я на связи, " + userName + ".";
            }
            
            return "Команда принята: " + lower;
        } catch (Exception e) {
            return "Ошибка: " + e.getMessage();
        }
    }

    private void respond(String text) {
        if (text.isEmpty()) return;
        addMessage(text, false, true);
        if (isVoiceEnabled && tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
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

    private void addMessage(String text, boolean isUser, boolean saveToHistory) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messageList.add(new ChatMessage(text, isUser, time));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerViewChat.smoothScrollToPosition(messageList.size() - 1);
        if (saveToHistory) saveHistory();
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messageList) {
            if (msg.getMessage().startsWith("Инфо: Найдено")) continue;
            sb.append(msg.isUser() ? "1" : "0").append(";")
              .append(msg.getTime()).append(";")
              .append(msg.getMessage().replace("\n", " ")).append("\n");
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
                if (parts.length == 3) messageList.add(new ChatMessage(parts[2], parts[0].equals("1"), parts[1]));
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
            if (isChecked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Включите разрешение 'Отображение поверх других окон'", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                startActivity(intent);
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

            Intent serviceIntent = new Intent(this, LiraBackgroundService.class);
            if (widgetEnabled) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                    ContextCompat.startForegroundService(this, serviceIntent);
                }
            } else {
                stopService(serviceIntent);
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
