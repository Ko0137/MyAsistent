package com.example.myjarvis;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;
    private Button micButton;
    private Switch widgetSwitch;
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    
    private SharedPreferences prefs;
    private String userName;
    private boolean isWaitingForName = false;
    
    private List<Voice> ruVoices = new ArrayList<>();
    private int currentVoiceIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        scrollView = findViewById(R.id.scrollView);
        Button sendButton = findViewById(R.id.sendButton);
        Button voiceBtn = findViewById(R.id.voiceBtn);
        micButton = findViewById(R.id.micButton);
        widgetSwitch = findViewById(R.id.widgetSwitch);
        Button policyBtn = findViewById(R.id.policyBtn);
        
        prefs = getSharedPreferences("JarvisPrefs", MODE_PRIVATE);
        userName = prefs.getString("UserName", null);

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }

        if (policyBtn != null) {
            policyBtn.setOnClickListener(v -> startActivity(new Intent(this, PrivacyPolicyActivity.class)));
        }

        widgetSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                if (!Settings.canDrawOverlays(this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, 2);
                    widgetSwitch.setChecked(false);
                } else {
                    startService(new Intent(this, FloatingWidgetService.class));
                }
            } else {
                stopService(new Intent(this, FloatingWidgetService.class));
            }
        });

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("ru"));
                loadAndSelectVoice();
                
                if (userName == null) {
                    isWaitingForName = true;
                    respond("Привет. Я Лира, твой персональный ассистент. Данные не сохраняются. Как я могу к тебе обращаться?");
                } else {
                    respond("Системы в норме. С возвращением, " + userName + ".");
                }
            }
        });
        
        voiceBtn.setOnClickListener(v -> changeVoice());

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { inputField.setHint("Слушаю..."); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { inputField.setHint("Текстовая команда..."); }
            @Override public void onError(int error) { inputField.setHint("Текстовая команда..."); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                inputField.setHint("Текстовая команда...");
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
    
    private void loadAndSelectVoice() {
        try {
            for (Voice tmpVoice : tts.getVoices()) {
                if (tmpVoice.getLocale().getLanguage().equals("ru")) {
                    ruVoices.add(tmpVoice);
                }
            }
            if (!ruVoices.isEmpty()) {
                tts.setVoice(ruVoices.get(0));
            }
        } catch (Exception e) {}
    }
    
    private void changeVoice() {
        if (ruVoices.isEmpty()) return;
        currentVoiceIndex = (currentVoiceIndex + 1) % ruVoices.size();
        tts.setVoice(ruVoices.get(currentVoiceIndex));
        respond("Голосовой модуль изменен.");
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
        if (isWaitingForName) {
            userName = command;
        prefs.edit().putString("UserName", userName).apply();
            isWaitingForName = false;
            respond("Рад знакомству, " + userName + ". Протоколы инициализации завершены.");
            return;
        }

        String lowerCmd = command.toLowerCase().trim();
        
        // 1. Команда открытия приложений ("открой [название]", "запусти [название]")
        if (lowerCmd.startsWith("открой ") || lowerCmd.startsWith("запусти ")) {
            String appSearch = lowerCmd.replace("открой ", "").replace("запусти ", "").trim();
            if (openAppByName(appSearch)) {
                respond("Открываю " + appSearch);
            } else {
                respond("Не удалось найти приложение с именем " + appSearch);
            }
            return;
        }

        // 2. Сканирование приложений
        if (lowerCmd.contains("сколько приложений") || lowerCmd.contains("какие приложения")) {
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> pkgList = getPackageManager().queryIntentActivities(mainIntent, 0);
            respond("На устройстве найдено " + pkgList.size() + " приложений. Данные не сохраняются.");
            return;
        }

        // 3. Звонки
        if (lowerCmd.startsWith("позвони")) {
            String number = lowerCmd.replaceAll("[^0-9+]", "");
            if (!number.isEmpty()) {
                respond("Набираю номер " + number);
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + number));
                startActivity(intent);
            } else {
                respond("Укажите номер телефона для вызова.");
            }
            return;
        }
        
        // 4. Поиск в интернете
        if (lowerCmd.startsWith("найди ")) {
            String query = lowerCmd.replace("найди ", "").trim();
            respond("Выполняю поиск: " + query);
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, query);
            startActivity(intent);
            return;
        }

        // 5. Отправка текста
        if (lowerCmd.contains("напиши")) {
            String message = lowerCmd.replace("напиши ", "").trim();
            respond("Открываю приложения для отправки...");
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, message);
            startActivity(Intent.createChooser(intent, "Отправить через"));
            return;
        }
        
        if (lowerCmd.contains("привет")) { 
            respond("Здравствуйте, " + userName + "."); 
        } else { 
            respond("Команда не распознана. Используйте: 'Открой...', 'Найди...', 'Позвони...' или 'Сколько приложений'."); 
        }
    }

    // Метод для интеллектуального поиска и запуска нужного приложения по имени
    private boolean openAppByName(String query) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgList = getPackageManager().queryIntentActivities(mainIntent, 0);

        for (ResolveInfo resolveInfo : pkgList) {
            String appLabel = resolveInfo.loadLabel(getPackageManager()).toString().toLowerCase();
            if (appLabel.contains(query)) {
                String packageName = resolveInfo.activityInfo.packageName;
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void respond(String text) {
        appendMessage("L.I.R.A.", text);
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
