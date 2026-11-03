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
     * 配置每次写入后文件权限会被重置，这里在写入落盘后重新放开读取权限，
     * 以便 zygote 阶段读取配置快照（不再依赖 New XSharedPreferences）。
     */
    private void setupPrefsPermission() {
        final Handler handler = new Handler(Looper.getMainLooper());
        final Runnable fix = () -> PrefsUtils.makePrefsReadable(this);
        PrefsUtils.makePrefsReadable(this);
        if (PrefsUtils.mSharedPreferences == null) return;
        sPrefsListener = (prefs, key) -> {
            handler.postDelayed(fix, 500);
            handler.postDelayed(fix, 3000);
        };
        PrefsUtils.mSharedPreferences.registerOnSharedPreferenceChangeListener(sPrefsListener);
    }
}
