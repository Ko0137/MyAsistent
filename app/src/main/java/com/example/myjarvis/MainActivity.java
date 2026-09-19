package com.example.myjarvis;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSIONS_REQUEST_CODE = 200;
    private TextToSpeech tts;
    private boolean isTtsInitialized = false;
    private SpeechRecognizer speechRecognizer;
    
    private LinearLayout chatLayout;
    private ScrollView chatScrollView;
    private EditText etInput;
    private int installedAppsCount = 0;
    private List<ApplicationInfo> installedApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isDarkTheme = prefs.getBoolean("theme_dark", true);
        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnSettings = findViewById(R.id.btnSettings);
        Button btnSend = findViewById(R.id.btnSend);
        Button btnMic = findViewById(R.id.btnMic);
        
        chatLayout = findViewById(R.id.chatLayout);
        chatScrollView = findViewById(R.id.chatScrollView);
        etInput = findViewById(R.id.etInput);

        checkAndRequestPermissions();

        // Сканируем установленные приложения
        scanInstalledApplications();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru", "RU"));
                applyVoicePreference();
                isTtsInitialized = true;
                
                boolean isQuietMode = prefs.getBoolean("quiet_mode", false);
                if (!isQuietMode) {
                    speakText("Привет, Костя! Система L.I.R.A. активна. На устройстве обнаружено " + installedAppsCount + " приложений.");
                }
            }
        });

        initSpeechRecognizer();

        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });

        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                addMessageToChat("Вы: " + text, Gravity.END);
                etInput.setText("");
                processUserCommand(text);
            }
        });

        btnMic.setOnClickListener(v -> startVoiceListening());

        addMessageToChat("L.I.R.A.: Привет, Костя! Сканирование завершено: найдено приложений — " + installedAppsCount + ".", Gravity.START);

        handleVoiceIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleVoiceIntent(intent);
    }

    private void handleVoiceIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("start_voice", false)) {
            startVoiceListening();
        }
    }

    private void checkAndRequestPermissions() {
        List<String> listPermissionsNeeded = new ArrayList<>();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            listPermissionsNeeded.add(Manifest.permission.RECORD_AUDIO);
        }
        if (!listPermissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, listPermissionsNeeded.toArray(new String[0]), PERMISSIONS_REQUEST_CODE);
        }

        // Запрос на отображение поверх других окон (для фонового шарика)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void scanInstalledApplications() {
        try {
            PackageManager pm = getPackageManager();
            installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA);
            installedAppsCount = installedApps.size();
        } catch (Exception e) {
            installedAppsCount = 0;
            installedApps = new ArrayList<>();
        }
    }

    private void initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            if (speechRecognizer != null) {
                speechRecognizer.destroy();
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
            speechRecognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {
                    Toast.makeText(MainActivity.this, "Слушаю вас...", Toast.LENGTH_SHORT).show();
                }
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}

                @Override
                public void onError(int error) {
                    Toast.makeText(MainActivity.this, "Не удалось распознать речь (ошибка " + error + ")", Toast.LENGTH_SHORT).show();
                }

                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    if (matches != null && !matches.isEmpty()) {
                        String spokenText = matches.get(0);
                        addMessageToChat("Вы (голосом): " + spokenText, Gravity.END);
                        processUserCommand(spokenText);
                    }
                }

                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle extras) {}
            });
        }
    }

    private void startVoiceListening() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Говорите команду...");
        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer();
            }
            speechRecognizer.startListening(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка запуска микрофона", Toast.LENGTH_SHORT).show();
        }
    }

    private void applyVoicePreference() {
        if (!isTtsInitialized || tts == null) return;
        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isFemale = prefs.getBoolean("voice_female", true);
        try {
            for (Locale locale : tts.getAvailableLanguages()) {
                if (locale.getLanguage().equals("ru")) tts.setLanguage(locale);
            }
        } catch (Exception ignored) {}
    }

    private void processUserCommand(String query) {
        String lower = query.toLowerCase();
        String response = "";
        boolean appOpened = false;

        // Проверяем, не просит ли пользователь открыть конкретное приложение
        if (lower.contains("открой")) {
            String appNameQuery = lower.replace("открой", "").trim();
            PackageManager pm = getPackageManager();
            for (ApplicationInfo app : installedApps) {
                String label = pm.getApplicationLabel(app).toString().toLowerCase();
                if (label.contains(appNameQuery)) {
                    Intent launchIntent = pm.getLaunchIntentForPackage(app.packageName);
                    if (launchIntent != null) {
                        startActivity(launchIntent);
                        response = "Открываю приложение: " + pm.getApplicationLabel(app);
                        appOpened = true;
                        break;
                    }
                }
            }
            if (!appOpened) {
                response = "Приложение \"" + appNameQuery + "\" не найдено среди " + installedAppsCount + " установленных.";
            }
        } else if (lower.contains("привет") || lower.contains("здарова")) {
            response = "Привет, Костя! Все системы в норме.";
        } else if (lower.contains("сколько приложений")) {
            response = "На твоем устройстве установлено " + installedAppsCount + " приложений.";
        } else if (lower.contains("время") || lower.contains("час")) {
            String time = android.text.format.DateFormat.format("HH:mm", new java.util.Date()).toString();
            response = "Текущее время: " + time;
        } else if (lower.contains("браузер") || lower.contains("интернет") || lower.contains("гугл")) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"));
            startActivity(intent);
            response = "Открываю браузер.";
        } else if (lower.contains("камера")) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivity(intent);
            response = "Открываю камеру.";
        } else {
            response = "Запрос принят: \"" + query + "\". Выполняю анализ!";
        }

        addMessageToChat("L.I.R.A.: " + response, Gravity.START);

        SharedPreferences prefs = getSharedPreferences("LiraPrefs", MODE_PRIVATE);
        boolean isQuietMode = prefs.getBoolean("quiet_mode", false);
        if (!isQuietMode) {
            speakText(response);
        }
    }

    private void speakText(String text) {
        if (isTtsInitialized && tts != null) {
            applyVoicePreference();
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    private void addMessageToChat(String message, int gravity) {
        TextView tv = new TextView(this);
        tv.setText(message);
        tv.setTextSize(15);
        tv.setPadding(14, 10, 14, 10);
        
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.gravity = gravity;
        params.setMargins(8, 4, 8, 4);
        tv.setLayoutParams(params);

        chatLayout.addView(tv);
        chatScrollView.post(() -> chatScrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyVoicePreference();
        // Когда возвращаемся в приложение — убираем фоновый сервис с шариком
        stopService(new Intent(this, LiraBackgroundService.class));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Когда сворачиваем приложение в фон — запускаем фоновый сервис с плавающим шариком
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(new Intent(this, LiraBackgroundService.class));
        } else {
            startService(new Intent(this, LiraBackgroundService.class));
        }
    }

    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        stopService(new Intent(this, LiraBackgroundService.class));
        super.onDestroy();
    }
}
