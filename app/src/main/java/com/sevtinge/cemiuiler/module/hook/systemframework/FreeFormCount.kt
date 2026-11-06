package com.sevtinge.cemiuiler.module.hook.systemframework

import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHooks
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.sevtinge.cemiuiler.module.base.BaseHook

class FreeFormCount : BaseHook() {
    override fun init() {
        val clazzMiuiFreeFormStackDisplayStrategy =
            loadClass("com.android.server.wm.MiuiFreeFormStackDisplayStrategy")
        // GetMaxMiuiFreeFormStackCount
        clazzMiuiFreeFormStackDisplayStrategy.methodFinder().filter {
            name in setOf(
                "getMaxMiuiFreeFormStackCount",
                "getMaxMiuiFreeFormStackCountForFlashBack"
            )
        }.toList().createHooks {
            returnConstant(256)
        }

        // ShouldStopStartFreeform
        // 有的系统版本没有这个方法：没有就不用 hook，不应让整个功能失败
        clazzMiuiFreeFormStackDisplayStrategy.methodFinder().filter {
            name == "shouldStopStartFreeform"
        }.toList().createHooks {
            returnConstant(false)
        }
    }
}
