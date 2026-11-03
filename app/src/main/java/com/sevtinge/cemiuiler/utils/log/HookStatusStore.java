package com.sevtinge.cemiuiler.utils.log;

import android.content.Context;
import android.content.SharedPreferences;

import com.sevtinge.cemiuiler.utils.Helpers;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 保存各个被 hook 进程上报的 hook 安装结果（运行在模块进程内，由 SharedPrefsProvider 写入，设置界面读取）。
 * 条目格式：状态|名称|信息，状态为 ok 或 fail。
 */
public final class HookStatusStore {
    public static final String METHOD_REPORT = "report_hook_status";
    public static final String EXTRA_PACKAGE = "package";
    public static final String EXTRA_PROCESS = "process";
    public static final String EXTRA_ENTRIES = "entries";

    private static final String PREFS_NAME = "hook_status";

    private HookStatusStore() {
    }

    public static final class Report {
        public String packageName;
        public String processName;
        public long time;
        public final List<String> entries = new ArrayList<>();
    }

    private static SharedPreferences prefs(Context context) {
        return Helpers.getProtectedContext(context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static void save(Context context, String packageName, String processName, List<String> entries) throws Exception {
        if (context == null || packageName == null || entries == null) return;
        String process = processName == null ? packageName : processName;
        JSONObject json = new JSONObject();
        json.put("package", packageName);
        json.put("process", process);
        json.put("time", System.currentTimeMillis());
        json.put("entries", new JSONArray(entries));
        prefs(context).edit().putString(packageName + "|" + process, json.toString()).apply();
    }

    public static List<Report> readAll(Context context) {
        List<Report> reports = new ArrayList<>();
        Map<String, ?> all = prefs(context).getAll();
        for (Map.Entry<String, ?> entry : all.entrySet()) {
            try {
                JSONObject json = new JSONObject(String.valueOf(entry.getValue()));
                Report report = new Report();
                report.packageName = json.optString("package");
                report.processName = json.optString("process");
                report.time = json.optLong("time");
                JSONArray array = json.optJSONArray("entries");
                if (array != null) {
                    for (int i = 0; i < array.length(); i++) report.entries.add(array.getString(i));
                }
                reports.add(report);
            } catch (Throwable ignored) {
            }
        }
        return reports;
    }
}
