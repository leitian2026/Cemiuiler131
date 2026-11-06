package com.sevtinge.cemiuiler.module.base;

import com.sevtinge.cemiuiler.XposedInit;
import com.sevtinge.cemiuiler.utils.PrefsMap;
import com.sevtinge.cemiuiler.utils.log.HookStatusReporter;
import com.sevtinge.cemiuiler.utils.log.XposedLogUtils;

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public abstract class BaseModule implements IXposedHook {

    public LoadPackageParam mLoadPackageParam = null;
    public final PrefsMap<String, Object> mPrefsMap = XposedInit.mPrefsMap;

    public void init(LoadPackageParam lpparam) {
        mLoadPackageParam = lpparam;
        initZygote();
        handleLoadPackage();
    }

    @Override
    public void initZygote() {
    }

    public void initHook(BaseHook baseHook) {
        initHook(baseHook, true);
    }

    public void initHook(BaseHook baseHook, boolean isInit) {
        if (isInit) {
            baseHook.onCreate(mLoadPackageParam);
        }
    }

    /**
     * 让单个 hook 的创建/初始化失败不会中断后面所有 hook 的安装。
     * 创建 hook 对象本身（构造函数、Kotlin object 的初始化）发生在 onCreate 的保护之外，
     * 一个类找不到就会把整条初始化链终止；这里把它隔离并记录到状态页。
     */
    protected void safeInit(String name, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            XposedLogUtils.logE("initHook", name, t);
            if (mLoadPackageParam != null) {
                HookStatusReporter.record(mLoadPackageParam.packageName, mLoadPackageParam.processName, name, false, t);
            }
        }
    }
}
