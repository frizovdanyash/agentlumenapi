package com.lumen.agent;

import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class ChatClient {

    public interface Callback {
        void onDelta(String text);
        void onEnd(String content, JSONArray toolCalls, JSONObject usage);
        void onError(String message);
    }

    private static final int ATTEMPTS = 3;

    public static void chat(final String baseUrl, final String apiKey, final JSONObject payload,
                            final Handler ui, final Callback cb) {
        new Thread(() -> stream(baseUrl, apiKey, payload, ui, cb)).start();
    }

    private static void stream(String baseUrl, String apiKey, JSONObject payload, Handler ui, Callback cb) {
        String lastError = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            Result r = attemptStream(baseUrl, apiKey, payload, ui, cb, attempt > 0);
            if (r.error == null) return;
            lastError = r.error;
            if (!r.retryable || attempt == ATTEMPTS - 1) {
                final String msg = r.error;
                ui.post(() -> cb.onError(msg));
                return;
            }
            try {
                Thread.sleep(1500L * (attempt + 1));
            } catch (InterruptedException ignored) {
            }
        }
        final String msg = lastError == null ? "неизвестная ошибка" : lastError;
        ui.post(() -> cb.onError(msg));
    }

    private static class Result {
        String error;
        boolean retryable;
    }

    private static Result attemptStream(String baseUrl, String apiKey, JSONObject payload, Handler ui, Callback cb, boolean alreadyTried) {
        Result res = new Result();
        HttpURLConnection conn = null;
        try {
            JSONObject body = new JSONObject(payload.toString());
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
            conn.setRequestProperty("User-Agent", "lumen-agent/" + MainActivity.VERSION);

            byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(data.length);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int code = conn.getResponseCode();
            InputStream is = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (code >= 400) {
                String raw = readAll(is);
                res.error = errorText(raw, code);
                res.retryable = code >= 500 || code == 429;
                return res;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
            StringBuilder content = new StringBuilder();
            Map<Integer, JSONObject> calls = new LinkedHashMap<>();
            JSONObject usage = null;
            boolean sawData = false;
            boolean streamed = false;
            String line;

            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (!line.startsWith("data:")) {
                    if (sawData) continue;
                    try {
                        JSONObject o = new JSONObject(line);
                        String err = extractError(o);
                        if (err != null) {
                            res.error = err;
                            res.retryable = retryable(err);
                            return res;
                        }
                        if (o.has("choices")) {
                            emit(o, content, calls, ui, cb, true, streamed);
                            streamed = true;
                            if (o.has("usage") && !o.isNull("usage")) usage = o.optJSONObject("usage");
                        }
                    } catch (Exception ignored) {
                    }
                    continue;
                }

                sawData = true;
                String chunk = line.substring(5).trim();
                if (chunk.equals("[DONE]")) break;
                JSONObject o;
                try {
                    o = new JSONObject(chunk);
                } catch (Exception e) {
                    continue;
                }
                String err = extractError(o);
                if (err != null) {
                    res.error = err;
                    res.retryable = retryable(err);
                    return res;
                }
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
                        streamed = true;
                        final String piece = text;
                        ui.post(() -> cb.onDelta(piece));
                    }
                }
                collectCalls(delta.optJSONArray("tool_calls"), calls);
            }

            final String finalContent = content.toString();
            final JSONObject finalUsage = usage;
            JSONArray toolCalls = new JSONArray();
            for (Map.Entry<Integer, JSONObject> e : calls.entrySet()) {
                JSONObject slot = e.getValue();
                if (slot != null && !slot.optString("name").isEmpty()) toolCalls.put(slot);
            }
            final JSONArray finalCalls = toolCalls;
            ui.post(() -> cb.onEnd(finalContent, finalCalls, finalUsage));
            return res;
        } catch (Exception e) {
            res.error = "ошибка сети: " + e.getMessage();
            res.retryable = !alreadyTried;
            return res;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static void emit(JSONObject o, StringBuilder content, Map<Integer, JSONObject> calls,
                             Handler ui, Callback cb, boolean asMessage, boolean streamed) {
        try {
            JSONObject msg = o.getJSONArray("choices").getJSONObject(0).optJSONObject(asMessage ? "message" : "delta");
            if (msg == null) return;
            if (msg.has("content") && !msg.isNull("content")) {
                String text = msg.getString("content");
                content.append(text);
                ui.post(() -> cb.onDelta(text));
            }
            collectCalls(msg.optJSONArray("tool_calls"), calls);
        } catch (Exception ignored) {
        }
    }

    private static void collectCalls(JSONArray arr, Map<Integer, JSONObject> calls) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject tc = arr.getJSONObject(i);
                int idx = tc.optInt("index", 0);
                JSONObject slot = calls.get(idx);
                if (slot == null) {
                    slot = new JSONObject();
                    slot.put("id", "");
                    slot.put("name", "");
                    slot.put("arguments", "");
                    calls.put(idx, slot);
                }
                if (tc.has("id") && !tc.isNull("id")) slot.put("id", tc.getString("id"));
                JSONObject fn = tc.optJSONObject("function");
                if (fn != null) {
                    if (fn.has("name") && !fn.isNull("name")) slot.put("name", slot.optString("name") + fn.getString("name"));
                    if (fn.has("arguments") && !fn.isNull("arguments"))
                        slot.put("arguments", slot.optString("arguments") + fn.getString("arguments"));
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static JSONObject ping(String baseUrl, String apiKey) {
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
                out.put("message", errorText(raw, code));
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
            } catch (Exception ignored) {
            }
        } finally {
            if (conn != null) conn.disconnect();
        }
        return out;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append("\n");
        return sb.toString();
    }

    public static String extractError(JSONObject o) {
        if (o == null || !o.has("error") || o.isNull("error")) return null;
        Object err = o.opt("error");
        if (err instanceof JSONObject) {
            JSONObject e = (JSONObject) err;
            String m = e.optString("message", "");
            return m.isEmpty() ? e.toString() : m;
        }
        return String.valueOf(err);
    }

    private static String errorText(String raw, int code) {
        try {
            JSONObject o = new JSONObject(raw);
            String err = extractError(o);
            if (err != null) return "HTTP " + code + ": " + err;
            if (o.has("detail")) return "HTTP " + code + ": " + o.optString("detail");
        } catch (Exception ignored) {
        }
        return "HTTP " + code + ": " + (raw == null || raw.trim().isEmpty() ? "пустой ответ" : raw.trim());
    }

    private static boolean retryable(String msg) {
        String m = msg == null ? "" : msg.toLowerCase();
        return m.contains("перегруж") || m.contains("overload") || m.contains("rate limit")
                || m.contains("timeout") || m.contains("timed out") || m.contains(" 429") || m.contains(" 500")
                || m.contains(" 502") || m.contains(" 503") || m.contains(" 504");
    }
}
