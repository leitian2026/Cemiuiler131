package com.sevtinge.cemiuiler.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;

import com.sevtinge.cemiuiler.XposedInit;
import com.sevtinge.cemiuiler.provider.SharedPrefsProvider;
import com.sevtinge.cemiuiler.utils.log.XposedLogUtils;

import org.json.JSONArray;

import java.io.File;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XposedBridge;

public class PrefsUtils {

    public static SharedPreferences mSharedPreferences = null;

    public static String mPrefsPathCurrent = null;
    public static String mPrefsFileCurrent = null;
    public static String mPrefsName = "cemiuiler_prefs";
    public static String mPrefsPath = "/data/user_de/0/" + Helpers.mAppModulePkg + "/shared_prefs";
    public static String mPrefsFile = mPrefsPath + "/" + mPrefsName + ".xml";


    /**
     * 模块端调用：让配置文件对其他进程可读（供 zygote 阶段读取快照）。
     * 不依赖 New XSharedPreferences，失败时静默忽略。
     */
    public static void makePrefsReadable(Context context) {
        try {
            Context protectedContext = Helpers.getProtectedContext(context);
            File dataDir = protectedContext.getDataDir();
            File prefsDir = new File(dataDir, "shared_prefs");
            File prefsFile = new File(prefsDir, mPrefsName + ".xml");
            dataDir.setExecutable(true, false);
            prefsDir.setExecutable(true, false);
            prefsDir.setReadable(true, false);
            if (prefsFile.exists()) prefsFile.setReadable(true, false);
        } catch (Throwable ignored) {
        }
    }

    /**
     * 获取系统 Context（ActivityThread.getSystemContext），用于在被 hook 进程中访问模块的 ContentProvider。
     */
    public static Context getSystemContext() {
        try {
            Class<?> activityThread = Class.forName("android.app.ActivityThread");
            Object thread = activityThread.getMethod("currentActivityThread").invoke(null);
            return (Context) activityThread.getMethod("getSystemContext").invoke(thread);
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 通过模块的 SharedPrefsProvider 一次性读取全部配置并写入 mPrefsMap。
     *
     * @return 是否成功读取到配置
     */
    public static boolean loadAllFromProvider(Context context) {
        if (context == null) return false;
        Cursor cursor = null;
        try {
            Uri uri = Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/all");
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) return false;

            Map<String, Object> loaded = new HashMap<>();
            while (cursor.moveToNext()) {
                String key = cursor.getString(0);
                String type = cursor.getString(1);
                String value = cursor.getString(2);
                if (key == null || type == null || value == null) continue;
                switch (type) {
                    case "boolean" -> loaded.put(key, "1".equals(value));
                    case "int" -> loaded.put(key, Integer.parseInt(value));
                    case "long" -> loaded.put(key, Long.parseLong(value));
                    case "float" -> loaded.put(key, Float.parseFloat(value));
                    case "string" -> loaded.put(key, value);
                    case "stringset" -> {
                        JSONArray array = new JSONArray(value);
                        Set<String> set = new LinkedHashSet<>();
                        for (int i = 0; i < array.length(); i++) set.add(array.getString(i));
                        loaded.put(key, set);
                    }
                }
            }
            if (loaded.isEmpty()) return false;
            XposedInit.mPrefsMap.putAll(loaded);
            return true;
        } catch (Throwable t) {
            return false;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    private static final AtomicBoolean sPrefsLoaderStarted = new AtomicBoolean(false);

    /**
     * 被 hook 进程启动时配置快照没读到，就通过模块 Provider 兜底读取。
     * system_server 启动早期 Provider 不可用，放到后台线程里重试，避免阻塞系统启动。
     */
    public static void ensurePrefsLoaded(String packageName) {
        if (!XposedInit.mPrefsMap.isEmpty()) return;
        if ("android".equals(packageName)) {
            if (!sPrefsLoaderStarted.compareAndSet(false, true)) return;
            Thread loader = new Thread(() -> {
                for (int i = 0; i < 30 && XposedInit.mPrefsMap.isEmpty(); i++) {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    loadAllFromProvider(getSystemContext());
                }
            }, "Cemiuiler-PrefsLoader");
            loader.setDaemon(true);
            loader.start();
        } else {
            loadAllFromProvider(getSystemContext());
        }
    }

    public static SharedPreferences getSharedPrefs(Context context, boolean multiProcess) {
        context = Helpers.getProtectedContext(context);
        try {
            return context.getSharedPreferences(mPrefsName, multiProcess ? Context.MODE_MULTI_PROCESS | Context.MODE_WORLD_READABLE : Context.MODE_WORLD_READABLE);
        } catch (Throwable t) {
            return context.getSharedPreferences(mPrefsName, multiProcess ? Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE : Context.MODE_PRIVATE);
        }
    }

    public static SharedPreferences getSharedPrefs(Context context) {
        return getSharedPrefs(context, false);
    }


    public static String getSharedPrefsPath() {
        if (mPrefsPathCurrent == null) try {
            Field mFile = mSharedPreferences.getClass().getDeclaredField("mFile");
            mFile.setAccessible(true);
            mPrefsPathCurrent = ((File) mFile.get(mSharedPreferences)).getParentFile().getAbsolutePath();
            return mPrefsPathCurrent;
        } catch (Throwable t) {
            System.out.print("Test" + t);
            return mPrefsPath;
        }
        else return mPrefsPathCurrent;
    }

    public static String getSharedPrefsFile() {
        if (mPrefsFileCurrent == null) try {
            Field fFile = mSharedPreferences.getClass().getDeclaredField("mFile");
            fFile.setAccessible(true);
            mPrefsFileCurrent = ((File) fFile.get(mSharedPreferences)).getAbsolutePath();
            System.out.println("Test: mPrefsFileCurrent");
            return mPrefsFileCurrent;
        } catch (Throwable t) {
            System.out.println("Test: mPrefsFile" + t);
            return mPrefsFile;
        }
        else
            System.out.println("Test: mPrefsFileCurrent2");
        return mPrefsFileCurrent;
    }


    public static boolean contains(String key) {
        return mSharedPreferences.contains(key);
    }

    public static SharedPreferences.Editor editor() {
        return mSharedPreferences.edit();
    }


    public static String getSharedStringPrefs(Context context, String name, String defValue) {
        Uri uri = stringPrefsToUri(name, defValue);
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String prefValue = cursor.getString(0);
                cursor.close();
                return prefValue;
            } else XposedLogUtils.logI("ContentResolver", "[" + name + "] Cursor fail: " + cursor);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        if (XposedInit.mPrefsMap.containsKey(name))
            return (String) XposedInit.mPrefsMap.getObject(name, defValue);
        else return defValue;
    }

    public static Set<String> getSharedStringSetPrefs(Context context, String name) {
        Uri uri = stringSetPrefsToUri(name);
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                Set<String> prefValue = new LinkedHashSet<>();
                while (cursor.moveToNext()) {
                    prefValue.add(cursor.getString(0));
                }
                cursor.close();
                return prefValue;
            } else {
                XposedLogUtils.logI("ContentResolver", "[" + name + "] Cursor fail: null");
            }
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        LinkedHashSet<String> empty = new LinkedHashSet<>();
        if (XposedInit.mPrefsMap.containsKey(name)) {
            return (Set<String>) XposedInit.mPrefsMap.getObject(name, empty);
        } else {
            return empty;
        }
    }


    public static int getSharedIntPrefs(Context context, String name, int defValue) {
        Uri uri = intPrefsToUri(name, defValue);
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int prefValue = cursor.getInt(0);
                cursor.close();
                return prefValue;
            } else XposedLogUtils.logI("ContentResolver", "[" + name + "] Cursor fail: " + cursor);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        if (XposedInit.mPrefsMap.containsKey(name))
            return (int) XposedInit.mPrefsMap.getObject(name, defValue);
        else return defValue;
    }


    public static boolean getSharedBoolPrefs(Context context, String name, boolean defValue) {
        Uri uri = boolPrefsToUri(name, defValue);
        try {
            Cursor cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                int prefValue = cursor.getInt(0);
                cursor.close();
                return prefValue == 1;
            } else XposedLogUtils.logI("ContentResolver", "[" + name + "] Cursor fail: " + cursor);
        } catch (Throwable t) {
            XposedBridge.log(t);
        }

        if (XposedInit.mPrefsMap.containsKey(name))
            return (boolean) XposedInit.mPrefsMap.getObject(name, false);
        else
            return defValue;
    }


    public static Uri stringPrefsToUri(String name, String defValue) {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/string/" + name + "/" + defValue);
    }

    public static Uri stringSetPrefsToUri(String name) {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/stringset/" + name);
    }

    public static Uri intPrefsToUri(String name, int defValue) {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/integer/" + name + "/" + defValue);
    }

    public static Uri boolPrefsToUri(String name, boolean defValue) {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/boolean/" + name + "/" + (defValue ? '1' : '0'));
    }

    public static Uri shortcutIconPrefsToUri(String name) {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/shortcut_icon/" + name);
    }

    public static Uri anyPrefsToUri() {
        return Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/pref/");
    }


    public static class SharedPrefsObserver extends ContentObserver {

        enum PrefType {
            Any, String, StringSet, Integer, Boolean
        }

        PrefType prefType;
        Context ctx;
        String mPrefsName;
        String mPrefsDefValueString;
        int mPrefsDefValueInt;
        boolean mPrefsDefValueBool;

        public SharedPrefsObserver(Context context, Handler handler) {
            super(handler);
            ctx = context;
            prefType = PrefType.Any;
            registerObserver();
        }

        public SharedPrefsObserver(Context context, Handler handler, String name, String defValue) {
            super(handler);
            ctx = context;
            mPrefsName = name;
            prefType = PrefType.String;
            mPrefsDefValueString = defValue;
            registerObserver();
        }

        public SharedPrefsObserver(Context context, Handler handler, String name) {
            super(handler);
            ctx = context;
            mPrefsName = name;
            prefType = PrefType.StringSet;
            registerObserver();
        }

        public SharedPrefsObserver(Context context, Handler handler, String name, int defValue) {
            super(handler);
            ctx = context;
            prefType = PrefType.Integer;
            mPrefsName = name;
            mPrefsDefValueInt = defValue;
            registerObserver();
        }

        @SuppressLint("SuspiciousIndentation")
        public SharedPrefsObserver(Context context, Handler handler, String name, boolean defValue) {
            super(handler);
            ctx = context;
            prefType = PrefType.Boolean;
            mPrefsName = name;
            mPrefsDefValueBool = defValue;
            registerObserver();
        }

        void registerObserver() {
            Uri uri = null;
            if (prefType == PrefType.String)
                uri = stringPrefsToUri(mPrefsName, mPrefsDefValueString);
            else if (prefType == PrefType.StringSet)
                uri = stringSetPrefsToUri(mPrefsName);
            else if (prefType == PrefType.Integer)
                uri = intPrefsToUri(mPrefsName, mPrefsDefValueInt);
            else if (prefType == PrefType.Boolean)
                uri = boolPrefsToUri(mPrefsName, mPrefsDefValueBool);
            else if (prefType == PrefType.Any)
                uri = anyPrefsToUri();
            if (uri != null)
                ctx.getContentResolver().registerContentObserver(uri, prefType == PrefType.Any, this);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (prefType == PrefType.Any)
                onChange(uri);
            else
                onChange(selfChange);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (selfChange) return;
            if (prefType == PrefType.String)
                onChange(mPrefsName, mPrefsDefValueString);
            else if (prefType == PrefType.StringSet)
                onChange(mPrefsName);
            else if (prefType == PrefType.Integer)
                onChange(mPrefsName, mPrefsDefValueInt);
            else if (prefType == PrefType.Boolean)
                onChange(mPrefsName, mPrefsDefValueBool);
        }

        public void onChange(Uri uri) {
        }

        public void onChange(String name) {
        }

        public void onChange(String name, String defValue) {
        }

        public void onChange(String name, int defValue) {
        }

        public void onChange(String name, boolean defValue) {
        }
    }
}
