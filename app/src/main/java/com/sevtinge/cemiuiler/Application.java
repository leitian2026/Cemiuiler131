package com.sevtinge.cemiuiler;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.sevtinge.cemiuiler.utils.PrefsUtils;

public class Application extends android.app.Application {
    // SharedPreferences 只持有监听器的弱引用，需要静态保存
    private static SharedPreferences.OnSharedPreferenceChangeListener sPrefsListener;

    @Override
    protected void attachBaseContext(Context base) {
        PrefsUtils.mSharedPreferences = PrefsUtils.getSharedPrefs(base);
        super.attachBaseContext(base);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        setupPrefsPermission();
    }

    /**
     * 配置每次写入后重新收紧文件权限，不让其他应用读写模块配置。
     */
    private void setupPrefsPermission() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable fix = () -> PrefsUtils.makePrefsPrivate(this);
        PrefsUtils.makePrefsPrivate(this);
        if (PrefsUtils.mSharedPreferences == null) return;
        sPrefsListener = (prefs, key) -> {
            // 连续多次修改只执行最后一轮，不堆积任务
            handler.removeCallbacks(fix);
            handler.postDelayed(fix, 500);
        };
        PrefsUtils.mSharedPreferences.registerOnSharedPreferenceChangeListener(sPrefsListener);
    }
}
