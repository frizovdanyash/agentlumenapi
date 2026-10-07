package com.lumen.agent;

import android.content.Context;
import android.os.Handler;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Agent {

    public static final String[] MODEL_KEYS = {"mini", "fast", "max", "ultra", "bare"};
    public static final String[] MODEL_LABELS = {"Mini", "Fast", "Max", "Ultra", "Bare"};
    public static final String[] MODEL_DESC = {
            "самая дешёвая и быстрая — черновики",
            "быстрая и умная — оптимальный выбор",
            "сильная модель — код и сложные задачи",
            "топовая — максимум качества",
            "без цензуры — ответственность на вас"
    };
    public static final int[] MODEL_CTX = {32000, 131072, 200000, 200000, 131072};
    public static final boolean[] MODEL_NATIVE_TOOLS = {false, true, true, true, true};

    public interface Listener {
        void onDelta(String text);
        void onAssistantDone(Sess.Msg msg);
        void onToolCreated(Sess.ToolCall tool);
        void onToolUpdated(Sess.ToolCall tool);
        void onError(String message);
        void onBusy(boolean busy);
        void onStepDone();
    }

    public interface Confirmer {
        boolean ask(String title, String detail);
    }

    private final Context ctx;
    private final Store store;
    private final Handler ui;
    private final MiniShell shell;
    private final Listener listener;
    private final Confirmer confirmer;
    private volatile boolean stopped;

    private static final Pattern TOOL_FENCE = Pattern.compile("```(?:tool|json)\\s*(\\{[\\s\\S]*?\\})\\s*```");
    private static final Pattern TOOL_LINE = Pattern.compile("(?m)^\\s*(\\{\\s*\"name\"\\s*:[^\\n]*\\})\\s*$");
    private static final Pattern DANGEROUS = Pattern.compile(
            "(\\brm\\s+-[a-z]*r[a-z]*f?\\s+(/|\\*|~|\\$HOME)|\\bsudo\\b|\\bmkfs\\b|\\bdd\\s+if=|\\bchmod\\s+-R\\s+777\\s+/|\\bshutdown\\b|\\breboot\\b)");
    private static final Pattern BUILTIN_CMD = Pattern.compile(
            "^\\s*(pwd|ls|cd|cat|echo|mkdir|touch|rm|mv|cp|head|tail|wc|grep|find|date|whoami|uname|du|help|clear)\\b");

    public Agent(Context ctx, Handler ui, MiniShell shell, Listener listener, Confirmer confirmer) {
        this.ctx = ctx;
        this.store = new Store(ctx);
        this.ui = ui;
        this.shell = shell;
        this.listener = listener;
        this.confirmer = confirmer;
    }

    public void stop() {
        stopped = true;
    }

    public static int modelIndex(String key) {
        for (int i = 0; i < MODEL_KEYS.length; i++) if (MODEL_KEYS[i].equals(key)) return i;
        return 0;
    }

    public void send(final Sess sess, final String text) {
        stopped = false;
        Sess.Msg user = new Sess.Msg("user");
        user.content = text;
        synchronized (sess.messages) {
            sess.messages.add(user);
        }
        ui.post(() -> listener.onBusy(true));
        new Thread(() -> runLoop(sess)).start();
    }

    private void runLoop(Sess sess) {
        int steps = 0;
        String model = store.getStr("model", "mini");
        boolean nativeTools = MODEL_NATIVE_TOOLS[modelIndex(model)];
        List<String> signatures = new ArrayList<>();
        try {
            while (steps < 12) {
                if (stopped) break;
                steps++;
                JSONObject payload = new JSONObject();
                payload.put("model", model);
                payload.put("temperature", 0.3);
                payload.put("max_tokens", 4096);
                payload.put("messages", buildMessages(sess, model));
                if (nativeTools) {
                    payload.put("tools", nativeToolsSchema());
                    payload.put("tool_choice", "auto");
                }

                final Object lock = new Object();
                final String[] contentHolder = new String[]{""};
                final JSONArray[] callsHolder = new JSONArray[]{new JSONArray()};
                final JSONObject[] usageHolder = new JSONObject[]{null};
                final String[] errorHolder = new String[]{null};
                final boolean[] finished = {false};

                ChatClient.chat(store.getStr("base", "https://lumen.unionium.org/api/v1"),
                        store.getStr("key", ""), payload, ui, new ChatClient.Callback() {
                            @Override
                            public void onDelta(String piece) {
                                listener.onDelta(piece);
                            }

                            @Override
                            public void onEnd(String content, JSONArray toolCalls, JSONObject usage) {
                                contentHolder[0] = content;
                                callsHolder[0] = toolCalls;
                                usageHolder[0] = usage;
                                synchronized (lock) {
                                    finished[0] = true;
                                    lock.notifyAll();
                                }
                            }

                            @Override
                            public void onError(String message) {
                                errorHolder[0] = message;
                                synchronized (lock) {
                                    finished[0] = true;
                                    lock.notifyAll();
                                }
                            }
                        });

                synchronized (lock) {
                    long deadline = System.currentTimeMillis() + 320000;
                    while (!finished[0] && System.currentTimeMillis() < deadline) {
                        lock.wait(500);
                    }
                }

                if (errorHolder[0] != null) {
                    final String err = errorHolder[0];
                    ui.post(() -> listener.onError(err));
                    break;
                }

                String content = contentHolder[0] == null ? "" : contentHolder[0];
                JSONArray calls = callsHolder[0] == null ? new JSONArray() : callsHolder[0];
                if (calls.length() == 0 && !nativeTools) calls = parseTextCalls(content);

                if (calls.length() == 0) {
                    String visible = stripToolBlocks(content);
                    Sess.Msg msg = new Sess.Msg("assistant");
                    msg.content = visible.trim().isEmpty() ? "_Модель вернула пустой ответ — попробуй другую модель._" : visible;
                    msg.model = model;
                    if (usageHolder[0] != null) {
                        msg.promptTokens = usageHolder[0].optInt("prompt_tokens", 0);
                        msg.completionTokens = usageHolder[0].optInt("completion_tokens", 0);
                    }
                    if (visible.trim().isEmpty() && steps == 1) continue;
                    synchronized (sess.messages) {
                        sess.messages.add(msg);
                    }
                    sess.updated = System.currentTimeMillis();
                    ui.post(() -> listener.onAssistantDone(msg));
                    break;
                }

                StringBuilder sig = new StringBuilder();
                for (int i = 0; i < calls.length(); i++) {
                    JSONObject c = calls.optJSONObject(i);
                    if (c != null) sig.append(c.optString("name")).append(':').append(c.optString("arguments")).append('|');
                }
                if (signatures.contains(sig.toString())) {
                    Sess.Msg msg = new Sess.Msg("assistant");
                    msg.content = "Повторный вызов инструмента остановлен.";
                    synchronized (sess.messages) {
                        sess.messages.add(msg);
                    }
                    ui.post(() -> listener.onAssistantDone(msg));
                    break;
                }
                signatures.add(sig.toString());

                Sess.Msg stepMsg = new Sess.Msg("assistant");
                stepMsg.content = stripToolBlocks(content);
                stepMsg.model = model;
                if (nativeTools) stepMsg.toolCallsJson = calls.toString();
                final Sess.Msg anchor = new Sess.Msg("tool-anchor");
                synchronized (sess.messages) {
                    sess.messages.add(stepMsg);
                    sess.messages.add(anchor);
                }

                for (int i = 0; i < calls.length(); i++) {
                    if (stopped) break;
                    JSONObject call = calls.optJSONObject(i);
                    if (call == null) continue;
                    String name = call.optString("name", "");
                    JSONObject args = parseArgs(call.optString("arguments", "{}"));

                    Sess.ToolCall tool = new Sess.ToolCall();
                    tool.id = call.optString("id", "call_" + i);
                    tool.name = name;
                    tool.args = args;
                    tool.running = true;
                    tool.collapsed = true;
                    anchor.tools.add(tool);
                    ui.post(() -> listener.onToolCreated(tool));

                    boolean allowed = true;
                    if (name.equals("bash") && store.getBool("confirm", true)
                            && DANGEROUS.matcher(args.optString("command", "")).find()) {
                        allowed = confirmer != null && confirmer.ask("Опасная команда", args.optString("command", ""));
                    }

                    long t0 = System.currentTimeMillis();
                    String output;
                    String engineUsed = "встроенный";
                    boolean ok;
                    if (!allowed) {
                        ok = false;
                        output = "отменено пользователем";
                        engineUsed = "—";
                    } else if (name.equals("bash")) {
                        String cmd = args.optString("command", "");
                        String engine = store.getStr("engine", "builtin");
                        boolean termuxReady = Termux.available(ctx);
                        boolean useTermux = termuxReady && (engine.equals("termux")
                                || (engine.equals("auto") && !BUILTIN_CMD.matcher(cmd).find()));
                        if (useTermux) {
                            JSONObject r = Termux.run(ctx, cmd, store.getStr("home", ""), 60000);
                            ok = r.optBoolean("ok", false);
                            output = r.optString("output", "");
                            engineUsed = "termux";
                        } else {
                            output = shell.run(cmd);
                            ok = !output.startsWith("ошибка:");
                            output = output.replaceFirst("^ошибка:\\s*", "");
                        }
                    } else {
                        String res = shell.toolResult(name, args);
                        ok = !res.startsWith("ошибка:");
                        output = res.replaceFirst("^ошибка:\\s*", "");
                    }
                    tool.running = false;
                    tool.ok = ok;
                    tool.output = output;
                    tool.secs = (System.currentTimeMillis() - t0) / 1000.0;
                    tool.engine = engineUsed;
                    Sess.Msg result = nativeTools ? new Sess.Msg("tool") : new Sess.Msg("user");
                    final Sess.Msg resultMsg = result;
                    result.hidden = true;
                    if (nativeTools) result.toolCallId = tool.id;
                    result.content = nativeTools ? clip(output, 4000) : "[TOOL RESULT: " + name + "]\n" + clip(output, 4000);
                    synchronized (sess.messages) {
                        sess.messages.add(resultMsg);
                    }
                    sess.updated = System.currentTimeMillis();
                    final Sess.ToolCall ft = tool;
                    ui.post(() -> listener.onToolUpdated(ft));
                }
                if (stopped) break;
            }
        } catch (Exception e) {
            final String msg = "ошибка агента: " + e.getMessage();
            ui.post(() -> listener.onError(msg));
        } finally {
            ui.post(() -> {
                listener.onStepDone();
                listener.onBusy(false);
            });
        }
    }

    private JSONArray buildMessages(Sess sess, String model) throws JSONException {
        JSONArray out = new JSONArray();
        JSONObject sys = new JSONObject();
        sys.put("role", "system");
        sys.put("content", systemPrompt(model));
        out.put(sys);

        List<Sess.Msg> visible = new ArrayList<>();
        for (Sess.Msg m : sess.messages) {
            if ("tool-anchor".equals(m.role)) continue;
            visible.add(m);
        }
        for (Sess.Msg m : visible) {
            if (m.toolCallsJson != null && toolCallsPayload(m.toolCallsJson).length() == 0) m.toolCallsJson = null;
        }
        int keep = Math.min(visible.size(), 24);
        int mediaCtx = MODEL_CTX[modelIndex(model)];
        int size = 0;
        for (Sess.Msg m : visible) size += m.content.length();
        if (keep < visible.size() && size > mediaCtx * 2) {
            JSONObject note = new JSONObject();
            note.put("role", "system");
            note.put("content", "Часть старых сообщений скрыта для экономии контекста.");
            out.put(note);
        }
        for (int i = visible.size() - keep; i < visible.size(); i++) {
            Sess.Msg m = visible.get(i);
            JSONObject o = new JSONObject();
            if ("tool".equals(m.role)) {
                o.put("role", "tool");
                o.put("tool_call_id", m.toolCallId == null ? "call" : m.toolCallId);
                o.put("content", m.content == null ? "" : m.content);
                out.put(o);
                continue;
            }
            if (m.content == null || m.content.isEmpty() && m.toolCallsJson == null) continue;
            o.put("role", "user".equals(m.role) ? "user" : "assistant");
            o.put("content", m.content == null ? "" : m.content);
            if (m.toolCallsJson != null) {
                o.put("tool_calls", new JSONArray(m.toolCallsJson));
            }
            out.put(o);
        }
        return out;
    }

    private String systemPrompt(String model) {
        StringBuilder p = new StringBuilder();
        p.append("Ты Lumen Agent — ассистент-программист внутри Android-приложения (создатель t.me/frizovdanya).\n")
                .append("Рабочая папка: ").append(shell.getCwd().getAbsolutePath()).append("\n")
                .append("Модель: Lumen ").append(MODEL_LABELS[modelIndex(model)]).append("\n\n")
                .append("Правила:\n")
                .append("- отвечай по-русски, кратко и по делу;\n")
                .append("- пользуйся инструментами, а не догадками: чтобы узнать содержимое папки — вызови ls, чтобы создать файл — write_file;\n")
                .append("- перед правкой файла прочитай его (read_file), правь через edit_file;\n")
                .append("- не выдумывай вывод команд: сначала вызови инструмент и дождись результата;\n")
                .append("- код оформляй в ```блоках.");
        if (MODEL_NATIVE_TOOLS[modelIndex(model)]) {
            p.append("\nИнструменты доступны через function calling — вызывай их, когда нужно.");
        } else {
            p.append("\n\nИНСТРУМЕНТЫ (текстовый протокол):\n")
                    .append("- bash(command) — shell-команда в терминале пользователя\n")
                    .append("- read_file(path, start_line=1, end_line=0)\n")
                    .append("- write_file(path, content)\n")
                    .append("- edit_file(path, old_string, new_string)\n")
                    .append("- ls(path=\".\")\n\n")
                    .append("Чтобы вызвать инструмент, выведи ТОЛЬКО такой блок:\n\n")
                    .append("```tool\n{\"name\": \"bash\", \"arguments\": {\"command\": \"ls -la\"}}\n```\n\n")
                    .append("После блока остановись и жди сообщение [TOOL RESULT]. Один вызов за раз.");
        }
        return p.toString();
    }

    private JSONArray nativeToolsSchema() throws JSONException {
        JSONArray arr = new JSONArray();
        arr.put(fn("bash", "Выполнить shell-команду на устройстве пользователя",
                props(new String[]{"command"}, new String[]{"shell-команда"}), new String[]{"command"}));
        arr.put(fn("read_file", "Прочитать файл с номерами строк",
                props(new String[]{"path", "start_line", "end_line"}, new String[]{"путь", "с какой строки", "по какую строку"}),
                new String[]{"path"}));
        arr.put(fn("write_file", "Создать или перезаписать файл целиком",
                props(new String[]{"path", "content"}, new String[]{"путь", "содержимое"}), new String[]{"path", "content"}));
        arr.put(fn("edit_file", "Заменить точный фрагмент old_string на new_string",
                props(new String[]{"path", "old_string", "new_string"}, new String[]{"путь", "что заменить", "на что заменить"}),
                new String[]{"path", "old_string", "new_string"}));
        arr.put(fn("ls", "Список файлов и папок", props(new String[]{"path"}, new String[]{"папка"}), new String[]{}));
        return arr;
    }

    private JSONObject fn(String name, String desc, JSONObject properties, String[] required) throws JSONException {
        JSONObject f = new JSONObject();
        f.put("name", name);
        f.put("description", desc);
        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties);
        JSONArray req = new JSONArray();
        for (String r : required) req.put(r);
        params.put("required", req);
        f.put("parameters", params);
        JSONObject wrapper = new JSONObject();
        wrapper.put("type", "function");
        wrapper.put("function", f);
        return wrapper;
    }

    private JSONObject props(String[] keys, String[] descs) throws JSONException {
        JSONObject o = new JSONObject();
        for (int i = 0; i < keys.length; i++) {
            JSONObject prop = new JSONObject();
            prop.put("type", keys[i].startsWith("start") || keys[i].startsWith("end") ? "integer" : "string");
            prop.put("description", descs[i]);
            o.put(keys[i], prop);
        }
        return o;
    }

    private JSONObject parseArgs(String raw) {
        try {
            return new JSONObject(raw == null || raw.isEmpty() ? "{}" : raw);
        } catch (Exception e) {
            try {
                return new JSONObject().put("command", raw);
            } catch (Exception e2) {
                return new JSONObject();
            }
        }
    }

    private static JSONArray toolCallsPayload(String json) {
        try {
            return new JSONArray(json);
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONArray parseTextCalls(String content) {
        JSONArray out = new JSONArray();
        if (content == null) return out;
        List<String> raws = new ArrayList<>();
        Matcher m = TOOL_FENCE.matcher(content);
        while (m.find()) raws.add(m.group(1));
        if (raws.isEmpty()) {
            Matcher m2 = TOOL_LINE.matcher(content);
            while (m2.find()) raws.add(m2.group(1));
        }
        for (String raw : raws) {
            try {
                JSONObject o = new JSONObject(raw);
                String name = o.optString("name", o.optString("tool", ""));
                if (name.isEmpty()) continue;
                JSONObject args = o.optJSONObject("arguments");
                if (args == null) args = o.optJSONObject("args");
                if (args == null) {
                    String cmd = o.optString("arguments", "");
                    args = new JSONObject();
                    if (!cmd.isEmpty()) args.put("command", cmd);
                }
                JSONObject call = new JSONObject();
                call.put("name", name);
                call.put("arguments", args.toString());
                out.put(call);
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    public static String stripToolBlocks(String content) {
        if (content == null) return "";
        return TOOL_FENCE.matcher(content).replaceAll("").trim();
    }

    private static String clip(String text, int limit) {
        if (text == null) return "";
        return text.length() <= limit ? text : text.substring(0, limit) + "\n… [обрезано]";
    }
}
