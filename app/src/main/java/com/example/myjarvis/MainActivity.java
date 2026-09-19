package com.example.myjarvis;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;
    private Button micButton;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    private Animation pulseAnimation;
    
    // Карта для хранения названий и пакетов приложений
    private Map<String, String> installedApps = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        micButton = findViewById(R.id.micButton);
        pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.pulse);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        if (!Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 2);
        } else {
            startService(new Intent(this, FloatingWidgetService.class));
        }
        
        // Сканируем приложения при запуске
        scanInstalledApps();

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) tts.setLanguage(new Locale("ru"));
        });

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { 
                inputField.setHint("Слушаю..."); 
                micButton.startAnimation(pulseAnimation);
                micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFF0000));
            }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { stopMicAnim(); }
            @Override public void onError(int error) { stopMicAnim(); appendMessage("Система", "Ошибка распознавания"); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                stopMicAnim();
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    appendMessage("USER", recognizedText);
                    processCommand(recognizedText);
                }
            }
        });

        sendButton.setOnClickListener(v -> {
            String text = inputField.getText().toString().trim();
            if (!text.isEmpty()) {
                appendMessage("USER", text);
                inputField.setText("");
                processCommand(text);
            }
        });

        micButton.setOnClickListener(v -> speechRecognizer.startListening(speechIntent));
        handleAutoListen(getIntent());
    }
    
    private void scanInstalledApps() {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> apps = pm.queryIntentActivities(mainIntent, 0);
        
        for (ResolveInfo info : apps) {
            String appName = info.loadLabel(pm).toString().toLowerCase();
            String packageName = info.activityInfo.packageName;
            installedApps.put(appName, packageName);
        }
        appendMessage("Система", "Просканировано " + installedApps.size() + " приложений.");
    }

    private void stopMicAnim() {
        micButton.clearAnimation();
        micButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF));
        inputField.setHint("Текстовая команда...");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleAutoListen(intent);
    }

    private void handleAutoListen(Intent intent) {
        if (intent != null && intent.getBooleanExtra("AUTO_LISTEN", false)) {
            micButton.performClick();
        }
    }

    private void processCommand(String command) {
        String lowerCmd = command.toLowerCase().trim();
        
        // Поиск в Ютубе
        if (lowerCmd.startsWith("найди в ютубе") || lowerCmd.startsWith("найди на ютубе")) {
            String query = lowerCmd.replace("найди в ютубе", "").replace("найди на ютубе", "").trim();
            searchYoutube(query);
            return;
        }
        
        // Поиск в Гугле / Интернете
        if (lowerCmd.startsWith("найди в гугле") || lowerCmd.startsWith("найди ")) {
            String query = lowerCmd.replace("найди в гугле", "").replace("найди ", "").trim();
            searchWeb(query);
            return;
        }

        // Написание сообщений в Телеграм
        if (lowerCmd.contains("напиши") && lowerCmd.contains("телеграм")) {
            // Вырезаем текст сообщения (все, что идет после слова "что" или просто берем остаток)
            String message = "";
            if (lowerCmd.contains(" что ")) {
                message = lowerCmd.substring(lowerCmd.indexOf(" что ") + 5).trim();
            } else {
                message = lowerCmd.substring(lowerCmd.indexOf("телеграм") + 8).trim();
            }
            sendTelegramMessage(message);
            return;
        }
        
        // Открытие приложений
        if (lowerCmd.startsWith("открой ") || lowerCmd.startsWith("запусти ")) {
            String targetApp = lowerCmd.replace("открой ", "").replace("запусти ", "").trim();
            
            // Ищем точное или частичное совпадение в сканированных приложениях
            String foundPackage = null;
            String foundAppName = null;
            
            for (Map.Entry<String, String> entry : installedApps.entrySet()) {
                if (entry.getKey().contains(targetApp) || targetApp.contains(entry.getKey())) {
                    foundPackage = entry.getValue();
                    foundAppName = entry.getKey();
                    break;
                }
            }
            
            if (foundPackage != null) {
                openApp(foundPackage, foundAppName);
            } else {
                respond("Я просканировал устройство, но не нашел приложения с названием " + targetApp);
            }
            return;
        }
        
        if (lowerCmd.contains("привет")) { respond("Приветствую, сэр."); } 
        else { respond("Команда не распознана: " + command); }
    }
    
    private void searchWeb(String query) {
        respond("Ищу " + query + " в интернете.");
        Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        startActivity(intent);
    }
    
    private void searchYoutube(String query) {
        respond("Ищу " + query + " на YouTube.");
        Intent intent = new Intent(Intent.ACTION_SEARCH);
        intent.setPackage("com.google.android.youtube");
        intent.putExtra("query", query);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(intent);
        } catch (Exception e) {
            searchWeb(query + " youtube"); // Резервный вариант, если Ютуб не установлен
        }
    }
    
    private void sendTelegramMessage(String text) {
        respond("Открываю Телеграм для отправки сообщения.");
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.setPackage("org.telegram.messenger");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        try {
            startActivity(intent);
        } catch (Exception e) {
            respond("Телеграм не установлен на устройстве.");
        }
    }
    
    private void openApp(String packageName, String appName) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            respond("Открываю " + appName);
            startActivity(intent);
        } else {
            respond("Не удалось запустить " + appName);
        }
    }

    private void respond(String text) {
        appendMessage("J.A.R.V.I.S.", text);
        if (tts != null) tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }

    private void appendMessage(String sender, String message) {
        String currentText = chatDisplay.getText().toString();
        chatDisplay.setText(currentText + "\n[" + sender + "] " + message);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    protected void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        if (speechRecognizer != null) { speechRecognizer.destroy(); }
        super.onDestroy();
    }
}
