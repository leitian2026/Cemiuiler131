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

    public void reload() {
        try {
            XSharedPreferences xsp = new XSharedPreferences(new File(PrefsUtils.mPrefsFile));
            Map<String, ?> all = xsp.getAll();
            if (all != null && !all.isEmpty()) {
                mPrefsMap.putAll(all);
            }
        } catch (Throwable ignored) {
            // 无权限读取时沿用 zygote 阶段的快照
        }
    }
}
