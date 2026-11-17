package com.sevtinge.cemiuiler.module.hook.systemframework

import android.system.Os
import com.sevtinge.cemiuiler.utils.PrefsUtils
import com.sevtinge.cemiuiler.utils.log.HookStatusReporter
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File
import java.io.FileOutputStream
import java.lang.reflect.Method
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Installs before AudioService builds its stream states, without waiting for module preferences.
 * The desired value is staged by a later, config-aware pass for the next system_server start.
 */
object EarlyVolumeMediaSteps {
    private const val TAG = "Cemiuiler-MediaSteps"
    private const val CACHE_PATH = "/data/system/cemiuiler_media_steps"
    private const val TEMP_PATH = "/data/system/.cemiuiler_media_steps.tmp"
    private val installStarted = AtomicBoolean(false)
    private val statusReported = AtomicBoolean(false)

    @Volatile
    private var installSucceeded = false

    @Volatile
    private var installError: Throwable? = null

    @JvmStatic
    fun install(classLoader: ClassLoader?) {
        if (!installStarted.compareAndSet(false, true)) return
        try {
            val audioService = Class.forName(
                "com.android.server.audio.AudioService",
                false,
                classLoader
            )
            val method: Method = audioService.getDeclaredMethod("createStreamStates")
            method.isAccessible = true
            XposedBridge.hookMethod(method, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    try {
                        val cache = File(CACHE_PATH)
                        if (!cache.isFile) return
                        val steps = cache.readText(Charsets.UTF_8).trim().toIntOrNull()
                        if (steps == null || steps !in 16..29) {
                            XposedBridge.log("[$TAG][W]: invalid cached media steps; ignored")
                            return
                        }
                        val maxStreamVolume = XposedHelpers.getStaticObjectField(
                            audioService,
                            "MAX_STREAM_VOLUME"
                        ) as? IntArray ?: throw IllegalStateException("MAX_STREAM_VOLUME is not IntArray")
                        if (maxStreamVolume.size <= 3) {
                            throw IllegalStateException("MAX_STREAM_VOLUME has no STREAM_MUSIC index")
                        }
                        maxStreamVolume[3] = steps
                        XposedBridge.log("[$TAG][I]: applied cached STREAM_MUSIC max=$steps")
                    } catch (t: Throwable) {
                        XposedBridge.log("[$TAG][E]: applying cached media steps failed: $t")
                    }
                }
            })
            installSucceeded = true
            XposedBridge.log("[$TAG][I]: early AudioService.createStreamStates hook installed")
        } catch (t: Throwable) {
            installError = t
            XposedBridge.log("[$TAG][E]: cannot install early AudioService hook: $t")
        }
    }

    /**
     * Called by SystemFramework only after its normal preference-loading path.
     * A fallback/default pass must never overwrite a previously staged user value.
     */
    @JvmStatic
    fun syncAfterPrefsReady(enabled: Boolean, configuredSteps: Int) {
        if (!PrefsUtils.sConfigLoaded) return

        val desired = if (enabled && configuredSteps in 16..29) configuredSteps else null
        val syncError = syncCache(desired)

        if (enabled && statusReported.compareAndSet(false, true)) {
            val error = syncError ?: installError
            HookStatusReporter.record(
                "android",
                "android",
                "VolumeMediaSteps",
                installSucceeded && syncError == null,
                error
            )
        }
    }

    private fun syncCache(desired: Int?): Throwable? {
        val target = File(CACHE_PATH)
        try {
            if (desired == null) {
                if (target.exists() && !target.delete()) {
                    throw IllegalStateException("cannot delete $CACHE_PATH")
                }
                return null
            }

            if (target.isFile && target.readText(Charsets.UTF_8).trim() == desired.toString()) {
                return null
            }

            val temp = File(TEMP_PATH)
            if (temp.exists() && !temp.delete()) {
                throw IllegalStateException("cannot replace temporary cache file")
            }
            FileOutputStream(temp, false).use { output ->
                output.write(desired.toString().toByteArray(Charsets.UTF_8))
                output.write(byteArrayOf(10))
                output.fd.sync()
            }
            Os.chmod(temp.absolutePath, 384) // 0600; owned by system_server
            Os.rename(temp.absolutePath, target.absolutePath)
            XposedBridge.log("[$TAG][I]: staged media steps=$desired for next boot")
            return null
        } catch (t: Throwable) {
            try {
                File(TEMP_PATH).delete()
            } catch (_: Throwable) {
            }
            XposedBridge.log("[$TAG][E]: syncing media steps cache failed: $t")
            return t
        }
    }
}
