package com.sevtinge.cemiuiler.module.hook.home.folder;

import android.view.View;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * 文件夹图标模糊（BigFolderIconBlur / 1x2 / 2x1 / SmallFolderIconBlur）共用的桌面钩子。
 * <p>
 * 原来每个文件夹图标每次 onFinishInflate 都会往桌面的 showEditPanel / openFolder / closeFolder /
 * onStateSetStart 上再各挂一个钩子，钩子数量随 inflate 次数不断增长（onStateSetStart 每次桌面状态切换都会触发），
 * 并且每个钩子都捕获着图标的视图造成泄漏。现在这 4 个钩子在一个进程里只挂一次，
 * 各个图标只往弱引用列表里登记自己的两个视图，回调时统一处理。
 * <p>
 * 全部在主线程调用。
 */
final class FolderBlurLauncherHooks {
    private FolderBlurLauncherHooks() {
    }

    private static final class Holder {
        final WeakReference<View> dock;
        final WeakReference<View> icon;

        Holder(View dock, View icon) {
            this.dock = new WeakReference<>(dock);
            this.icon = new WeakReference<>(icon);
        }
    }

    private static final List<Holder> HOLDERS = new ArrayList<>();
    private static boolean installed = false;
    private static boolean isShowEditPanel = false;

    static synchronized void register(View dock, View icon, Class<?> launcher, Class<?> folderInfo, Class<?> launcherState) {
        HOLDERS.add(new Holder(dock, icon));
        if (installed) return;
        installed = true;

        try {
            XposedHelpers.findAndHookMethod(launcher, "showEditPanel", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    isShowEditPanel = (boolean) param.args[0];
                    forEach((dockView, iconView) -> {
                        dockView.setVisibility(isShowEditPanel ? View.GONE : View.VISIBLE);
                        if (iconView != null) iconView.setVisibility(isShowEditPanel ? View.VISIBLE : View.GONE);
                    });
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler] FolderBlur hook showEditPanel failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(launcher, "openFolder", folderInfo, View.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    forEach((dockView, iconView) -> dockView.setVisibility(View.GONE));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler] FolderBlur hook openFolder failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(launcher, "closeFolder", boolean.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (!isShowEditPanel) forEach((dockView, iconView) -> dockView.setVisibility(View.VISIBLE));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler] FolderBlur hook closeFolder failed: " + t);
        }

        try {
            XposedHelpers.findAndHookMethod(launcher, "onStateSetStart", launcherState, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    boolean visible = param.args[0].getClass().getSimpleName().equals("LauncherState");
                    forEach((dockView, iconView) -> dockView.setVisibility(visible ? View.VISIBLE : View.GONE));
                }
            });
        } catch (Throwable t) {
            XposedBridge.log("[Cemiuiler] FolderBlur hook onStateSetStart failed: " + t);
        }
    }

    private interface Action {
        void run(View dock, View icon);
    }

    private static synchronized void forEach(Action action) {
        Iterator<Holder> iterator = HOLDERS.iterator();
        while (iterator.hasNext()) {
            Holder holder = iterator.next();
            View dock = holder.dock.get();
            if (dock == null) {
                iterator.remove();
                continue;
            }
            action.run(dock, holder.icon.get());
        }
    }
}
