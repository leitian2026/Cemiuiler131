package com.sevtinge.cemiuiler.module.hook.systemframework

import com.github.kyuubiran.ezxhelper.ClassUtils.loadClass
import com.github.kyuubiran.ezxhelper.HookFactory.`-Static`.createHook
import com.github.kyuubiran.ezxhelper.finders.MethodFinder.`-Static`.methodFinder
import com.sevtinge.cemiuiler.module.base.BaseHook
import de.robv.android.xposed.XposedHelpers

class VolumeMediaSteps : BaseHook() {
    override fun init() {
        val mediaSteps = mPrefsMap.getInt("system_framework_volume_media_steps", 15)
        if (mediaSteps <= 15) return

        // AudioService reads ro.config.media_vol_steps while constructing itself.
        // A delayed SystemProperties hook can miss that read entirely. Instead,
        // update the media stream maximum immediately before stream states are built.
        val audioService = loadClass("com.android.server.audio.AudioService")
        audioService.methodFinder().first {
            name == "createStreamStates" && parameterTypes.isEmpty()
        }.createHook {
            before {
                val maxStreamVolume = XposedHelpers.getStaticObjectField(
                    audioService,
                    "MAX_STREAM_VOLUME"
                ) as IntArray
                // AudioSystem.STREAM_MUSIC is stream index 3.
                maxStreamVolume[3] = mediaSteps
            }
        }
    }
}
