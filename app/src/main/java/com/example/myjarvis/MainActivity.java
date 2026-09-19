package com.example.myjarvis;

import android.Manifest;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.BatteryManager;
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
    private Switch voiceMuteSwitch;
    private Switch genderSwitch; // Переключатель мужской/женский голос
    private TextToSpeech tts;
    private SpeechRecognizer speechRecognizer;
    
    private SharedPreferences prefs;
    private String userName;
    private boolean isWaitingForName = false;
    private boolean isVoiceMuted = false;
    private boolean isMaleVoice = false;
    
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
        voiceMuteSwitch = findViewById(R.id.voiceMuteSwitch);
        genderSwitch = findViewById(R.id.genderSwitch);
        Button policyBtn = findViewById(R.id.policyBtn);
        
        prefs = getSharedPreferences("JarvisPrefs", MODE_PRIVATE);
        userName = prefs.getString("UserName", null);
        isVoiceMuted = prefs.getBoolean("VoiceMuted", false);
        isMaleVoice = prefs.getBoolean("IsMaleVoice", false);

        if (voiceMuteSwitch != null) {
            voiceMuteSwitch.setChecked(isVoiceMuted);
            voiceMuteSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                isVoiceMuted = isChecked;
                prefs.edit().putBoolean("VoiceMuted", isVoiceMuted).apply();
                respond(isVoiceMuted ? "Голосовой ответ отключен." : "Голосовой ответ активирован.");
            });
        }

        if (genderSwitch != null) {
            genderSwitch.setChecked(isMaleVoice);
            genderSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                isMaleVoice = isChecked;
                prefs.edit().putBoolean("IsMaleVoice", isMaleVoice).apply();
                applyVoiceProfile();
                respond(isMaleVoice ? "Установлен мужской голос." : "Установлен женский голос.");
            });
        }

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
                loadVoices();
                
                if (userName == null) {
                    isWaitingForName = true;
                    respond("Привет. Я Лира, ваш защищенный ассистент. Как я могу к вам обращаться?");
                } else {
                    respond("Системы в норме. С возвращением, " + userName + ".");
                }
            }
        });
        
        voiceBtn.setOnClickListener(v -> changeVoiceCycle());

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        Intent speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { inputField.setHint("Слушаю команду..."); }
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { inputField.setHint("Команда для Лиры..."); }
            @Override public void onError(int error) { inputField.setHint("Команда для Лиры..."); }
            @Override public void onPartialResults(Bundle partialResults) {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override
            public void onResults(Bundle results) {
                inputField.setHint("Команда для Лиры...");
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
    
    private void loadVoices() {
        try {
            ruVoices.clear();
            for (Voice tmpVoice : tts.getVoices()) {
                if (tmpVoice.getLocale().getLanguage().equals("ru")) {
                    ruVoices.add(tmpVoice);
                }
            }
            applyVoiceProfile();
        } catch (Exception e) {}
    }

    private void applyVoiceProfile() {
        if (ruVoices.isEmpty()) return;
        // Пытаемся разделить на мужские/женские по имени тега голоса
        for (int i = 0; i < ruVoices.size(); i++) {
            String vName = ruVoices.get(i).getName().toLowerCase();
            boolean isMale = vName.contains("male") || vName.contains("mikhail") || vName.contains("pavel") || vName.contains("ru-ru-x-rum");
            if (isMale == isMaleVoice) {
                currentVoiceIndex = i;
                tts.setVoice(ruVoices.get(currentVoiceIndex));
                return;
            }
        }
        // Фолбек, если специфичный тег не найден
        tts.setVoice(ruVoices.get(0));
    }
    
    private void changeVoiceCycle() {
        if (ruVoices.isEmpty()) return;
        currentVoiceIndex = (currentVoiceIndex + 1) % ruVoices.size();
        tts.setVoice(ruVoices.get(currentVoiceIndex));
        respond("Голосовой профиль переключен.");
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
            respond("Приятно познакомиться, " + userName + ". Конфиденциальность активна.");
            return;
        }

        String lowerCmd = command.toLowerCase().trim();
        
        // Управление голосом
        if (lowerCmd.contains("отключи голос") || lowerCmd.contains("без звука")) {
            isVoiceMuted = true;
            if (voiceMuteSwitch != null) voiceMuteSwitch.setChecked(true);
            prefs.edit().putBoolean("VoiceMuted", true).apply();
            respond("Голосовые ответы отключены.");
            return;
        }
        if (lowerCmd.contains("включи голос") || lowerCmd.contains("разреши голос")) {
            isVoiceMuted = false;
            if (voiceMuteSwitch != null) voiceMuteSwitch.setChecked(false);
            prefs.edit().putBoolean("VoiceMuted", false).apply();
            respond("Голосовые ответы включены.");
            return;
        }

        // Триггер-слово "Лира"
        if (lowerCmd.startsWith("лира ") || lowerCmd.equals("лира")) {
            String subCmd = lowerCmd.replace("лира", "").trim();
            if (!subCmd.isEmpty()) {
                processCommand(subCmd);
            } else {
                respond("Я здесь, " + (userName != null ? userName : "пользователь") + ".");
            }
            return;
        }

        // Сценарии телефона: Фонарик
        if (lowerCmd.contains("включи фонарик") || lowerCmd.contains("фонарь вкл")) {
            setFlashlight(true);
            return;
        }
        if (lowerCmd.contains("выключи фонарик") || lowerCmd.contains("фонарь выкл")) {
            setFlashlight(false);
            return;
        }

        // Сценарий телефона: Заряд батареи
        if (lowerCmd.contains("заряд") || lowerCmd.contains("батарея") || lowerCmd.contains("сколько процентов")) {
            checkBatteryLevel();
            return;
        }

        // Умный запуск приложений по ключевым словам (например, "включи яндекс музыку", "открой музыку")
        if (lowerCmd.startsWith("открой ") || lowerCmd.startsWith("запусти ") || lowerCmd.startsWith("включи ")) {
            String appSearch = lowerCmd.replace("открой ", "")
                                       .replace("запусти ", "")
                                       .replace("включи ", "").trim();
            smartOpenApp(appSearch);
            return;
        }

        // Поиск в интернете
        if (lowerCmd.startsWith("найди ")) {
            String query = lowerCmd.replace("найди ", "").trim();
            respond("Ищу в сети: " + query);
            Intent intent = new Intent(Intent.ACTION_WEB_SEARCH);
            intent.putExtra(SearchManager.QUERY, query);
            startActivity(intent);
            return;
        }

        // Звонки
        if (lowerCmd.startsWith("позвони")) {
            String number = lowerCmd.replaceAll("[^0-9+]", "");
            if (!number.isEmpty()) {
                respond("Набираю номер...");
                Intent intent = new Intent(Intent.ACTION_DIAL);
                intent.setData(Uri.parse("tel:" + number));
                startActivity(intent);
            } else {
                respond("Укажите номер телефона для вызова.");
            }
            return;
        }
        
        if (lowerCmd.contains("привет")) { 
            respond("Здравствуйте, " + (userName != null ? userName : "") + "."); 
        } else { 
            respond("Команда не распознана. Попробуйте: 'Лира, включи Яндекс Музыку', 'Лира, включи фонарик' или 'Лира, заряд батареи'."); 
        }
    }

    // Умный поиск приложений с подсказкой вариантов при частичном совпадении
    private void smartOpenApp(String query) {
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> pkgList = getPackageManager().queryIntentActivities(mainIntent, 0);

        List<String> matchedNames = new ArrayList<>();
        List<String> matchedPackages = new ArrayList<>();

        // Ключевые маппинги для удобства (например, "музыка" -> Яндекс Музыка или Spotify)
        String searchKey = query;
        if (query.equals("музыка") || query.equals("я музыка")) {
            searchKey = "яндекс музыка";
        }

        for (ResolveInfo resolveInfo : pkgList) {
            String appLabel = resolveInfo.loadLabel(getPackageManager()).toString().toLowerCase();
            // Проверяем точное или частичное вхождение ключевых слов (например, "яндекс" и "музыка")
            boolean isMatch = true;
            String[] keywords = searchKey.split(" ");
            for (String kw : keywords) {
                if (!appLabel.contains(kw)) {
                    isMatch = false;
                    break;
                }
            }

            if (isMatch || appLabel.contains(searchKey)) {
                matchedNames.add(resolveInfo.loadLabel(getPackageManager()).toString());
                matchedPackages.add(resolveInfo.activityInfo.packageName);
            }
        }

        if (matchedPackages.size() == 1) {
            // Найдено ровно одно приложение — запускаем сразу
            String appName = matchedNames.get(0);
            String pkgName = matchedPackages.get(0);
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(pkgName);
            if (launchIntent != null) {
                respond("Запускаю " + appName);
                startActivity(launchIntent);
                return;
            }
        } else if (matchedPackages.size() > 1) {
            // Найдено несколько вариантов — перечисляем их пользователю
            StringBuilder sb = new_builder_suggestions(matchedNames);
            respond("Найдено несколько вариантов: " + sb.toString() + ". Уточните название.");
            return;
        }

        // Если точных совпадений нет, ищем просто по первому слову или показываем ошибку
        respond("Приложение по запросу '" + query + "' не найдено на устройстве.");
    }

    private StringBuilder new_builder_suggestions(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(names.size(), 3); i++) {
            sb.append(names.get(i));
            if (i < Math.min(names.size(), 3) - 1) sb.append(", ");
        }
        return sb;
    }

    // Сценарий: Управление фонариком
    private void setFlashlight(boolean turnOn) {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
            String cameraId = cameraManager.getCameraIdList()[0];
            cameraManager.setTorchMode(cameraId, turnOn);
            respond(turnOn ? "Фонарик включен." : "Фонарик выключен.");
        } catch (Exception e) {
            respond("Не удалось управлять фонариком на этом устройстве.");
        }
    }

    // Сценарий: Проверка заряда батареи
    private void checkBatteryLevel() {
        android.content.IntentFilter ifilter = new android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = registerReceiver(null, ifilter);
        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            float batteryPct = level * 100 / (float)scale;
            respond("Заряд батареи составляет " + (int)batteryPct + " процентов.");
        } else {
            respond("Не удалось получить данные о батарее.");
        }
    }
    
    private void respond(String text) {
        appendMessage("L.I.R.A.", text);
        if (tts != null && !isVoiceMuted) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
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
