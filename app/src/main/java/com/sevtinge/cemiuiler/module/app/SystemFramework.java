package com.sevtinge.cemiuiler.module.app;

import static com.sevtinge.cemiuiler.utils.api.VoyagerApisKt.isPad;
import static com.sevtinge.cemiuiler.utils.devicesdk.SystemSDKKt.isMoreAndroidVersion;

import com.sevtinge.cemiuiler.module.base.BaseModule;
import com.sevtinge.cemiuiler.module.hook.systemframework.AllowUntrustedTouch;
import com.sevtinge.cemiuiler.module.hook.systemframework.AppDisableService;
import com.sevtinge.cemiuiler.module.hook.systemframework.AppLinkVerify;
import com.sevtinge.cemiuiler.module.hook.systemframework.CleanOpenMenu;
import com.sevtinge.cemiuiler.module.hook.systemframework.CleanShareMenu;
import com.sevtinge.cemiuiler.module.hook.systemframework.DeleteOnPostNotification;
import com.sevtinge.cemiuiler.module.hook.systemframework.DisableCleaner;
import com.sevtinge.cemiuiler.module.hook.systemframework.DisableFreeformBlackList;
import com.sevtinge.cemiuiler.module.hook.systemframework.DisablePinVerifyPer72h;
import com.sevtinge.cemiuiler.module.hook.systemframework.FlagSecure;
import com.sevtinge.cemiuiler.module.hook.systemframework.FreeFormCount;
import com.sevtinge.cemiuiler.module.hook.systemframework.FreeformBubble;
import com.sevtinge.cemiuiler.module.hook.systemframework.MultiFreeFormSupported;
import com.sevtinge.cemiuiler.module.hook.systemframework.PackagePermissions;
import com.sevtinge.cemiuiler.module.hook.systemframework.RemoveSmallWindowRestrictions;
import com.sevtinge.cemiuiler.module.hook.systemframework.ScreenRotation;
import com.sevtinge.cemiuiler.module.hook.systemframework.SpeedInstall;
import com.sevtinge.cemiuiler.module.hook.systemframework.StickyFloatingWindows;
import com.sevtinge.cemiuiler.module.hook.systemframework.ThemeProvider;
import com.sevtinge.cemiuiler.module.hook.systemframework.ThermalBrightness;
import com.sevtinge.cemiuiler.module.hook.systemframework.UseOriginalAnimation;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeDefaultStream;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeDisableSafe;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeFirstPress;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeMediaSteps;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeSeparateControl;
import com.sevtinge.cemiuiler.module.hook.systemframework.VolumeSteps;
import com.sevtinge.cemiuiler.module.hook.systemframework.corepatch.BypassSignCheckForT;
import com.sevtinge.cemiuiler.module.hook.systemframework.display.DisplayCutout;
import com.sevtinge.cemiuiler.module.hook.systemframework.display.ToastTime;
import com.sevtinge.cemiuiler.module.hook.systemframework.freeform.OpenAppInFreeForm;
import com.sevtinge.cemiuiler.module.hook.systemframework.mipad.IgnoreStylusKeyGesture;
import com.sevtinge.cemiuiler.module.hook.systemframework.mipad.NoMagicPointer;
import com.sevtinge.cemiuiler.module.hook.systemframework.mipad.RemoveStylusBluetoothRestriction;
import com.sevtinge.cemiuiler.module.hook.systemframework.mipad.RestoreEsc;
import com.sevtinge.cemiuiler.module.hook.systemframework.mipad.SetGestureNeedFingerNum;
import com.sevtinge.cemiuiler.module.hook.systemframework.network.DualNRSupport;
import com.sevtinge.cemiuiler.module.hook.systemframework.network.DualSASupport;
import com.sevtinge.cemiuiler.module.hook.systemframework.network.N1Band;
import com.sevtinge.cemiuiler.module.hook.systemframework.network.N28Band;
import com.sevtinge.cemiuiler.module.hook.systemframework.network.N5N8Band;
import com.sevtinge.cemiuiler.module.hook.various.NoAccessDeviceLogsRequest;

import de.robv.android.xposed.XposedBridge;


public class SystemFramework extends BaseModule {

    @Override
    public void handleLoadPackage() {
        if (mPrefsMap.getBoolean("settings_disable_detailed_log"))
            XposedBridge.log("[Cemiuiler][I]: Detail log is disabled.");

        // 小窗
        safeInit("FreeFormCount", () -> initHook(new FreeFormCount(), mPrefsMap.getBoolean("system_framework_freeform_count")));
        safeInit("FreeformBubble", () -> initHook(new FreeformBubble(), mPrefsMap.getBoolean("system_framework_freeform_bubble")));
        safeInit("DisableFreeformBlackList", () -> initHook(new DisableFreeformBlackList(), mPrefsMap.getBoolean("system_framework_disable_freeform_blacklist")));
        safeInit("RemoveSmallWindowRestrictions", () -> initHook(RemoveSmallWindowRestrictions.INSTANCE, mPrefsMap.getBoolean("system_framework_disable_freeform_blacklist")));
        safeInit("StickyFloatingWindows", () -> initHook(new StickyFloatingWindows(), mPrefsMap.getBoolean("system_framework_freeform_sticky")));
        safeInit("MultiFreeFormSupported", () -> initHook(MultiFreeFormSupported.INSTANCE, mPrefsMap.getBoolean("system_framework_freeform_recents_to_small_freeform")));
        safeInit("OpenAppInFreeForm", () -> initHook(new OpenAppInFreeForm(), mPrefsMap.getBoolean("system_framework_freeform_jump")));
        // initHook(new OpenAppInFreeForm(), mPrefsMap.getBoolean("system_framework_freeform_jump"));

        // 音量
        safeInit("VolumeDefaultStream", () -> initHook(new VolumeDefaultStream()));
        safeInit("VolumeFirstPress", () -> initHook(new VolumeFirstPress(), mPrefsMap.getBoolean("system_framework_volume_first_press")));
        safeInit("VolumeSeparateControl", () -> initHook(new VolumeSeparateControl(), mPrefsMap.getBoolean("system_framework_volume_separate_control")));
        safeInit("VolumeSteps", () -> initHook(new VolumeSteps(), mPrefsMap.getInt("system_framework_volume_steps", 0) > 0));
        safeInit("VolumeMediaSteps", () -> initHook(new VolumeMediaSteps(), mPrefsMap.getBoolean("system_framework_volume_media_steps_enable")));
        safeInit("VolumeDisableSafe", () -> initHook(new VolumeDisableSafe(), mPrefsMap.getBoolean("system_framework_volume_disable_safe")));
        // initHook(new ClockShowSecond(), mPrefsMap.getBoolean("system_ui_statusbar_clock_show_second"));

        safeInit("ThemeProvider", () -> initHook(new ThemeProvider(), mPrefsMap.getBoolean("various_enable_super_function") && mPrefsMap.getBoolean("various_theme_crack")));

        // 其他
        safeInit("ScreenRotation", () -> initHook(new ScreenRotation(), mPrefsMap.getBoolean("system_framework_screen_all_rotations")));
        safeInit("CleanShareMenu", () -> initHook(new CleanShareMenu(), mPrefsMap.getBoolean("system_framework_clean_share_menu")));
        safeInit("CleanOpenMenu", () -> initHook(new CleanOpenMenu(), mPrefsMap.getBoolean("system_framework_clean_open_menu")));
        safeInit("AllowUntrustedTouch", () -> initHook(new AllowUntrustedTouch(), mPrefsMap.getBoolean("system_framework_allow_untrusted_touch")));
        safeInit("FlagSecure", () -> initHook(new FlagSecure(), mPrefsMap.getBoolean("system_other_flag_secure")));
        safeInit("AppLinkVerify", () -> initHook(new AppLinkVerify(), mPrefsMap.getBoolean("system_framework_disable_app_link_verify")));
        safeInit("UseOriginalAnimation", () -> initHook(new UseOriginalAnimation(), mPrefsMap.getBoolean("system_framework_other_use_original_animation")));
        safeInit("SpeedInstall", () -> initHook(new SpeedInstall(), mPrefsMap.getBoolean("system_framework_other_speed_install")));
        safeInit("DeleteOnPostNotification", () -> initHook(DeleteOnPostNotification.INSTANCE, mPrefsMap.getBoolean("system_other_delete_on_post_notification")));
        safeInit("NoAccessDeviceLogsRequest", () -> initHook(NoAccessDeviceLogsRequest.INSTANCE, mPrefsMap.getBoolean("various_disable_access_device_logs")));

        // 显示
        safeInit("DisplayCutout", () -> initHook(DisplayCutout.INSTANCE, mPrefsMap.getBoolean("system_ui_display_hide_cutout_enable")));
        safeInit("ToastTime", () -> initHook(new ToastTime(), mPrefsMap.getBoolean("system_ui_display_toast_times_enable")));
        // initHook(new AutoBrightness(), mPrefsMap.getBoolean("system_control_center_auto_brightness"));

        // 位置模拟
        // initHook(new LocationSimulation(), false);

        // 小米/红米平板设置相关
        if (isPad()) {
            safeInit("IgnoreStylusKeyGesture", () -> initHook(IgnoreStylusKeyGesture.INSTANCE, mPrefsMap.getBoolean("mipad_input_ingore_gesture")));
            safeInit("NoMagicPointer", () -> initHook(NoMagicPointer.INSTANCE, mPrefsMap.getBoolean("mipad_input_close_magic")));
            safeInit("RemoveStylusBluetoothRestriction", () -> initHook(RemoveStylusBluetoothRestriction.INSTANCE, mPrefsMap.getBoolean("mipad_input_disable_bluetooth")));
            safeInit("RestoreEsc", () -> initHook(RestoreEsc.INSTANCE, mPrefsMap.getBoolean("mipad_input_restore_esc")));
            safeInit("SetGestureNeedFingerNum", () -> initHook(SetGestureNeedFingerNum.INSTANCE, mPrefsMap.getBoolean("mipad_input_need_finger_num")));
        }

        // 核心破解
        if (isMoreAndroidVersion(33)) {
            safeInit("BypassSignCheckForT", () -> initHook(BypassSignCheckForT.INSTANCE, mPrefsMap.getBoolean("system_framework_core_patch_auth_creak") ||
                mPrefsMap.getBoolean("system_framework_core_patch_disable_integrity")));
        }

        // 网络
        safeInit("DualNRSupport", () -> initHook(DualNRSupport.INSTANCE, mPrefsMap.getBoolean("phone_double_5g_nr")));
        safeInit("DualSASupport", () -> initHook(DualSASupport.INSTANCE, mPrefsMap.getBoolean("phone_double_5g_sa")));
        safeInit("N1Band", () -> initHook(N1Band.INSTANCE, mPrefsMap.getBoolean("phone_n1")));
        safeInit("N5N8Band", () -> initHook(N5N8Band.INSTANCE, mPrefsMap.getBoolean("phone_n5_n8")));
        safeInit("N28Band", () -> initHook(N28Band.INSTANCE, mPrefsMap.getBoolean("phone_n28")));

        // Other
        safeInit("PackagePermissions", () -> initHook(new PackagePermissions()));
        safeInit("GlobalActions", () -> initHook(new GlobalActions(), mLoadPackageParam.processName.equals("android")));
        safeInit("AppDisableService", () -> initHook(new AppDisableService()));
        safeInit("ThermalBrightness", () -> initHook(new ThermalBrightness(), mPrefsMap.getBoolean("system_framework_other_thermal_brightness")));
        safeInit("DisableCleaner", () -> initHook(DisableCleaner.INSTANCE, mPrefsMap.getBoolean("system_framework_other_disable_cleaner")));
        safeInit("DisablePinVerifyPer72h", () -> initHook(new DisablePinVerifyPer72h(), mPrefsMap.getBoolean("system_framework_disable_72h_verify")));
    }

}
