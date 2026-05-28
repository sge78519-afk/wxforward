package com.naso.wxforward;

import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HookMain implements IXposedHookLoadPackage {
    private static final String WEBHOOK_URL = "http://100.89.179.30:17800/wxmsg";
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!lpparam.packageName.equals("com.tencent.mm")) return;
        XposedBridge.log("[WxFwd] WeChat loaded, hooking...");
        hookMessageReceiver(lpparam);
    }

    private void hookMessageReceiver(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook LauncherUI.onNewIntent or similar UI entry points
            Class<?> launcherUIClass = XposedHelpers.findClass(
                "com.tencent.mm.ui.LauncherUI", lpparam.classLoader);
            XposedHelpers.findAndHookMethod(launcherUIClass, "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[WxFwd] LauncherUI.onResume triggered");
                    }
                });

            // Hook the message adapter to capture new text messages
            Class<?> msgInfoClass = XposedHelpers.findClass(
                "com.tencent.mm.storage.cc", lpparam.classLoader);
            if (msgInfoClass != null) {
                hookMsgInfo(msgInfoClass);
            }

            // Also try common WeChat 8.0 class names
            tryHookClass(lpparam, "com.tencent.mm.modelmulti.notify.c");
            tryHookClass(lpparam, "com.tencent.mm.model.b");
            tryHookClass(lpparam, "com.tencent.mm.plugin.messenger.foundation.a.a.j");
        } catch (Throwable t) {
            XposedBridge.log("[WxFwd] Hook error: " + t.getMessage());
        }
    }

    private void tryHookClass(XC_LoadPackage.LoadPackageParam lpparam, String className) {
        try {
            Class<?> clz = XposedHelpers.findClass(className, lpparam.classLoader);
            XposedBridge.log("[WxFwd] Found class: " + className);
            XposedHelpers.findAndHookMethod(clz, "a", 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        XposedBridge.log("[WxFwd] Intercepted from: " + className);
                        processMessageArgs(param.args);
                    }
                });
        } catch (Throwable ignored) {}
    }

    private void hookMsgInfo(Class<?> msgInfoClass) {
        try {
            XposedHelpers.findAndHookMethod(msgInfoClass, "getContent",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object result = param.getResult();
                        if (result instanceof String) {
                            String content = (String) result;
                            if (content != null && !content.isEmpty()) {
                                XposedBridge.log("[WxFwd] Message content: " + content.substring(0, 
                                    Math.min(content.length(), 50)));
                                sendToWebhook(content, getFieldString(param.thisObject, "field_talker"));
                            }
                        }
                    }
                });
        } catch (Throwable t) {
            XposedBridge.log("[WxFwd] MsgInfo hook error: " + t.getMessage());
        }
    }

    private static String getFieldString(Object obj, String fieldName) {
        try {
            Object val = XposedHelpers.getObjectField(obj, fieldName);
            return val != null ? val.toString() : "";
        } catch (Throwable t) {
            return "";
        }
    }

    private static void processMessageArgs(Object[] args) {
        if (args == null || args.length == 0) return;
        for (Object arg : args) {
            if (arg == null) continue;
            try {
                String talker = getFieldString(arg, "field_talker");
                String content = getFieldString(arg, "field_content");
                String createTime = getFieldString(arg, "field_createTime");
                if (content != null && !content.isEmpty()) {
                    sendToWebhook("talker=" + talker + "&content=" + content + "&time=" + createTime, talker);
                }
            } catch (Throwable ignored) {}
        }
    }

    private static void sendToWebhook(String message, String talker) {
        executor.execute(() -> {
            try {
                URL url = new URL(WEBHOOK_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                conn.setDoOutput(true);
                conn.setConnectTimeout(3000);
                conn.setReadTimeout(3000);

                String data = "talker=" + (talker != null ? talker : "") + "&content=" + (message != null ? message : "");
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data.getBytes("UTF-8"));
                    os.flush();
                }
                int code = conn.getResponseCode();
                XposedBridge.log("[WxFwd] POST to webhook: " + code);
                conn.disconnect();
            } catch (Exception e) {
                XposedBridge.log("[WxFwd] Webhook error: " + e.getMessage());
            }
        });
    }
}
