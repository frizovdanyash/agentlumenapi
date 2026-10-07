package com.lumen.agent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class Sess {

    public String id;
    public long created;
    public long updated;
    public String model = "mini";
    public List<Msg> messages = new ArrayList<>();

    public static class Msg {
        public String role;
        public String content = "";
        public boolean hidden;
        public long ts = System.currentTimeMillis();
        public String model;
        public String toolCallsJson;
        public String toolCallId;
        public long ms;
        public int promptTokens;
        public int completionTokens;
        public List<ToolCall> tools = new ArrayList<>();

        public Msg(String role) {
            this.role = role;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("role", role);
                o.put("content", content);
                o.put("hidden", hidden);
                o.put("ts", ts);
                if (model != null) o.put("model", model);
                if (toolCallsJson != null) o.put("tcs", toolCallsJson);
                if (toolCallId != null) o.put("tcid", toolCallId);
                o.put("ms", ms);
                o.put("pt", promptTokens);
                o.put("ct", completionTokens);
                if (!tools.isEmpty()) {
                    JSONArray arr = new JSONArray();
                    for (ToolCall t : tools) arr.put(t.toJson());
                    o.put("tools", arr);
                }
            } catch (Exception ignored) {
            }
            return o;
        }

        public static Msg fromJson(JSONObject o) {
            Msg m = new Msg(o.optString("role", "assistant"));
            m.content = o.optString("content", "");
            m.hidden = o.optBoolean("hidden", false);
            m.ts = o.optLong("ts", System.currentTimeMillis());
            m.model = o.optString("model", null);
            m.toolCallsJson = o.optString("tcs", null);
            m.toolCallId = o.optString("tcid", null);
            m.ms = o.optLong("ms", 0);
            m.promptTokens = o.optInt("pt", 0);
            m.completionTokens = o.optInt("ct", 0);
            JSONArray arr = o.optJSONArray("tools");
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject t = arr.optJSONObject(i);
                    if (t != null) m.tools.add(ToolCall.fromJson(t));
                }
            }
            return m;
        }
    }

    public static class ToolCall {
        public String id = "";
        public String name = "";
        public JSONObject args = new JSONObject();
        public boolean running;
        public boolean ok;
        public String output = "";
        public double secs;
        public String engine = "";
        public boolean collapsed;

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("args", args);
                o.put("ok", ok);
                o.put("output", output.length() > 4000 ? output.substring(0, 4000) : output);
                o.put("secs", secs);
                o.put("engine", engine);
                o.put("collapsed", collapsed);
            } catch (Exception ignored) {
            }
            return o;
        }

        public static ToolCall fromJson(JSONObject o) {
            ToolCall t = new ToolCall();
            t.id = o.optString("id", "");
            t.name = o.optString("name", "");
            t.args = o.optJSONObject("args") == null ? new JSONObject() : o.optJSONObject("args");
            t.ok = o.optBoolean("ok", false);
            t.output = o.optString("output", "");
            t.secs = o.optDouble("secs", 0);
            t.engine = o.optString("engine", "");
            t.collapsed = o.optBoolean("collapsed", false);
            return t;
        }
    }

    public static Sess create(String model) {
        Sess s = new Sess();
        s.id = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "-" + UUID.randomUUID().toString().substring(0, 4);
        s.created = System.currentTimeMillis();
        s.updated = s.created;
        s.model = model;
        return s;
    }

    public java.util.List<Msg> snapshot() {
        synchronized (messages) {
            return new ArrayList<>(messages);
        }
    }

    public String title() {
        synchronized (messages) {
        for (Msg m : messages) {
            if ("user".equals(m.role) && !m.hidden) {
                String t = m.content.replaceAll("\\s+", " ").trim();
                if (!t.isEmpty()) return t.length() > 42 ? t.substring(0, 42) + "…" : t;
            }
        }
        }
        return "Новый диалог";
    }

    public int visibleCount() {
        synchronized (messages) {
            int n = 0;
            for (Msg m : messages) if (!m.hidden) n++;
            return n;
        }
    }

    public JSONObject toJson() {
        JSONObject o = new JSONObject();
        try {
            synchronized (messages) {
            o.put("id", id);
            o.put("created", created);
            o.put("updated", updated);
            o.put("model", model);
            JSONArray arr = new JSONArray();
            for (Msg m : messages) arr.put(m.toJson());
            o.put("messages", arr);
            }
        } catch (Exception ignored) {
        }
        return o;
    }

    public static Sess fromJson(JSONObject o) {
        Sess s = new Sess();
        s.id = o.optString("id", UUID.randomUUID().toString());
        s.created = o.optLong("created", System.currentTimeMillis());
        s.updated = o.optLong("updated", s.created);
        s.model = o.optString("model", "mini");
        JSONArray arr = o.optJSONArray("messages");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject m = arr.optJSONObject(i);
                if (m != null) s.messages.add(Msg.fromJson(m));
            }
        }
        return s;
    }

    public static List<Sess> loadAll(Store store) {
        List<Sess> list = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(store.getStr("sessions", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o != null) list.add(fromJson(o));
            }
        } catch (Exception ignored) {
        }
        return list;
    }

    public static void saveAll(Store store, List<Sess> list) {
        JSONArray arr = new JSONArray();
        int from = Math.max(0, list.size() - 40);
        for (int i = from; i < list.size(); i++) arr.put(list.get(i).toJson());
        store.setStr("sessions", arr.toString());
    }
}
