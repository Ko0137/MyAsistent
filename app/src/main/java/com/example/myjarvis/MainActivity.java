package com.example.myjarvis;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.speech.RecognizerIntent;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.navigation.NavigationView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private DrawerLayout drawerLayout;
    private NavigationView navView;
    private ImageButton btnMenu, btnSend, btnVoice;
    private RecyclerView recyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private EditText etMessage;
    private static final int REQUEST_CODE_SPEECH = 100;
    private boolean isFlashlightOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        drawerLayout = findViewById(R.id.drawerLayout);
        navView = findViewById(R.id.navView);
        btnMenu = findViewById(R.id.btnMenu);
        recyclerView = findViewById(R.id.recyclerView);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(chatAdapter);

        // Открытие бокового меню по кнопке
        btnMenu.setOnClickListener(v -> drawerLayout.openDrawer(GravityCompat.START));

        // Обработка выбора разделов в боковом меню
        navView.setNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == android.R.id.home || id == R.id.nav_chat) {
                drawerLayout.closeDrawer(GravityCompat.START);
            } else if (id == R.id.nav_apps) {
                drawerLayout.closeDrawer(GravityCompat.START);
                addMessage("Напиши 'открой [название]', и я найду любое приложение на телефоне!", false);
            } else if (id == R.id.nav_tools) {
                drawerLayout.closeDrawer(GravityCompat.START);
                toggleFlashlight();
            } else if (id == R.id.nav_web) {
                drawerLayout.closeDrawer(GravityCompat.START);
                addMessage("Напиши 'найди [запрос]', чтобы выполнить поиск в интернете.", false);
            }
            return true;
        });

        // Приветственное сообщение
        addMessage("Привет! Я L.I.R.A. Твой нативный ассистент с полным функционалом. Умею открывать приложения, искать в сети и управлять устройством.", false);

        btnSend.setOnClickListener(v -> {
            String text = etMessage.getText().toString().trim();
            if (!text.isEmpty()) {
                processUserMessage(text);
                etMessage.setText("");
            }
        });

        btnVoice.setOnClickListener(v -> startVoiceRecognition());
    }

    private void addMessage(String text, boolean isUser) {
        String time = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());
        messageList.add(new ChatMessage(text, isUser, time));
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        recyclerView.scrollToPosition(messageList.size() - 1);
    }

    private void startVoiceRecognition() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Скажи команду...");
        try {
            startActivityForResult(intent, REQUEST_CODE_SPEECH);
        } catch (Exception e) {
            Toast.makeText(this, "Голосовой ввод не поддерживается", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH && resultCode == RESULT_OK && data != null) {
            ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (result != null && !result.isEmpty()) {
                processUserMessage(result.get(0));
            }
        }
    }

    private void processUserMessage(String text) {
        addMessage(text, true);
        String lower = text.toLowerCase();

        if (lower.contains("фонарик") || lower.contains("torch")) {
            toggleFlashlight();
        } else if (lower.contains("камера") || lower.contains("фото")) {
            openCamera();
        } else if (lower.startsWith("найди ") || lower.startsWith("поиск ")) {
            String query = text.replaceFirst("(?i)(найди|поиск)", "").trim();
            performWebSearch(query);
        } else if (lower.startsWith("открой ")) {
            String appName = text.replaceFirst("(?i)открой", "").trim();
            openAppByName(appName);
        } else {
            addMessage("Команда принята. Используй меню слева или попроси меня открыть приложение/найти информацию!", false);
        }
    }

    private void toggleFlashlight() {
        try {
            CameraManager cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
            if (cameraManager != null) {
                String cameraId = cameraManager.getCameraIdList()[0];
                isFlashlightOn = !isFlashlightOn;
                cameraManager.setTorchMode(cameraId, isFlashlightOn);
                addMessage(isFlashlightOn ? "Фонарик включен 🔦" : "Фонарик выключен", false);
            }
        } catch (Exception e) {
            addMessage("Не удалось переключить фонарик", false);
        }
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            addMessage("Открываю камеру 📷", false);
        } else {
            addMessage("Камера недоступна", false);
        }
    }

    private void performWebSearch(String query) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            startActivity(intent);
            addMessage("Ищу в интернете: " + query, false);
        } catch (Exception e) {
            addMessage("Ошибка при открытии браузера", false);
        }
    }

    private void openAppByName(String name) {
        PackageManager pm = getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(name);
        if (intent != null) {
            startActivity(intent);
            addMessage("Запускаю приложение...", false);
            return;
        }

        List<ResolveInfo> apps = pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER), 0);
        for (ResolveInfo app : apps) {
            String appLabel = app.loadLabel(pm).toString();
            if (appLabel.toLowerCase().contains(name.toLowerCase())) {
                Intent launchIntent = pm.getLaunchIntentForPackage(app.activityInfo.packageName);
                if (launchIntent != null) {
                    startActivity(launchIntent);
                    addMessage("Запускаю " + appLabel + " 🚀", false);
                    return;
                }
            }
        }
        addMessage("Приложение \"" + name + "\" не найдено", false);
    }
}
