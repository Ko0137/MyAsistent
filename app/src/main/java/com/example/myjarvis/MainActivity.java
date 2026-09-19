package com.example.myjarvis;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private TextView chatDisplay;
    private EditText inputField;
    private ScrollView scrollView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        try {
            setContentView(R.layout.activity_main);

            chatDisplay = findViewById(R.id.chatDisplay);
            inputField = findViewById(R.id.inputField);
            scrollView = findViewById(R.id.scrollView);
            Button sendButton = findViewById(R.id.sendButton);

            if (sendButton != null) {
                sendButton.setOnClickListener(v -> {
                    String text = inputField.getText().toString().trim();
                    if (!text.isEmpty()) {
                        appendLog("[USER] " + text);
                        inputField.setText("");
                        appendLog("[L.I.R.A.] Команда получена.");
                    }
                });
            }

            appendLog("[L.I.R.A.] Система запущена успешно.");
        } catch (Exception e) {
            // Если ошибка случится, она не уронит приложение молча
            e.printStackTrace();
        }
    }

    private void appendLog(String text) {
        if (chatDisplay != null) {
            chatDisplay.append(text + "\n");
        }
        if (scrollView != null) {
            scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
        }
    }
}
