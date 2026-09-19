package com.example.myjarvis

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settingsButton = findViewById<ImageButton>(R.id.settingsButton)
        val chatDisplay = findViewById<TextView>(R.id.chatDisplay)
        val inputField = findViewById<EditText>(R.id.inputField)
        val sendButton = findViewById<Button>(R.id.sendButton)

        // Всплывающее меню настроек по клику на шестеренку (как по правому клику на ПК)
        settingsButton.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menu.add(0, 1, 0, "🌙 Темная тема (Вкл)")
            popup.menu.add(0, 2, 1, "👩 Женский голос (LIRA)")
            popup.menu.add(0, 3, 2, "🔕 Тихий режим")
            popup.menu.add(0, 4, 3, "❌ Закрыть меню")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        Toast.makeText(this, "Темная тема активна", Toast.LENGTH_SHORT).show()
                        true
                    }
                    2 -> {
                        Toast.makeText(this, "Голос LIRA изменен", Toast.LENGTH_SHORT).show()
                        true
                    }
                    3 -> {
                        Toast.makeText(this, "Тихий режим включен", Toast.LENGTH_SHORT).show()
                        true
                    }
                    4 -> true
                    else -> false
                }
            }
            popup.show()
        }

        sendButton.setOnClickListener {
            val text = inputField.text.toString().trim()
            if (text.isNotEmpty()) {
                chatDisplay.append("\nВы: $text\n")
                chatDisplay.append("[L.I.R.A.]: Обрабатываю запрос...\n")
                inputField.setText("")
            }
        }
    }
}
