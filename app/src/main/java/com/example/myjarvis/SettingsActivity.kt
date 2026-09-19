package com.example.myjarvis

import android.os.Bundle
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        // Плавная анимация появления окна снизу
        overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.fade_out)

        val backButton = findViewById<ImageButton>(R.id.backButton)
        backButton.setOnClickListener {
            finish()
        }
    }

    override fun finish() {
        super.finish()
        // Плавная анимация закрытия окна
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.slide_out_right)
    }
}
