package com.sevtinge.cemiuiler.module.hook.systemframework.corepatch;

import static com.sevtinge.cemiuiler.module.base.BaseXposedInit.mPrefsMap;

import com.sevtinge.cemiuiler.utils.PrefsUtils;

import java.io.File;
import java.util.Map;

import de.robv.android.xposed.XSharedPreferences;

/**
 * CorePatch 使用的配置读取器，用于替代 New XSharedPreferences（LSPosed 2.3.0 将移除）。
 * <p>
 * 优先使用 initZygote 阶段已经加载到 mPrefsMap 的配置快照；
 * reload() 时尝试重新读取配置文件，读取失败则继续使用现有快照。
 */
public class CorePatchPrefs {

    public boolean getBoolean(String key, boolean defValue) {
        Object value = mPrefsMap.get(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    // reload() 会被 ReturnConstant 在每次被 hook 的方法调用时执行（包括 MessageDigest.isEqual 这类
    // system_server 里很常用的方法）。原来每次都重新解析整个配置 XML，开销很大，
    // 现在限制最多每 5 秒检查一次，并且只有文件的修改时间变了才重新读取。
    private static final long MIN_RELOAD_INTERVAL_MS = 5000;
    private volatile long mLastCheckTime = 0;
    private volatile long mLastModified = -1;

    public void reload() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - mLastCheckTime < MIN_RELOAD_INTERVAL_MS) return;
        mLastCheckTime = now;
        try {
            File file = new File(PrefsUtils.mPrefsFile);
            long modified = file.lastModified();
            if (modified == 0 || modified == mLastModified) return;
            XSharedPreferences xsp = new XSharedPreferences(file);
            Map<String, ?> all = xsp.getAll();
            if (all != null && !all.isEmpty()) {
                mPrefsMap.putAll(all);
                mLastModified = modified;
            }
        } catch (Throwable ignored) {
            // 无权限读取时沿用 zygote 阶段的快照
        }
    }
}
