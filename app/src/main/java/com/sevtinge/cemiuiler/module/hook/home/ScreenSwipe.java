package com.sevtinge.cemiuiler.module.hook.home;

import static com.sevtinge.cemiuiler.utils.log.AndroidLogUtils.LogD;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import com.sevtinge.cemiuiler.module.app.GlobalActions;
import com.sevtinge.cemiuiler.module.base.BaseHook;
import com.sevtinge.cemiuiler.utils.PrefsUtils;
import com.sevtinge.cemiuiler.utils.log.XposedLogUtils;

import de.robv.android.xposed.XposedHelpers;

public class ScreenSwipe extends BaseHook {

    Class<?> mLauncher;
    Class<?> mWorkspace;

    // 下面这些桌面方法在手势、布局过程中会被频繁调用，原来每次都向模块的 Provider 做一次跨进程查询。
    // 这里加一个短时缓存；设置变化时由 SharedPrefsObserver 主动失效，所以不会读到旧值。
    private static final long INT_CACHE_TTL_MS = 5000;

    private static final class CachedInt {
        final int value;
        final long time;

        CachedInt(int value, long time) {
            this.value = value;
            this.time = time;
        }
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, CachedInt> sIntCache =
        new java.util.concurrent.ConcurrentHashMap<>();

    private static int getIntPref(Context context, String key, int def) {
        long now = android.os.SystemClock.uptimeMillis();
        // 同一个 key 在不同调用处的默认值可能不同，缓存键必须带上默认值
        String cacheKey = key + "|" + def;
        CachedInt cached = sIntCache.get(cacheKey);
        if (cached != null && now - cached.time < INT_CACHE_TTL_MS) return cached.value;
        int value = PrefsUtils.getSharedIntPrefs(context, key, def);
        sIntCache.put(cacheKey, new CachedInt(value, now));
        return value;
    }

    @Override
    public void init() {
        mLauncher = findClassIfExists("com.miui.home.launcher.Launcher");
        mWorkspace = findClassIfExists("com.miui.home.launcher.Workspace");

        findAndHookMethod(mWorkspace, "onVerticalGesture", int.class, MotionEvent.class, new MethodHook() {
            @Override
            protected void before(MethodHookParam param) {
                if ((boolean) XposedHelpers.callMethod(param.thisObject, "isInNormalEditingMode")) return;
                String key = null;
                Context helperContext = ((ViewGroup) param.thisObject).getContext();
                int numOfFingers = 1;
                if (param.args[1] != null) numOfFingers = ((MotionEvent) param.args[1]).getPointerCount();
                if ((int) param.args[0] == 11) {
                    if (numOfFingers == 1)
                        key = "prefs_key_home_gesture_down_swipe";
                    else if (numOfFingers == 2)
                        key = "prefs_key_home_gesture_down_swipe2";
                    if (GlobalActions.handleAction(helperContext, key)) param.setResult(true);
                } else if ((int) param.args[0] == 10) {
                    if (numOfFingers == 1)
                        key = "prefs_key_home_gesture_up_swipe";
                    else if (numOfFingers == 2)
                        key = "prefs_key_home_gesture_up_swipe2";
                    if (GlobalActions.handleAction(helperContext, key)) param.setResult(true);
                }
            }
        });

        findAndHookMethod(mLauncher, "onCreate", Bundle.class, new MethodHook() {
            @Override
            protected void after(final MethodHookParam param) {
                final Activity act = (Activity) param.thisObject;
                Handler mHandler = (Handler) XposedHelpers.getObjectField(act, "mHandler");
                new PrefsUtils.SharedPrefsObserver(act, mHandler) {
                    @Override
                    public void onChange(Uri uri) {
                        try {
                            final String changedKey = uri.getPathSegments().get(2);
                            final String prefix = changedKey + "|";
                            sIntCache.keySet().removeIf(k -> k.startsWith(prefix));
                        } catch (Throwable ignored) {
                        }
                        try {
                            String type = uri.getPathSegments().get(1);
                            String key = uri.getPathSegments().get(2);
                            if (key.contains("prefs_key_home_gesture_down_swipe"))
                                switch (type) {
                                    case "string" ->
                                        mPrefsMap.put(key, PrefsUtils.getSharedStringPrefs(act, key, ""));
                                    case "integer" ->
                                        mPrefsMap.put(key, PrefsUtils.getSharedIntPrefs(act, key, 1));
                                    case "boolean" ->
                                        mPrefsMap.put(key, PrefsUtils.getSharedBoolPrefs(act, key, false));
                                }
                        } catch (Throwable t) {
                            LogD(TAG, "setAction", t);
                        }
                    }
                };
            }
        });

        findAndHookMethodSilently("com.miui.home.launcher.uioverrides.StatusBarSwipeController", "canInterceptTouch", MotionEvent.class, new MethodHook() {
            @Override
            protected void before(final MethodHookParam param) {
                if (mPrefsMap.getInt("home_gesture_down_swipe_action", 0) > 0) param.setResult(false);
            }
        });

        // content_center, global_search, notification_bar
        findAndHookMethodSilently("com.miui.home.launcher.allapps.LauncherMode", "getPullDownGesture", Context.class, new MethodHook() {
            @Override
            protected void after(final MethodHookParam param) {
                if (getIntPref((Context) param.args[0], "prefs_key_home_gesture_down_swipe_action", 1) > 1)
                    param.setResult("no_action");
            }
        });

        // content_center, global_search
        findAndHookMethodSilently("com.miui.home.launcher.allapps.LauncherMode", "getSlideUpGesture", Context.class, new MethodHook() {
            @Override
            protected void before(final MethodHookParam param) {
                if (getIntPref((Context) param.args[0], "prefs_key_home_gesture_up_swipe_action", 0) > 0)
                    param.setResult("no_action");
            }
        });

        if (findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", "isGlobalSearchEnable", Context.class, new MethodHook() {
            @Override
            protected void before(final MethodHookParam param) {
                if (getIntPref((Context) param.args[0], "prefs_key_home_gesture_up_swipe_action", 0) > 0)
                    param.setResult(false);
            }
        })) {
            findAndHookMethodSilently("com.miui.home.launcher.search.SearchEdgeLayout", "isTopSearchEnable", new MethodHook() {
                @Override
                protected void before(final MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (getIntPref(view.getContext(), "prefs_key_home_gesture_down_swipe_action", 0) > 0)
                        param.setResult(false);
                }
            });

            findAndHookMethodSilently("com.miui.home.launcher.search.SearchEdgeLayout", "isBottomGlobalSearchEnable", new MethodHook() {
                @Override
                protected void before(final MethodHookParam param) {
                    View view = (View) param.thisObject;
                    if (getIntPref(view.getContext(), "prefs_key_home_gesture_up_swipe_action", 0) > 0)
                        param.setResult(false);
                }
            });

            findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", "isGlobalSearchBottomEffectEnable", Context.class, new MethodHook() {
                @Override
                protected void before(final MethodHookParam param) {
                    if (getIntPref((Context) param.args[0], "prefs_key_home_gesture_up_swipe_action", 0) > 0)
                        param.setResult(false);
                }
            });
        } else if (!findAndHookMethodSilently("com.miui.home.launcher.DeviceConfig", "allowedSlidingUpToStartGolbalSearch", Context.class, new MethodHook() {
            @Override
            protected void before(final MethodHookParam param) {
                if (getIntPref((Context) param.args[0], "prefs_key_home_gesture_up_swipe_action", 0) > 0)
                    param.setResult(false);
            }
        })) if (lpparam.packageName.equals("com.miui.home"))
            XposedLogUtils.logI(TAG, "Cannot disable swipe up search");
    }
}
