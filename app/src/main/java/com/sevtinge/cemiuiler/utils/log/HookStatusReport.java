package com.sevtinge.cemiuiler.utils.log;

import android.content.Context;
import android.os.Build;

import com.sevtinge.cemiuiler.BuildConfig;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 把各进程上报的 hook 安装结果整理成可读文本，供“功能检查”弹窗显示。
 */
public final class HookStatusReport {
    private HookStatusReport() {
    }

    public static String build(Context context) {
        StringBuilder sb = new StringBuilder();
        sb.append("模块 ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")")
            .append("，Android ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")")
            .append("，").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n\n");

        List<HookStatusStore.Report> reports;
        try {
            reports = HookStatusStore.readAll(context);
        } catch (Throwable t) {
            return sb.append("读取记录失败：").append(t).toString();
        }

        if (reports.isEmpty()) {
            return sb.append("还没有收到任何上报。\n")
                .append("请确认模块已在 LSPosed 中启用并勾选了作用域，然后重启对应应用或系统框架（重启手机最彻底），再回来查看。")
                .toString();
        }

        Collections.sort(reports, (a, b) -> {
            int c = a.packageName.compareTo(b.packageName);
            return c != 0 ? c : a.processName.compareTo(b.processName);
        });

        int totalFail = 0;
        StringBuilder body = new StringBuilder();
        SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault());
        for (HookStatusStore.Report report : reports) {
            List<String> fails = new ArrayList<>();
            List<String> oks = new ArrayList<>();
            String configLine = null;
            for (String raw : report.entries) {
                String[] parts = raw.split("\\|", 3);
                if (parts.length < 2) continue;
                boolean ok = "ok".equals(parts[0]);
                String name = parts[1];
                String message = parts.length > 2 ? parts[2] : "";
                if ("[ConfigPrefs]".equals(name)) {
                    configLine = ok ? "配置读取：正常" : "配置读取：异常（" + message + "）";
                    if (!ok) fails.add("[配置读取] " + message);
                    continue;
                }
                if (ok) oks.add(name);
                else fails.add(name + (message.isEmpty() ? "" : "：" + message));
            }
            totalFail += fails.size();

            body.append("\n\n").append(fails.isEmpty() ? "✓ " : "✗ ").append(report.packageName);
            if (!report.processName.equals(report.packageName)) {
                body.append(" (").append(report.processName).append(")");
            }
            body.append("\n  记录时间 ").append(format.format(new Date(report.time)));
            if (configLine != null) body.append("\n  ").append(configLine);
            body.append("\n  正常 ").append(oks.size()).append(" 项，失败 ").append(fails.size()).append(" 项");
            for (String fail : fails) body.append("\n  ✗ ").append(fail);
            if (!oks.isEmpty()) {
                Collections.sort(oks);
                body.append("\n  正常：").append(String.join("、", oks));
            }
        }

        sb.append("共 ").append(reports.size()).append(" 个进程有记录，失败 ").append(totalFail).append(" 项");
        sb.append(body);
        sb.append("\n\n说明：只统计已开启功能的 hook 安装结果，不含运行期异常；");
        sb.append("“失败”通常表示系统或应用更新后相应的类/方法已变化，该功能不会生效。");
        sb.append("改设置或更新系统后，需要重启对应应用（或系统框架）再查看。");
        return sb.toString();
    }
}
