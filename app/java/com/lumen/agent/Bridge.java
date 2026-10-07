package com.lumen.agent;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class Bridge {

    public static final String VERSION = "1.0.0";

    private final Activity act;
    private final WebView web;
    private final MiniShell shell;

    public Bridge(Activity act, WebView web) {
        this.act = act;
        this.web = web;
        File base = act.getExternalFilesDir(null);
        if (base == null) base = act.getFilesDir();
        this.shell = new MiniShell(new File(base, "workspace"));
    }

    private void callJs(String fn, Object... args) {
        StringBuilder sb = new StringBuilder("window.__lumen && window.__lumen.").append(fn).append("(");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(",");
            if (args[i] instanceof Number || args[i] instanceof Boolean) sb.append(args[i]);
            else sb.append(JSONObject.quote(String.valueOf(args[i])));
        }
        sb.append(")");
        final String js = sb.toString();
        act.runOnUiThread(() -> {
            try {
                web.evaluateJavascript(js, null);
            } catch (Exception ignored) {
            }
        });
    }

    

    @JavascriptInterface
    public String env() {
        try {
            JSONObject o = new JSONObject();
            o.put("version", VERSION);
            o.put("platform", "android");
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("device", Build.MANUFACTURER + " " + Build.MODEL);
            o.put("abi", Build.SUPPORTED_ABIS.length > 0 ? Build.SUPPORTED_ABIS[0] : "?");
            o.put("home", shell.getCwd().getAbsolutePath());
            o.put("sandbox", shell.getRoot().getAbsolutePath());
            o.put("termux", termuxAvailable());
            return o.toString();
        } catch (Exception e) {
            return "{\"version\":\"" + VERSION + "\"}";
        }
    }

    @JavascriptInterface
    public boolean termuxAvailable() {
        try {
            act.getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    @JavascriptInterface
    public String cwd() { return shell.cwdPath(); }

    

    
    @JavascriptInterface
    public String shell(String command) {
        try {
            return shell.run(command);
        } catch (Exception e) {
            return "ошибка: " + e.getMessage();
        }
    }

    
    @JavascriptInterface
    public String tool(String name, String argsJson) {
        JSONObject out = new JSONObject();
        try {
            JSONObject args = argsJson == null || argsJson.isEmpty() ? new JSONObject() : new JSONObject(argsJson);
            String result = shell.toolResult(name, args);
            boolean failed = result.startsWith("ошибка:");
            out.put("ok", !failed);
            out.put("output", result);
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("output", "ошибка: " + e.getMessage());
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    

    
    @JavascriptInterface
    public String termuxRun(String command, String workdir, int timeoutMs) {
        JSONObject out = new JSONObject();
        if (!termuxAvailable()) {
            try {
                out.put("ok", false);
                out.put("fallback", true);
                out.put("output", "Termux не установлен. Встроенный шелл: Настройки → Движок → «Встроенный».");
            } catch (Exception ignored) { }
            return out.toString();
        }

        final String action = "com.lumen.agent.RESULT." + UUID.randomUUID();
        final CountDownLatch latch = new CountDownLatch(1);
        final String[] holder = new String[1];

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                try {
                    String stdout = null, stderr = null;
                    Integer exit = null;
                    Bundle b = intent.getBundleExtra("result");
                    if (b == null) b = intent.getBundleExtra("com.termux.RUN_COMMAND_RESULT_BUNDLE");
                    if (b != null) {
                        stdout = b.getString("stdout");
                        stderr = b.getString("stderr");
                        if (b.containsKey("exitCode")) exit = b.getInt("exitCode");
                    }
                    if (stdout == null && intent.hasExtra("stdout")) stdout = intent.getStringExtra("stdout");
                    if (stderr == null && intent.hasExtra("stderr")) stderr = intent.getStringExtra("stderr");
                    if (exit == null && intent.hasExtra("exitCode")) exit = intent.getIntExtra("exitCode", -1);

                    JSONObject o = new JSONObject();
                    if (stdout == null && stderr == null) {
                        o.put("ok", false);
                        o.put("output", "Termux принял команду, но не вернул вывод.\n"
                                + "Проверь: ~/.termux/termux.properties → allow-external-apps=true, затем перезапусти Termux.");
                    } else {
                        String text = (stdout == null ? "" : stdout) + (stderr == null ? "" : stderr);
                        o.put("ok", exit == null || exit == 0);
                        o.put("output", text.trim().isEmpty() ? "(команда завершилась без вывода)" : text);
                        o.put("exit", exit == null ? 0 : exit);
                    }
                    holder[0] = o.toString();
                } catch (Exception e) {
                    holder[0] = "{\"ok\":false,\"output\":\"ошибка приёма результата: " + e.getMessage() + "\"}";
                } finally {
                    latch.countDown();
                }
            }
        };

        try {
            if (Build.VERSION.SDK_INT >= 33) {
                act.registerReceiver(receiver, new IntentFilter(action), Context.RECEIVER_EXPORTED);
            } else {
                act.registerReceiver(receiver, new IntentFilter(action));
            }

            Intent i = new Intent();
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.setAction("com.termux.RUN_COMMAND");
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            i.putExtra("com.termux.RUN_COMMAND_WORKDIR",
                    workdir != null && !workdir.isEmpty() ? workdir : "/data/data/com.termux/files/home");
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);

            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pi = PendingIntent.getBroadcast(act, 0, new Intent(action).setPackage(act.getPackageName()), flags);
            i.putExtra("com.termux.RUN_COMMAND_PENDING_INTENT", pi);

            try {
                act.startService(i);
            } catch (Exception e) {
                act.startForegroundService(i);
            }

            if (!latch.await(Math.max(3000, timeoutMs), TimeUnit.MILLISECONDS)) {
                JSONObject o = new JSONObject();
                o.put("ok", false);
                o.put("output", "Termux не ответил за " + (timeoutMs / 1000) + " с.\n"
                        + "Проверь в Termux: ~/.termux/termux.properties → allow-external-apps=true (потом перезапусти Termux).\n"
                        + "Либо переключи движок на «Встроенный» в настройках.");
                holder[0] = o.toString();
            }
        } catch (Exception e) {
            try {
                JSONObject o = new JSONObject();
                o.put("ok", false);
                o.put("output", "не удалось запустить через Termux: " + e.getMessage());
                holder[0] = o.toString();
            } catch (Exception ignored) { }
        } finally {
            try {
                act.unregisterReceiver(receiver);
            } catch (Exception ignored) { }
        }
        return holder[0] != null ? holder[0] : "{\"ok\":false,\"output\":\"неизвестная ошибка Termux\"}";
    }

    
    @JavascriptInterface
    public String termuxVisible(String command) {
        JSONObject out = new JSONObject();
        try {
            if (!termuxAvailable()) {
                out.put("ok", false);
                out.put("output", "Termux не установлен");
                return out.toString();
            }
            Intent i = new Intent();
            i.setClassName("com.termux", "com.termux.app.RunCommandService");
            i.setAction("com.termux.RUN_COMMAND");
            i.putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash");
            i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS", new String[]{"-lc", command});
            i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", false);
            i.putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", 0);
            try {
                act.startService(i);
            } catch (Exception e) {
                act.startForegroundService(i);
            }
            out.put("ok", true);
            out.put("output", "команда отправлена в Termux");
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("output", "ошибка: " + e.getMessage());
            } catch (Exception ignored) { }
        }
        return out.toString();
    }

    

    
    @JavascriptInterface
    public void chat(final String reqId, final String payloadJson, final boolean stream) {
        new Thread(() -> {
            HttpURLConnection conn = null;
            try {
                JSONObject payload = new JSONObject(payloadJson);
                String baseUrl = payload.optString("baseUrl", "https://lumen.unionium.org/api/v1");
                String apiKey = payload.optString("apiKey", "");
                JSONObject body = new JSONObject(payloadJson);
                body.remove("baseUrl");
                body.remove("apiKey");
                body.put("stream", true);

                URL url = new URL(baseUrl.replaceAll("/+$", "") + "/chat/completions/");
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(300000);
                conn.setDoOutput(true);
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                conn.setRequestProperty("Authorization", "Bearer " + apiKey);
                conn.setRequestProperty("Accept", "text/event-stream");
                conn.setRequestProperty("User-Agent", "lumen-agent-gui/" + VERSION);

                byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(data.length);
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(data);
                }

                int code = conn.getResponseCode();
                InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
                if (code >= 400) {
                    String raw = readAll(is);
                    callJs("onError", reqId, errText(raw, code));
                    return;
                }

                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
                StringBuilder content = new StringBuilder();
                JSONObject toolCalls = new JSONObject();
                JSONObject usage = null;
                String line;

                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) continue;
                    if (!line.startsWith("data:")) {
                        
                        if (content.length() == 0) {
                            try {
                                JSONObject o = new JSONObject(line);
                                String err = extractError(o);
                                if (err != null) { callJs("onError", reqId, err); return; }
                                if (o.has("choices")) emitMessage(reqId, o, content, toolCalls);
                                if (o.has("usage")) usage = o.optJSONObject("usage");
                            } catch (Exception ignored) { }
                        }
                        continue;
                    }
                    String chunk = line.substring(5).trim();
                    if (chunk.equals("[DONE]")) break;
                    JSONObject o;
                    try {
                        o = new JSONObject(chunk);
                    } catch (Exception e) {
                        continue;
                    }
                    String err = extractError(o);
                    if (err != null) { callJs("onError", reqId, err); return; }
                    if (o.has("usage") && !o.isNull("usage")) usage = o.optJSONObject("usage");

                    JSONArray choices = o.optJSONArray("choices");
                    if (choices == null || choices.length() == 0) continue;
                    JSONObject choice = choices.getJSONObject(0);
                    JSONObject delta = choice.optJSONObject("delta");
                    if (delta == null) delta = choice.optJSONObject("message");
                    if (delta == null) continue;

                    if (delta.has("content") && !delta.isNull("content")) {
                        String text = delta.getString("content");
                        if (!text.isEmpty()) {
                            content.append(text);
                            callJs("onDelta", reqId, text);
                        }
                    }
                    JSONArray tcs = delta.optJSONArray("tool_calls");
                    if (tcs != null) {
                        for (int k = 0; k < tcs.length(); k++) {
                            JSONObject tc = tcs.getJSONObject(k);
                            int idx = tc.optInt("index", 0);
                            String key = String.valueOf(idx);
                            JSONObject slot = toolCalls.optJSONObject(key);
                            if (slot == null) {
                                slot = new JSONObject();
                                slot.put("id", "");
                                slot.put("name", "");
                                slot.put("arguments", "");
                                toolCalls.put(key, slot);
                            }
                            if (tc.has("id") && !tc.isNull("id")) slot.put("id", tc.getString("id"));
                            JSONObject fn = tc.optJSONObject("function");
                            if (fn != null) {
                                if (fn.has("name") && !fn.isNull("name")) slot.put("name", slot.optString("name") + fn.getString("name"));
                                if (fn.has("arguments") && !fn.isNull("arguments")) slot.put("arguments", slot.optString("arguments") + fn.getString("arguments"));
                            }
                        }
                    }
                }

                JSONObject result = new JSONObject();
                result.put("content", content.toString());
                JSONArray calls = new JSONArray();
                for (int k = 0; k < toolCalls.length(); k++) {
                    JSONObject slot = toolCalls.optJSONObject(String.valueOf(k));
                    if (slot != null && !slot.optString("name").isEmpty()) calls.put(slot);
                }
                result.put("toolCalls", calls);
                result.put("usage", usage == null ? JSONObject.NULL : usage);
                callJs("onEnd", reqId, result.toString());
            } catch (Exception e) {
                callJs("onError", reqId, "ошибка сети: " + e.getMessage());
            } finally {
                if (conn != null) conn.disconnect();
            }
        }).start();
    }

    private void emitMessage(String reqId, JSONObject o, StringBuilder content, JSONObject toolCalls) {
        try {
            JSONObject msg = o.getJSONArray("choices").getJSONObject(0).optJSONObject("message");
            if (msg == null) return;
            if (msg.has("content") && !msg.isNull("content")) {
                String text = msg.getString("content");
                content.append(text);
                callJs("onDelta", reqId, text);
            }
            JSONArray tcs = msg.optJSONArray("tool_calls");
            if (tcs != null) {
                for (int k = 0; k < tcs.length(); k++) {
                    toolCalls.put(String.valueOf(k), tcs.getJSONObject(k));
                }
            }
        } catch (Exception ignored) { }
    }

    
    @JavascriptInterface
    public String ping(String apiKey, String baseUrl) {
        JSONObject out = new JSONObject();
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject();
            body.put("model", "mini");
            JSONArray msgs = new JSONArray();
            JSONObject m = new JSONObject();
            m.put("role", "user");
            m.put("content", "ping");
            msgs.put(m);
            body.put("messages", msgs);
            body.put("max_tokens", 5);

            URL url = new URL(baseUrl.replaceAll("/+$", "") + "/chat/completions/");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(45000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            conn.setRequestProperty("Authorization", "Bearer " + apiKey);

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }
            int code = conn.getResponseCode();
            String raw = readAll(code >= 400 ? conn.getErrorStream() : conn.getInputStream());
            if (code >= 400) {
                out.put("ok", false);
                out.put("message", errText(raw, code));
            } else {
                JSONObject o = new JSONObject(raw);
                String err = extractError(o);
                if (err != null) {
                    out.put("ok", false);
                    out.put("message", err);
                } else {
                    out.put("ok", true);
                    out.put("message", "ключ принят, API отвечает");
                }
            }
        } catch (Exception e) {
            try {
                out.put("ok", false);
                out.put("message", "нет связи: " + e.getMessage());
            } catch (Exception ignored) { }
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out.toString();
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        return sb.toString();
    }

    private static String extractError(JSONObject o) {
        if (o == null || !o.has("error") || o.isNull("error")) return null;
        Object err = o.opt("error");
        if (err instanceof JSONObject) {
            JSONObject e = (JSONObject) err;
            String m = e.optString("message", "");
            return m.isEmpty() ? e.toString() : m;
        }
        return String.valueOf(err);
    }

    private static String errText(String raw, int code) {
        try {
            JSONObject o = new JSONObject(raw);
            String err = extractError(o);
            if (err != null) return "HTTP " + code + ": " + err;
            if (o.has("detail")) return "HTTP " + code + ": " + o.optString("detail");
        } catch (Exception ignored) { }
        return "HTTP " + code + ": " + (raw == null || raw.isEmpty() ? "пустой ответ" : raw.trim());
    }

    

    @JavascriptInterface
    public void copyText(String text) {
        act.runOnUiThread(() -> {
            try {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) act.getSystemService(Context.CLIPBOARD_SERVICE);
                cm.setPrimaryClip(android.content.ClipData.newPlainText("lumen", text));
                callJs("onToast", "Скопировано");
            } catch (Exception ignored) { }
        });
    }

    @JavascriptInterface
    public void openUrl(String url) {
        act.runOnUiThread(() -> {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)));
            } catch (Exception e) {
                callJs("onToast", "не удалось открыть ссылку");
            }
        });
    }

    @JavascriptInterface
    public void toast(String text) {
        callJs("onToast", text);
    }
}
