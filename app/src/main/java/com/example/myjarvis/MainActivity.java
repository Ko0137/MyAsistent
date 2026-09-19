package com.example.myjarvis;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView chatDisplay;
    private EditText inputField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatDisplay = findViewById(R.id.chatDisplay);
        inputField = findViewById(R.id.inputField);
        Button sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> {
            String userText = inputField.getText().toString().trim();
            if (!userText.isEmpty()) {
                appendMessage("Ты", userText);
                inputField.setText("");
                processCommand(userText);
            }
        });
    }

    private void processCommand(String command) {
        // Здесь мы потом подключим ИИ или другие функции
        appendMessage("Jarvis", "Команда получена: " + command);
    }

    private void appendMessage(String sender, String message) {
        String currentText = chatDisplay.getText().toString();
        chatDisplay.setText(currentText + "\n" + sender + ": " + message);
    }
}
