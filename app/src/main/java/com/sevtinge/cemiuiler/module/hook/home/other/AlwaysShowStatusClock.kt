package com.sevtinge.cemiuiler.module.hook.home.other

import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHooks
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.sevtinge.cemiuiler.module.base.BaseHook

/**
 * 桌面当前页有时钟小部件时，系统会把状态栏时间隐藏。
 * 这里让桌面认为“这一页没有时钟小部件”，状态栏时间就不会被隐藏。
 */
object AlwaysShowStatusClock : BaseHook() {
    // 已知的方法名；不同系统版本里名字不一定相同
    private val knownNames = setOf(
        "isScreenHasClockGadget", "isScreenHasClockWidget", "isClockWidget"
    )

    // 兜底：boolean 返回值、名字形如 is/has ... Clock ... Gadget/Widget 的方法
    private val clockNameRegex = Regex(
        "(?i)^(is|has).*clock.*(gadget|widget).*|^(is|has).*(gadget|widget).*clock.*"
    )

    override fun init() {
        val workspace = loadClass("com.miui.home.launcher.Workspace")

        // 所有重载都 hook（原来每个名字只取第一个重载）
        val targets = workspace.methodFinder().filter {
            returnType == Boolean::class.javaPrimitiveType &&
                (name in knownNames || clockNameRegex.matches(name))
        }.toList()

        if (targets.isEmpty()) {
            // 一个都没命中：不能装作成功。把桌面里含 clock 的方法名带出来，下一步才有依据
            val clockish = workspace.declaredMethods
                .map { it.name }
                .filter { it.contains("clock", ignoreCase = true) }
                .distinct()
                .take(6)
            throw NoSuchMethodException("Workspace 无匹配方法，含clock的方法:$clockish")
        }

        targets.createHooks {
            before { it.result = false }
        }
    }
}
