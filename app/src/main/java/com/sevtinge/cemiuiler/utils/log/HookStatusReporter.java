package com.sevtinge.cemiuiler.utils.log;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import com.sevtinge.cemiuiler.XposedInit;
import com.sevtinge.cemiuiler.provider.SharedPrefsProvider;
import com.sevtinge.cemiuiler.utils.PrefsUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行在被 hook 的进程内：收集各个 hook 的安装结果，延迟后通过模块的 Provider 上报给模块进程。
 * 上报在后台线程进行：等“系统就绪”事件后才开始（system_server 启动早期 Provider 不可用），
 * 失败最多再试一次，不轮询，不会阻塞 hook 流程。
 */
public final class HookStatusReporter {
    private static final long FIRST_DELAY_MS = 3000;
    private static final long RETRY_DELAY_MS = 5000;
    private static final int MAX_TRIES = 2;

    private static final class Pending {
        final String packageName;
        final String processName;
        final List<String> entries = new ArrayList<>();
        int sentCount = -1;

        Pending(String packageName, String processName) {
            this.packageName = packageName;
            this.processName = processName;
        }
    }

    private static final Map<String, Pending> sPending = new LinkedHashMap<>();
    private static boolean sScheduled = false;
    private static boolean sDirty = false;

    private HookStatusReporter() {
    }

    public static void record(String packageName, String processName, String hookName, boolean ok, Throwable error) {
        try {
            if (packageName == null || hookName == null) return;
            String process = processName == null ? packageName : processName;
            String entry = (ok ? "ok" : "fail") + "|" + clean(hookName, 80) + "|" + (ok ? "" : describe(error));
            synchronized (HookStatusReporter.class) {
                String key = packageName + "|" + process;
                Pending pending = sPending.get(key);
                if (pending == null) {
                    pending = new Pending(packageName, process);
                    sPending.put(key, pending);
                }
                pending.entries.add(entry);
                sDirty = true;
                schedule();
            }
        } catch (Throwable ignored) {
        }
    }

    private static String describe(Throwable t) {
        if (t == null) return "";
        Throwable root = t;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return clean(root.getClass().getSimpleName() + ": " + root.getMessage(), 160);
    }

    private static String clean(String text, int max) {
        if (text == null) return "";
        int nl = text.indexOf('\n');
        if (nl >= 0) text = text.substring(0, nl);
        text = text.replace('|', '/').trim();
        return text.length() > max ? text.substring(0, max) : text;
    }

    // 调用时已持有 HookStatusReporter.class 锁
    private static void schedule() {
        if (sScheduled) return;
        sScheduled = true;
        // 事件驱动：系统就绪后才开始；之后等一小段时间攒一批再上报（一次性，不是轮询）
        PrefsUtils.runWhenSystemReady(() -> {
            try {
                Thread.sleep(FIRST_DELAY_MS);
                boolean sent = false;
                for (int i = 0; i < MAX_TRIES; i++) {
                    if (flush()) {
                        sent = true;
                        break;
                    }
                    if (i + 1 < MAX_TRIES) Thread.sleep(RETRY_DELAY_MS);
                }
                // 上报成功说明模块 Provider 此刻可用：启动时没读到配置的进程在这里补读一次
                if (sent) PrefsUtils.retryLateInit();
            } catch (InterruptedException ignored) {
            } catch (Throwable ignored) {
            } finally {
                synchronized (HookStatusReporter.class) {
                    sScheduled = false;
                    if (sDirty) schedule();
                }
            }
        }, false);
    }

    /**
     * @return true 表示没有需要上报的内容，或全部上报成功
     */
    private static boolean flush() {
        Context context = PrefsUtils.getSystemContext();
        if (context == null) return false;

        List<Pending> snapshot = new ArrayList<>();
        List<List<String>> copies = new ArrayList<>();
        synchronized (HookStatusReporter.class) {
            sDirty = false;
            for (Pending pending : sPending.values()) {
                if (pending.sentCount == pending.entries.size()) continue;
                snapshot.add(pending);
                copies.add(new ArrayList<>(pending.entries));
            }
        }
        if (snapshot.isEmpty()) return true;

        boolean allOk = true;
        Uri uri = Uri.parse("content://" + SharedPrefsProvider.AUTHORITY);
        for (int i = 0; i < snapshot.size(); i++) {
            Pending pending = snapshot.get(i);
            List<String> entries = copies.get(i);
            ArrayList<String> toSend = new ArrayList<>(entries);
            // 配置通道自检：被 hook 进程内读不到配置时，所有开关都会回退为默认值
            if (!PrefsUtils.sConfigLoaded) {
                String why = PrefsUtils.sLastLoadNote;
                toSend.add("fail|[ConfigPrefs]|配置为空，设置可能未生效" + (why == null ? "" : "；" + clean(why, 160)));
            } else {
                String src = PrefsUtils.sConfigSource;
                toSend.add("ok|[ConfigPrefs]|" + (src == null ? "" : clean(src, 40)));
            }
            try {
                Bundle extras = new Bundle();
                extras.putString(HookStatusStore.EXTRA_PACKAGE, pending.packageName);
                extras.putString(HookStatusStore.EXTRA_PROCESS, pending.processName);
                extras.putStringArrayList(HookStatusStore.EXTRA_ENTRIES, toSend);
                Bundle result = context.getContentResolver().call(uri, HookStatusStore.METHOD_REPORT, null, extras);
                if (result != null && result.getBoolean("ok")) {
                    synchronized (HookStatusReporter.class) {
                        pending.sentCount = entries.size();
                        if (pending.entries.size() != entries.size()) sDirty = true;
                    }
                } else {
                    allOk = false;
                }
            } catch (Throwable t) {
                allOk = false;
            }
        }
        return allOk && !sDirtyNow();
    }

    private static synchronized boolean sDirtyNow() {
        return sDirty;
    }
}
