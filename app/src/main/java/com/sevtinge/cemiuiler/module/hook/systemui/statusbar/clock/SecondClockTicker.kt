package com.sevtinge.cemiuiler.module.hook.systemui.statusbar.clock

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.View
import java.lang.ref.WeakReference
import java.lang.reflect.Method

/**
 * 带秒的时钟的统一节拍器（替代原来每个时钟各开一个永不停止的 Timer 线程）。
 *
 * - 全部时钟共用一个主线程 Handler，按整秒对齐，不再每秒新建 Handler、也没有额外线程；
 * - 息屏后停止，亮屏后恢复，息屏期间不再每秒唤醒；
 * - 只刷新已挂在窗口上且可见的时钟，视图被摘下 / 隐藏时跳过（例如解锁后的锁屏时钟）；视图被回收后自动移除；
 * - 同一个视图重复登记会被忽略（SystemUI 重建视图时不会越积越多）。
 *
 * 所有方法都在主线程调用（时钟视图在主线程构造，广播和 Handler 也都在主线程）。
 */
object SecondClockTicker {
    private class Entry(val view: WeakReference<View>, val updateTime: Method)

    private val handler = Handler(Looper.getMainLooper())
    private val entries = ArrayList<Entry>()
    private var scheduled = false
    private var screenOn = true
    private var receiverRegistered = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOn = false
                    handler.removeCallbacks(tick)
                    scheduled = false
                }

                Intent.ACTION_SCREEN_ON -> {
                    screenOn = true
                    schedule()
                }
            }
        }
    }

    private val tick = Runnable {
        scheduled = false
        if (!screenOn) return@Runnable
        var alive = false
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val view = entry.view.get()
            if (view == null) {
                iterator.remove()
                continue
            }
            alive = true
            if (view.isAttachedToWindow && view.isShown) {
                try {
                    entry.updateTime.invoke(view)
                } catch (e: Throwable) {
                }
            }
        }
        if (alive) schedule()
    }

    fun add(view: View, updateTime: Method) {
        for (entry in entries) {
            if (entry.view.get() === view) return
        }
        updateTime.isAccessible = true
        entries.add(Entry(WeakReference(view), updateTime))
        ensureReceiver(view.context)
        schedule()
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        receiverRegistered = true
        try {
            val app = context.applicationContext ?: context
            val powerManager = app.getSystemService(Context.POWER_SERVICE) as PowerManager
            screenOn = powerManager.isInteractive
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            if (Build.VERSION.SDK_INT >= 33) {
                app.registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                app.registerReceiver(screenReceiver, filter)
            }
        } catch (e: Throwable) {
            // 取不到亮灭屏状态时按亮屏处理，行为和原来一样
            screenOn = true
        }
    }

    private fun schedule() {
        if (scheduled || !screenOn || entries.isEmpty()) return
        scheduled = true
        // 对齐到下一个整秒
        handler.postDelayed(tick, 1000 - System.currentTimeMillis() % 1000)
    }
}
