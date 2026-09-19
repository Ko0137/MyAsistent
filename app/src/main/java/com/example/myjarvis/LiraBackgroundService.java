package com.example.myjarvis;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.Toast;

public class LiraBackgroundService extends Service {

    private WindowManager windowManager;
    private View floatingView;
    private WindowManager.LayoutParams params;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            
            // Надуваем макет плавающей кнопки (используем существующий или создаем программно)
            floatingView = LayoutInflater.from(this).inflate(R.layout.widget_floating, null);

            int layoutParamType;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                layoutParamType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                layoutParamType = WindowManager.LayoutParams.TYPE_PHONE;
            }

            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    layoutParamType,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            params.gravity = Gravity.TOP | Gravity.START;
            params.x = 100;
            params.y = 300;

            windowManager.addView(floatingView, params);

            // Клик по плавающей иконке открывает приложение
            ImageButton fab = floatingView.findViewById(R.id.fabWidget);
            if (fab != null) {
                fab.setOnClickListener(v -> {
                    Intent intent = new Intent(this, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                });

                // Реализация перетаскивания пальцем
                fab.setOnTouchListener(new View.OnTouchListener() {
                    private int initialX;
                    private int initialY;
                    private float initialTouchX;
                    private float initialTouchY;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        switch (event.getAction()) {
                            case MotionEvent.ACTION_DOWN:
                                initialX = params.x;
                                initialY = params.y;
                                initialTouchX = event.getRawX();
                                initialTouchY = event.getRawY();
                                return true;
                            case MotionEvent.ACTION_MOVE:
                                params.x = initialX + (int) (event.getRawX() - initialTouchX);
                                params.y = initialY + (int) (event.getRawY() - initialTouchY);
                                windowManager.updateViewLayout(floatingView, params);
                                return true;
                        }
                        return false;
                    }
                });
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка виджета: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
    }
}
