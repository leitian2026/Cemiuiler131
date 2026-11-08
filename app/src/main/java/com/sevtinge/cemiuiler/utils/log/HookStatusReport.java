package com.sevtinge.cemiuiler.utils.log;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import com.sevtinge.cemiuiler.BuildConfig;
import com.sevtinge.cemiuiler.utils.PrefsUtils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

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

        // 模块端当前的设置签名，用来判断各进程读到的设置是不是已经过期
        SharedPreferences prefs = null;
        String currentSig = null;
        try {
            prefs = PrefsUtils.getSharedPrefs(context, false);
            currentSig = PrefsUtils.signatureOf(prefs.getAll());
        } catch (Throwable ignored) {
        }
        Map<String, Set<String>> installedByPkg = new HashMap<>();
        Set<String> stalePackages = new HashSet<>();

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
                    String source = message;
                    String sig = null;
                    int at = message.indexOf(";sig=");
                    if (at >= 0) {
                        source = message.substring(0, at);
                        sig = message.substring(at + 5);
                    }
                    boolean stale = ok && sig != null && currentSig != null && !sig.equals(currentSig);
                    if (stale) stalePackages.add(report.packageName);
                    configLine = ok
                        ? "配置读取：正常" + (source.isEmpty() ? "" : "（来源：" + source + "）")
                        + (stale ? "，但配置已过期：之后改过设置，需重启这个进程才会用上" : "")
                        : "配置读取：异常（" + message + "）";
                    if (!ok) fails.add("[配置读取] " + message);
                    continue;
                }
                Set<String> names = installedByPkg.get(report.packageName);
                if (names == null) {
                    names = new HashSet<>();
                    installedByPkg.put(report.packageName, names);
                }
                names.add(name);
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
        sb.append(buildToggleCheck(context, prefs, installedByPkg, stalePackages));
        sb.append("\n\n说明：只统计已开启功能的 hook 安装结果，不含运行期异常；");
        sb.append("“失败”通常表示系统或应用更新后相应的类/方法已变化，该功能不会生效。");
        sb.append("改设置或更新系统后，需要重启对应应用（或系统框架）再查看。");
        return sb.toString();
    }

    private static final String MAP_ASSET = "hook_toggle_map.txt";

    private static boolean readBool(SharedPreferences prefs, String key) {
        try {
            return prefs.getBoolean("prefs_key_" + key, false);
        } catch (Throwable t) {
            return false;
        }
    }

    private static String titleOf(Context context, String key) {
        try {
            int id = context.getResources().getIdentifier(key, "string", context.getPackageName());
            if (id != 0) return context.getString(id);
        } catch (Throwable ignored) {
        }
        return key;
    }

    /**
     * 开关核对：设置里开着的功能，必须出现在某个进程的记录里（装上了或装失败了）。
     * 开着却没有记录的，就是“没装上”，在这里列出来，并说明可能原因。
     * 对照表由源码生成（开关键名 → hook），只覆盖能自动判断的条件。
     */
    private static String buildToggleCheck(Context context, SharedPreferences prefs,
                                           Map<String, Set<String>> installedByPkg, Set<String> stalePackages) {
        if (prefs == null) return "\n\n开关核对：读取模块设置失败，无法核对。";
        List<String[]> rows = new ArrayList<>();
        int unverifiable = -1;
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(context.getAssets().open(MAP_ASSET), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("# unverifiable=")) {
                    try {
                        unverifiable = Integer.parseInt(line.substring(15).trim());
                    } catch (Exception ignored) {
                    }
                    continue;
                }
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] fields = line.split("\\|", -1);
                if (fields.length >= 5) rows.add(fields);
            }
        } catch (Throwable t) {
            return "\n\n开关核对：读取对照表失败（" + t + "）";
        }

        int checked = 0;
        int missingCount = 0;
        Map<String, List<String>> missingByPkg = new TreeMap<>();
        Map<String, Integer> noRecord = new TreeMap<>();
        for (String[] f : rows) {
            String pkg = f[0];
            String hook = f[1];
            boolean or = "OR".equals(f[2]);
            String[] keys = f[3].split(",");
            String safeKey = f[4];
            if (!safeKey.isEmpty() && readBool(prefs, safeKey)) continue; // 该应用的安全模式开着，模块本来就不装
            boolean on = !or;
            for (String key : keys) {
                boolean value = readBool(prefs, key);
                on = or ? (on || value) : (on && value);
            }
            if (!on) continue;
            checked++;
            Set<String> installed = installedByPkg.get(pkg);
            if (installed == null) {
                Integer old = noRecord.get(pkg);
                noRecord.put(pkg, old == null ? 1 : old + 1);
                continue;
            }
            if (!installed.contains(hook)) {
                List<String> list = missingByPkg.get(pkg);
                if (list == null) {
                    list = new ArrayList<>();
                    missingByPkg.put(pkg, list);
                }
                list.add(hook + "（" + titleOf(context, keys[0]) + "）");
                missingCount++;
            }
        }

        StringBuilder sb = new StringBuilder("\n\n开关核对：设置里开着、且能自动校验的功能共 ").append(checked).append(" 项");
        if (missingCount == 0 && noRecord.isEmpty()) {
            sb.append("，全部都有记录（装上了或装失败了）。");
        } else {
            if (missingCount > 0) {
                sb.append("\n✗ 开着但没装上 ").append(missingCount).append(" 项：");
                for (Map.Entry<String, List<String>> e : missingByPkg.entrySet()) {
                    sb.append("\n  ").append(e.getKey()).append("：").append(String.join("、", e.getValue()));
                    if (stalePackages.contains(e.getKey())) {
                        sb.append("\n    （这个应用的进程用的是旧配置，重启它后可能就装上了）");
                    }
                }
            }
            if (!noRecord.isEmpty()) {
                sb.append("\n✗ 开着，但这些应用完全没有记录（可能没在 LSPosed 里勾选作用域，或进程还没启动过）：");
                for (Map.Entry<String, Integer> e : noRecord.entrySet()) {
                    sb.append("\n  ").append(e.getKey()).append("（").append(e.getValue()).append(" 项）");
                }
            }
        }
        if (unverifiable > 0) {
            sb.append("\n另有 ").append(unverifiable).append(" 个 hook 的启用条件较复杂（如带系统版本判断），无法自动核对。");
        }
        return sb.toString();
    }
}
