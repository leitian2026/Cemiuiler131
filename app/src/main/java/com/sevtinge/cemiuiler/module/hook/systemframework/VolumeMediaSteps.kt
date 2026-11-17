package com.sevtinge.cemiuiler.module.hook.systemframework

import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.sevtinge.cemiuiler.module.base.BaseHook

class VolumeMediaSteps : BaseHook() {
    override fun init() {
        val mediaSteps = mPrefsMap.getInt("system_framework_volume_media_steps", 15)
        if (mediaSteps <= 15) return

        // AudioService reads this property through getInt(String, int).
        // Match the full signature so an unrelated getInt overload is not hooked.
        loadClass("android.os.SystemProperties").methodFinder().first {
            name == "getInt" &&
                returnType == Int::class.java &&
                parameterTypes.contentEquals(
                    arrayOf(String::class.java, Int::class.javaPrimitiveType!!)
                )
        }.createHook {
            before {
                if (it.args[0] == "ro.config.media_vol_steps") {
                    it.result = mediaSteps
                }
            }
        }
    }
}
