package com.example.myjarvis;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

public class PrivacyPolicyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);
        layout.setBackgroundColor(0xFF0A0A0C);

        TextView title = new TextView(this);
        title.setText("Политика конфиденциальности");
        title.setTextSize(20);
        title.setTextColor(0xFF00E5FF);
        title.setPadding(0, 0, 0, 24);
        layout.addView(title);

        TextView content = new TextView(this);
        content.setText(
            "1. Приложение «Лира» строго охраняет вашу конфиденциальность.\n\n" +
            "2. История сайтов, сессий и активности сторонних приложений НЕ сохраняется и полностью стирается при закрытии приложения.\n\n" +
            "3. Данные об устройстве используются исключительно в оперативной памяти во время сеанса.\n\n" +
            "4. Единственная сохраняемая информация — ваше имя для персонализации диалогов.\n\n" +
            "5. Сканирование установленных приложений производится «на лету» в реальном времени без записи в постоянную память."
        );
        content.setTextSize(14);
        content.setTextColor(0xFFFFFFFF);
        layout.addView(content);

        Button backBtn = new Button(this);
        backBtn.setText("Понятно");
        backBtn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF00E5FF));
        backBtn.setTextColor(0xFF0A0A0C);
        backBtn.setOnClickListener(v -> finish());
        
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.topMargin = 40;
        backBtn.setLayoutParams(params);
        layout.addView(backBtn);

        setContentView(layout);
    }
}
