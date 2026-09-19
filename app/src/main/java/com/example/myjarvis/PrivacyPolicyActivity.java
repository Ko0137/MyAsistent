package com.example.myjarvis;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

public class PrivacyPolicyActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        ScrollView scrollView = new ScrollView(this);
        scrollView.setBackgroundColor(0xFF05050A);
        scrollView.setPadding(32, 32, 32, 32);

        TextView textView = new TextView(this);
        textView.setTextColor(0xFF00FF66);
        textView.setTextSize(14f);
        textView.setFontFamily("monospace");
        textView.setText(
            "ПОЛИТИКА КОНФИДЕНЦИАЛЬНОСТИ И БЕЗОПАСНОСТИ L.I.R.A.\n\n" +
            "1. ОСНОВНЫЕ ПОЛОЖЕНИЯ\n" +
            "Настоящее приложение разработано с приоритетом абсолютной безопасности пользователя. " +
            "Мы уважаем вашу конфиденциальность и гарантируем прозрачность работы всех систем.\n\n" +
            "2. СБОР И ОБРАБОТКА ДАННЫХ\n" +
            "• Приложение НЕ собирает, не сохраняет и не передает персональные данные на удаленные сервера.\n" +
            "• Все взаимодействия (текст, имя пользователя, настройки голоса) хранятся исключительно локально в памяти вашего устройства и удаляются при его очистке.\n" +
            "• У приложения нет сторонней аналитики, трекеров, рекламных модулей и скрытых сетевых запросов.\n\n" +
            "3. ИСПОЛЬЗУЕМЫЕ РАЗРЕШЕНИЯ\n" +
            "• Микрофон (RECORD_AUDIO): Используется только в момент вашей активной команды для ее распознавания.\n" +
            "• Окно поверх других приложений (SYSTEM_ALERT_WINDOW): Применяется исключительно по вашему желанию для вызова плавающего виджета.\n\n" +
            "Вы полностью контролируете работу ассистента, так как исходный код приложения полностью открыт."
        );

        scrollView.addView(textView);
        setContentView(scrollView);
    }
}
