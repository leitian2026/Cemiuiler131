package com.sevtinge.cemiuiler.module.hook.systemui.statusbar;

import android.view.View;
import android.widget.TextView;

import com.sevtinge.cemiuiler.module.base.BaseHook;

import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedHelpers;

public class MobileNetwork extends BaseHook {

    Class<?> mStatusBarMobileView;
    Class<?> mMobileIconState;
    Class<?> mHDController;

    @Override
    public void init() {

        mStatusBarMobileView = findClassIfExists("com.android.systemui.statusbar.StatusBarMobileView");
        mMobileIconState = findClassIfExists("com.android.systemui.statusbar.phone.StatusBarSignalPolicy$MobileIconState");

        mHDController = findClassIfExists("com.android.systemui.statusbar.policy.HDController");

        java.util.List<String> missing = new java.util.ArrayList<>();
        java.util.List<String> problems = new java.util.ArrayList<>();
        if (mStatusBarMobileView == null) missing.add("StatusBarMobileView");
        if (mMobileIconState == null) missing.add("MobileIconState");
        if (mHDController == null) missing.add("HDController");

        if (mStatusBarMobileView != null && mMobileIconState != null) {
            try {
        findAndHookMethod(mStatusBarMobileView, "initViewState", mMobileIconState, new MethodHook() {
            @Override
            protected void after(MethodHookParam param) {
                updateIconState(param, "mSmallHd", "system_ui_status_bar_icon_small_hd");
                updateIconState(param, "mVolte", "system_ui_status_bar_icon_big_hd");
            }
        });

            } catch (Throwable t) {
                problems.add("initViewState:" + t.getClass().getSimpleName());
            }
        }
        if (mStatusBarMobileView != null && mMobileIconState != null) {
            try {
        findAndHookMethod(mStatusBarMobileView, "updateState", mMobileIconState, new MethodHook() {
            @Override
            protected void after(MethodHookParam param) {
                updateIconState(param, "mSmallHd", "system_ui_status_bar_icon_small_hd");
                updateIconState(param, "mVolte", "system_ui_status_bar_icon_big_hd");
            }
        });

            } catch (Throwable t) {
                problems.add("updateState:" + t.getClass().getSimpleName());
            }
        }
        if (mStatusBarMobileView != null) {
            try {
        hookAllMethods(mStatusBarMobileView, "applyMobileState", new MethodHook() {
            @Override
            protected void after(MethodHookParam param) {
                int qpt = mPrefsMap.getStringAsInt("system_ui_status_bar_icon_mobile_network_type", 0);
                boolean singleMobileType = mPrefsMap.getBoolean("system_ui_statusbar_mobile_type_enable");
                boolean hideIndicator = mPrefsMap.getBoolean("system_ui_status_bar_mobile_indicator");
                View mMobileType = getMobileType(param, qpt, singleMobileType);
                // 隐藏移动网络活动指示器
                View mLeftInOut = (View) XposedHelpers.getObjectField(param.thisObject, "mLeftInOut");
                if (hideIndicator) {
                    View mRightInOut = (View) XposedHelpers.getObjectField(param.thisObject, "mRightInOut");
                    mLeftInOut.setVisibility(View.GONE);
                    mRightInOut.setVisibility(View.GONE);
                }
                if (mMobileType.getVisibility() == View.GONE && mLeftInOut.getVisibility() == View.GONE) {
                    View mMobileLeftContainer = (View) XposedHelpers.getObjectField(param.thisObject, "mMobileLeftContainer");
                    mMobileLeftContainer.setVisibility(View.GONE);
                }
            }
        });

            } catch (Throwable t) {
                problems.add("applyMobileState:" + t.getClass().getSimpleName());
            }
        }
        if (mHDController != null) {
            try {
        findAndHookMethod(mHDController, "update", new MethodHook() {
            @Override
            protected void before(MethodHookParam param) {
                int opt = mPrefsMap.getStringAsInt("system_ui_status_bar_icon_new_hd", 0);
                if (opt > 0) {
                    XposedHelpers.setBooleanField(param.thisObject, "mWifiAvailable", opt == 1 ? false : opt == 2);
                }
            }
        });
            } catch (Throwable t) {
                problems.add("HD update:" + t.getClass().getSimpleName());
            }
        }

        // 能装的 hook 已经装上；缺类的部分在这个系统版本上不可用，在状态页里写明
        if (!missing.isEmpty() || !problems.isEmpty()) {
            throw new IllegalStateException("部分未生效 缺类" + missing + " 失败" + problems);
        }

        // 信号
        /*hookAllMethods("com.android.systemui.statusbar.StatusBarMobileView", "applyMobileState", new MethodHook() {
            @Override
            protected void after(MethodHookParam param) throws Throwable {
                XposedHelpers.callMethod(param.thisObject, "setVisibility", View.GONE);
            }
        });*/
    }

    private static View getMobileType(MethodHookParam param, int qpt, boolean singleMobileType) {
        View mMobileType = (View) XposedHelpers.getObjectField(param.thisObject, "mMobileType");
        boolean dataConnected = (boolean) XposedHelpers.getObjectField(param.args[0], "dataConnected");
        if (qpt > 0) {
            if (qpt == 1) {
                if (singleMobileType) {
                    TextView mMobileTypeSingle = (TextView) XposedHelpers.getObjectField(param.thisObject, "mMobileTypeSingle");
                    mMobileTypeSingle.setVisibility(View.VISIBLE);
                } else {
                    mMobileType.setVisibility(View.VISIBLE);
                }
            }
            if (qpt == 3 && !dataConnected) {
                if (singleMobileType) {
                    TextView mMobileTypeSingle = (TextView) XposedHelpers.getObjectField(param.thisObject, "mMobileTypeSingle");
                    mMobileTypeSingle.setVisibility(View.GONE);
                } else {
                    mMobileType.setVisibility(View.GONE);
                }
            }
            if (qpt == 2) {
                if (singleMobileType) {
                    TextView mMobileTypeSingle = (TextView) XposedHelpers.getObjectField(param.thisObject, "mMobileTypeSingle");
                    mMobileTypeSingle.setVisibility(View.GONE);
                } else {
                    mMobileType.setVisibility(View.GONE);
                }
            }
        }
        return mMobileType;
    }

    private void updateIconState(MethodHookParam param, String fieldName, String key) {
        boolean isMobileConnected = false;
        int opt = mPrefsMap.getStringAsInt(key, 0);
        if (opt != 0) {
            View view = (View) XposedHelpers.getObjectField(param.thisObject, fieldName);
            switch (opt) {
                case 1 -> view.setVisibility(View.VISIBLE);
                case 2 -> view.setVisibility(View.GONE);
                case 3 -> {
                    view.setVisibility(View.GONE);
                    isMobileConnected = (boolean) XposedHelpers.getObjectField(param.args[0], "dataConnected");
                }
            }

            if (isMobileConnected) {
                view.setVisibility(View.VISIBLE);
            }
        }

    }
}
