package com.sevtinge.cemiuiler.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.sevtinge.cemiuiler.XposedInit;
import com.sevtinge.cemiuiler.provider.SharedPrefsProvider;
import com.sevtinge.cemiuiler.utils.log.XposedLogUtils;

import org.json.JSONArray;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XposedBridge;

public class PrefsUtils {

    public static SharedPreferences mSharedPreferences = null;

    public static String mPrefsPathCurrent = null;
    public static String mPrefsFileCurrent = null;
    public static String mPrefsName = "cemiuiler_prefs";
    public static String mPrefsPath = "/data/user_de/0/" + Helpers.mAppModulePkg + "/shared_prefs";
    public static String mPrefsFile = mPrefsPath + "/" + mPrefsName + ".xml";


    /**
     * 模块端调用：去掉配置文件及其目录上“其他用户”的权限位。
     * 被 hook 进程通过 Provider 读取配置，不需要文件对外可读；
     * 同时这也是 LSPosed 判断“旧版 NSP 风险”的条件之一（存在其他人可读的 xml）。
     */
    public static void makePrefsPrivate(Context context) {
        try {
            Context protectedContext = Helpers.getProtectedContext(context);
            File dataDir = protectedContext.getDataDir();
            File prefsDir = new File(dataDir, "shared_prefs");
            stripOthersPermission(dataDir);
            stripOthersPermission(prefsDir);
            File[] files = prefsDir.listFiles();
            if (files != null) for (File file : files) stripOthersPermission(file);
        } catch (Throwable ignored) {
        }
    }

    private static void stripOthersPermission(File file) {
        try {
            if (!file.exists()) return;
            String path = file.getAbsolutePath();
            int mode = android.system.Os.stat(path).st_mode & 07777;
            if ((mode & 07) != 0) android.system.Os.chmod(path, mode & ~07);
        } catch (Throwable ignored) {
            // 不属于本应用的文件改不了，忽略
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

    /** Provider.call 方法名：导出全部配置 */
    public static final String METHOD_GET_ALL = "get_all";

    public enum LoadResult {
        /** 读到了配置 */
        LOADED,
        /** 渠道可达，但模块那边确实没有任何配置（没保存过设置） */
        EMPTY,
        /** 渠道不可用（Provider 还没就绪、被拦截、异常等），值得重试 */
        FAILED
    }

    /** 最近一次读取配置失败/为空的原因，供状态页展示 */
    public static volatile String sLastLoadNote = null;

    /** 配置是从哪个通道读到的（状态页展示用） */
    public static volatile String sConfigSource = null;

    private static String describe(Throwable t) {
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    /**
     * 直接读取模块的配置文件（不经过 LSPosed 的 New XSharedPreferences）。
     * 被 hook 进程没有权限时读不到，返回 false。
     */
    public static boolean loadFromFile() {
        try {
            File file = new File(mPrefsFile);
            if (!file.canRead()) {
                sLastLoadNote = "配置文件不可读";
                return false;
            }
            Map<String, ?> all = new XSharedPreferences(file).getAll();
            if (all == null || all.isEmpty()) {
                sLastLoadNote = "配置文件为空";
                return false;
            }
            XposedInit.mPrefsMap.putAll(all);
            sLastLoadNote = null;
            sConfigSource = "配置文件";
            return true;
        } catch (Throwable t) {
            sLastLoadNote = "读配置文件异常 " + describe(t);
            return false;
        }
    }

    /**
     * 通过模块的 SharedPrefsProvider 一次性读取全部配置并写入 mPrefsMap。
     * 先用 call（状态上报用的就是这个通道），不行再退回 query。
     */
    public static LoadResult loadAllFromProvider(Context context) {
        if (context == null) {
            sLastLoadNote = "系统 Context 不可用";
            return LoadResult.FAILED;
        }
        String callNote;
        try {
            Uri base = Uri.parse("content://" + SharedPrefsProvider.AUTHORITY);
            Bundle result = context.getContentResolver().call(base, METHOD_GET_ALL, null, null);
            if (result != null && result.getBoolean("ok")) {
                Bundle values = result.getBundle("values");
                Map<String, Object> loaded = new HashMap<>();
                if (values != null) {
                    for (String key : values.keySet()) {
                        Object v = values.get(key);
                        if (v instanceof ArrayList) {
                            Set<String> set = new LinkedHashSet<>();
                            for (Object o : (ArrayList<?>) v) if (o != null) set.add(o.toString());
                            loaded.put(key, set);
                        } else if (v != null) {
                            loaded.put(key, v);
                        }
                    }
                }
                if (loaded.isEmpty()) {
                    sLastLoadNote = "Provider 可达但模块里没有任何配置";
                    return LoadResult.EMPTY;
                }
                XposedInit.mPrefsMap.putAll(loaded);
                sLastLoadNote = null;
                sConfigSource = "Provider call";
                return LoadResult.LOADED;
            }
            callNote = result == null ? "call 返回 null" : "call 返回失败";
        } catch (Throwable t) {
            callNote = "call 异常 " + describe(t);
        }

        Cursor cursor = null;
        try {
            Uri uri = Uri.parse("content://" + SharedPrefsProvider.AUTHORITY + "/all");
            cursor = context.getContentResolver().query(uri, null, null, null, null);
            if (cursor == null) {
                sLastLoadNote = callNote + "；query 返回 null";
                return LoadResult.FAILED;
            }

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
            if (loaded.isEmpty()) {
                sLastLoadNote = "Provider 可达但模块里没有任何配置";
                return LoadResult.EMPTY;
            }
            XposedInit.mPrefsMap.putAll(loaded);
            sLastLoadNote = null;
            sConfigSource = "Provider query";
            return LoadResult.LOADED;
        } catch (Throwable t) {
            sLastLoadNote = callNote + "；query 异常 " + describe(t);
            return LoadResult.FAILED;
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    // ---- “系统就绪”一次性事件：配置延后安装、状态上报共用，全程不轮询 ----
    // 就绪 = 本进程主线程第一次空闲（system_server 即开机初始化结束进入循环后）。

    private static final class ReadyTask {
        final Runnable task;
        final boolean inline;

        ReadyTask(Runnable task, boolean inline) {
            this.task = task;
            this.inline = inline;
        }
    }

    private static final Object sReadyLock = new Object();
    private static final List<ReadyTask> sReadyTasks = new ArrayList<>();
    private static boolean sReady = false;
    private static boolean sReadyHooked = false;
    private static volatile boolean sIsSystemServer = false;

    /**
     * 等“系统就绪”后执行 task，只执行一次。
     *
     * @param inline true：就绪回调所在的主线程里直接执行（system_server 里仍放后台线程，避免阻塞系统主线程）；
     *               false：总是放到后台线程。
     *               已经就绪时一律立即放到后台线程。
     */
    public static void runWhenSystemReady(Runnable task, boolean inline) {
        boolean ready;
        boolean needHook = false;
        synchronized (sReadyLock) {
            ready = sReady;
            if (!ready) {
                sReadyTasks.add(new ReadyTask(task, inline));
                if (!sReadyHooked) {
                    sReadyHooked = true;
                    needHook = true;
                }
            }
        }
        if (ready) {
            startReadyThread(java.util.Collections.singletonList(task));
            return;
        }
        if (needHook && !watchMainLooperIdle()) {
            // 挂不上等待事件：不能让任务永远不执行，退回立即在后台执行
            markReady(false);
        }
    }

    private static boolean watchMainLooperIdle() {
        try {
            Looper main = Looper.getMainLooper();
            if (main == null) return false;
            main.getQueue().addIdleHandler(() -> {
                markReady(true);
                return false; // 只触发一次
            });
            return true;
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler][E]: cannot watch main looper idle: " + t);
            return false;
        }
    }

    private static void markReady(boolean onMainThread) {
        List<ReadyTask> tasks;
        synchronized (sReadyLock) {
            if (sReady && sReadyTasks.isEmpty()) return;
            sReady = true;
            tasks = new ArrayList<>(sReadyTasks);
            sReadyTasks.clear();
        }
        for (ReadyTask t : tasks) {
            if (t.inline && onMainThread && !sIsSystemServer) {
                runSafely(t.task);
            } else {
                startReadyThread(java.util.Collections.singletonList(t.task));
            }
        }
    }

    private static void startReadyThread(List<Runnable> tasks) {
        Thread thread = new Thread(() -> {
            for (Runnable r : tasks) runSafely(r);
        }, "Cemiuiler-Ready");
        thread.setDaemon(true);
        thread.start();
    }

    private static void runSafely(Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler][E]: deferred task failed: " + t);
        }
    }

    /**
     * 保证“先拿到配置，再决定装哪些 hook”，全程不轮询、不定时唤醒：
     * <ul>
     *   <li>配置已在 map 里、配置文件能读、或 Provider 当场能读到：直接在当前线程执行。</li>
     *   <li>Provider 可达但确实没有配置（没保存过设置）：直接按默认配置执行。</li>
     *   <li>Provider 暂时不可用：等“系统就绪”事件，事件到了只重试一次，然后无论成败都执行。</li>
     * </ul>
     * task 保证只执行一次。
     */
    public static void runWhenPrefsReady(LoadPackageParam lpparam, Runnable task) {
        sIsSystemServer = "android".equals(lpparam.packageName);
        if (XposedInit.mPrefsMap.isEmpty() && !loadFromFile()) {
            LoadResult first = loadAllFromProvider(getSystemContext());
            if (first == LoadResult.FAILED) {
                runWhenSystemReady(() -> {
                    if (XposedInit.mPrefsMap.isEmpty()) loadAllFromProvider(getSystemContext());
                    task.run();
                }, true);
                return;
            }
        }
        task.run();
    }

    public static SharedPreferences getSharedPrefs(Context context, boolean multiProcess) {
        context = Helpers.getProtectedContext(context);
        // 不再使用 MODE_WORLD_READABLE（已废弃）；被 hook 进程通过 Provider 读取配置
        return context.getSharedPreferences(mPrefsName, multiProcess ? Context.MODE_MULTI_PROCESS | Context.MODE_PRIVATE : Context.MODE_PRIVATE);
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
