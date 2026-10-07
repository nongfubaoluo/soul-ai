package com.example.soulai;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import io.github.libxposed.api.HookLoadPackageParam;
import io.github.libxposed.api.MethodHookParam;
import io.github.libxposed.api.XC_MethodHook;
import io.github.libxposed.api.XposedHelpers;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XSharedPreferences;
import io.github.libxposed.api.annotations.XposedModuleEntry;
import io.github.libxposed.api.utils.XposedLogger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Soul App LSPosed LibXposed AI Auto-Chat Automation Hook Module
 */
@XposedModuleEntry
public class SoulAiAutoChat extends XposedModule {

    public static final String MODULE_PACKAGE = "com.example.soulai";
    public static final String TARGET_PACKAGE = "com.soulapp.cn";

    private static XSharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    // Context History Memory Map: <SessionId, List<ChatMessage>>
    private final Map<String, List<ChatMessage>> chatHistoryMap = Collections.synchronizedMap(new HashMap<>());
    private String lastHandledMessage = "";
    private boolean isProcessing = false;

    // 替换原来的 initZygote (IXposedHookZygoteInit)
    @Override
    public void onZygoteInit() {
        prefs = getSharedPreferences("ai_config");
        prefs.makeWorldReadable();
    }

    // 替换原来 handleLoadPackage (IXposedHookLoadPackage)
    @Override
    public void onLoadPackage(HookLoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(TARGET_PACKAGE)) {
            return;
        }

        XposedLogger.log("[SoulAI Hook] Hooking into Soul App...");

        // Monitor Activity Lifecycle to inspect Chat Views
        XposedHelpers.findAndHookMethod(
                Activity.class,
                "onResume",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Activity activity = (Activity) param.thisObject;
                        String activityName = activity.getClass().getName();

                        if (activityName.contains("Chat") || activityName.contains("Message")) {
                            XposedLogger.log("[SoulAI] Chat Activity active: " + activityName);
                            setupChatListener(activity);
                        }
                    }
                }
        );
    }

    private void setupChatListener(final Activity activity) {
        mainHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) return;

                try {
                    View rootView = activity.getWindow().getDecorView();
                    scanViewHierarchy(activity, rootView);
                } catch (Exception e) {
                    XposedLogger.log("[SoulAI] View scan exception: " + e.getMessage());
                }

                mainHandler.postDelayed(this, 1500); // Check screen every 1.5s
            }
        }, 1000);
    }

    private void scanViewHierarchy(Activity activity, View view) {
        if (view == null || isProcessing) return;

        if (prefs != null) prefs.reload();
        boolean enabled = prefs.getBoolean("enable_auto_reply", true);
        if (!enabled) return;

        List<TextView> textViews = new ArrayList<>();
        findViewsOfType(view, TextView.class, textViews);

        String latestPeerMessage = extractLatestPeerMessage(textViews);

        if (!TextUtils.isEmpty(latestPeerMessage) && !latestPeerMessage.equals(lastHandledMessage)) {
            lastHandledMessage = latestPeerMessage;
            isProcessing = true;

            XposedLogger.log("[SoulAI] New incoming peer message: " + latestPeerMessage);
            executor.execute(() -> processAiReplySequence(activity, "default_session", latestPeerMessage));
        }
    }

    private String extractLatestPeerMessage(List<TextView> textViews) {
        for (int i = textViews.size() - 1; i >= 0; i--) {
            TextView tv = textViews.get(i);
            CharSequence text = tv.getText();
            if (text == null) continue;

            String str = text.toString().trim();
            if (str.length() > 0 && !str.equals("发送") && !str.contains(":") && !str.equals("输入新消息")) {
                return str;
            }
        }
        return null;
    }

    private void processAiReplySequence(Activity activity, String sessionId, String userMsg) {
        try {
            String apiUrl = prefs.getString("api_url", "https://api.openai.com/v1/chat/completions");
            String apiKey = prefs.getString("api_key", "");
            String model = prefs.getString("model", "gpt-3.5-turbo");
            String systemPrompt = prefs.getString("system_prompt", "你是一个幽默、贴心且有趣的Soul小助手，语言简洁自然。");
            int maxHistory = Integer.parseInt(prefs.getString("max_history", "10"));
            int responseDelay = Integer.parseInt(prefs.getString("response_delay", "2000"));

            List<ChatMessage> history = chatHistoryMap.computeIfAbsent(sessionId, k -> new ArrayList<>());
            history.add(new ChatMessage("user", userMsg));

            while (history.size() > maxHistory) {
                history.remove(0);
            }

            JSONObject payload = new JSONObject();
            payload.put("model", model);

            JSONArray messagesArray = new JSONArray();
            JSONObject sysMsg = new JSONObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);
            messagesArray.put(sysMsg);

            for (ChatMessage msg : history) {
                JSONObject m = new JSONObject();
                m.put("role", msg.role);
                m.put("content", msg.content);
                messagesArray.put(m);
            }

            payload.put("messages", messagesArray);
            payload.put("temperature", 0.7);

            String aiResponseStr = sendHttpApiRequest(apiUrl, apiKey, payload.toString());

            if (!TextUtils.isEmpty(aiResponseStr)) {
                history.add(new ChatMessage("assistant", aiResponseStr));
                Thread.sleep(responseDelay);
                mainHandler.post(() -> injectTextAndSend(activity, aiResponseStr));
            }

        } catch (Exception e) {
            XposedLogger.log("[SoulAI] Request Error: " + e.getMessage());
        } finally {
            isProcessing = false;
        }
    }

    private String sendHttpApiRequest(String urlStr, String apiKey, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            if (!TextUtils.isEmpty(apiKey)) {
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
            }
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int code = conn.getResponseCode();
            InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }

            if (code == 200) {
                JSONObject jsonRes = new JSONObject(response.toString());
                JSONArray choices = jsonRes.getJSONArray("choices");
                if (choices.length() > 0) {
                    return choices.getJSONObject(0).getJSONObject("message").getString("content");
                }
            }
        } catch (Exception e) {
            XposedLogger.log("[SoulAI] HTTP Error: " + e.getMessage());
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private void injectTextAndSend(Activity activity, String text) {
        View rootView = activity.getWindow().getDecorView();

        List<EditText> editTexts = new ArrayList<>();
        findViewsOfType(rootView, EditText.class, editTexts);

        if (editTexts.isEmpty()) return;

        EditText inputField = editTexts.get(0);
        inputField.requestFocus();
        inputField.setText(text);

        if (inputField.getText() instanceof Editable) {
            Editable editable = (Editable) inputField.getText();
            inputField.setSelection(editable.length());
        }

        boolean autoSend = prefs.getBoolean("auto_click_send", true);
        if (!autoSend) return;

        mainHandler.postDelayed(() -> {
            List<View> clickableViews = new ArrayList<>();
            findClickableSendButtons(rootView, clickableViews);

            for (View v : clickableViews) {
                if (v.isShown() && v.isEnabled()) {
                    v.performClick();
                    XposedLogger.log("[SoulAI] Auto Clicked Send!");
                    return;
                }
            }
        }, 500);
    }

    private <T extends View> void findViewsOfType(View view, Class<T> clazz, List<T> result) {
        if (clazz.isInstance(view)) {
            result.add(clazz.cast(view));
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findViewsOfType(vg.getChildAt(i), clazz, result);
            }
        }
    }

    private void findClickableSendButtons(View view, List<View> result) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            String text = tv.getText() != null ? tv.getText().toString() : "";
            if ("发送".equals(text) || "Send".equalsIgnoreCase(text)) {
                result.add(view);
            }
        }
        if (view.getContentDescription() != null && view.getContentDescription().toString().contains("发送")) {
            result.add(view);
        }
        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                findClickableSendButtons(vg.getChildAt(i), result);
            }
        }
    }

    private static class ChatMessage {
        String role;
        String content;

        ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
}
