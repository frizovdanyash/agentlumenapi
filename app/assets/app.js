(function () {
  "use strict";

  var APP_VERSION = "1.1.0";
  var AUTHOR = "t.me/frizovdanya";
  var HAS_NATIVE = (typeof Native !== "undefined");

  var $ = function (s) { return document.querySelector(s); };
  var $$ = function (s) { return Array.prototype.slice.call(document.querySelectorAll(s)); };

  function esc(s) {
    return String(s == null ? "" : s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  var store = {
    get: function (k, d) {
      try { var v = localStorage.getItem(k); return v === null ? d : v; } catch (e) { return d; }
    },
    set: function (k, v) { try { localStorage.setItem(k, v); } catch (e) {} },
    del: function (k) { try { localStorage.removeItem(k); } catch (e) {} }
  };

  var MODELS = {
    mini: { label: "Mini", ctx: 32000, tools: "text", desc: "самая дешёвая и быстрая — черновики и простые задачи" },
    fast: { label: "Fast", ctx: 131072, tools: "native", desc: "быстрая и умная — оптимальный выбор по умолчанию" },
    max: { label: "Max", ctx: 200000, tools: "native", desc: "сильная модель — код и сложные задачи" },
    ultra: { label: "Ultra", ctx: 200000, tools: "native", desc: "топовая — максимум качества, дороже" },
    bare: { label: "Bare", ctx: 131072, tools: "native", desc: "без цензуры и отказов — ответственность на вас" }
  };
  var MODEL_KEYS = ["mini", "fast", "max", "ultra", "bare"];

  var ACCENTS = [
    { name: "Тёплая", a: "#ff8a3d", a2: "#ff6a00" },
    { name: "Закат", a: "#ff6b6b", a2: "#ff9e3d" },
    { name: "Океан", a: "#4aa8ff", a2: "#1f6fe0" },
    { name: "Изумруд", a: "#3ddc97", a2: "#12a06a" },
    { name: "Лаванда", a: "#9b8aee", a2: "#6f5ce0" },
    { name: "Роза", a: "#ff7ab6", a2: "#e0428a" },
    { name: "Кибер", a: "#39e6c3", a2: "#0f8f93" },
    { name: "Графит", a: "#a7b0ba", a2: "#66707b" }
  ];

  var WALLS = [
    { name: "Тёплая", css: "radial-gradient(1200px 520px at 50% -12%, #2a1a0e 0%, transparent 62%),linear-gradient(180deg,#12100e,#0b0a09)", light: false },
    { name: "Полночь", css: "radial-gradient(1200px 520px at 50% -12%, #101a2e 0%, transparent 62%),linear-gradient(180deg,#0b0e14,#070809)", light: false },
    { name: "Изумруд", css: "radial-gradient(1200px 520px at 50% -12%, #0c2320 0%, transparent 62%),linear-gradient(180deg,#0a1211,#070a09)", light: false },
    { name: "Роза", css: "radial-gradient(1200px 520px at 50% -12%, #2a1020 0%, transparent 62%),linear-gradient(180deg,#140d12,#0a0608)", light: false },
    { name: "Графит", css: "radial-gradient(1200px 520px at 50% -12%, #1c1f24 0%, transparent 62%),linear-gradient(180deg,#101214,#0a0b0c)", light: false },
    { name: "Светлая", css: "radial-gradient(1200px 520px at 50% -12%, #ffe6cf 0%, transparent 62%),linear-gradient(180deg,#faf7f2,#efe9e0)", light: true }
  ];

  var DANGEROUS = /(\brm\s+-[a-z]*r[a-z]*f?\s+(\/|\*|~|\$HOME)|\bsudo\b|\bmkfs\b|\bdd\s+if=|\bchmod\s+-R\s+777\s+\/|\bshutdown\b|\breboot\b)/;
  var BUILTIN_CMDS = /^\s*(pwd|ls|cd|cat|echo|mkdir|touch|rm|mv|cp|head|tail|wc|grep|find|date|whoami|uname|du|help|clear)\b/;

  var TOOLS_NATIVE = [
    { type: "function", function: { name: "bash", description: "Выполнить shell-команду на устройстве пользователя", parameters: { type: "object", properties: { command: { type: "string", description: "shell-команда" }, timeout: { type: "integer" } }, required: ["command"] } } },
    { type: "function", function: { name: "read_file", description: "Прочитать файл с номерами строк", parameters: { type: "object", properties: { path: { type: "string" }, start_line: { type: "integer" }, end_line: { type: "integer" } }, required: ["path"] } } },
    { type: "function", function: { name: "write_file", description: "Создать или перезаписать файл целиком", parameters: { type: "object", properties: { path: { type: "string" }, content: { type: "string" } }, required: ["path", "content"] } } },
    { type: "function", function: { name: "edit_file", description: "Заменить точный фрагмент old_string на new_string", parameters: { type: "object", properties: { path: { type: "string" }, old_string: { type: "string" }, new_string: { type: "string" } }, required: ["path", "old_string", "new_string"] } } },
    { type: "function", function: { name: "ls", description: "Список файлов и папок", parameters: { type: "object", properties: { path: { type: "string" } }, required: [] } } }
  ];

  var state = {
    apiKey: store.get("lumen.key", ""),
    baseUrl: store.get("lumen.base", "https://lumen.unionium.org/api/v1"),
    model: store.get("lumen.model", "mini"),
    engine: store.get("lumen.engine", "builtin"),
    custom: {
      accent: parseInt(store.get("lumen.c.accent", "0"), 10),
      wall: parseInt(store.get("lumen.c.wall", "0"), 10),
      fontSize: parseInt(store.get("lumen.c.fs", "15"), 10),
      radius: store.get("lumen.c.radius", "default"),
      density: store.get("lumen.c.density", "default"),
      avatars: store.get("lumen.c.avatars", "on"),
      theme: store.get("lumen.c.theme", "auto")
    },
    showTools: store.get("lumen.showTools", "1") === "1",
    confirm: store.get("lumen.confirm", "1") === "1",
    autoCompact: store.get("lumen.autocompact", "1") === "1",
    sessions: [],
    current: null,
    streaming: false,
    native: { termux: false, home: "(песочница)", sandbox: "", device: "" },
    pending: {},
    termHist: [],
    termIdx: -1
  };
  var lastAssistantNode = null;

  function fmtTokens(n) { return n >= 1000 ? (n / 1000).toFixed(1) + "k" : String(n || 0); }
  function fmtDur(s) { return s < 1 ? (s * 1000).toFixed(0) + " мс" : s.toFixed(2) + " с"; }
  function fmtTime(ts) { return new Date(ts).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }); }

  function toast(msg) {
    var t = $("#toast");
    t.textContent = msg;
    t.classList.add("on");
    clearTimeout(t._h);
    t._h = setTimeout(function () { t.classList.remove("on"); }, 2100);
  }

  function mdToHtml(src) {
    var blocks = [];
    var text = String(src || "").replace(/```(\w*)\n?([\s\S]*?)(?:```|$)/g, function (m, lang, code) {
      blocks.push(code);
      return "\u0000B" + (blocks.length - 1) + "\u0000";
    });
    text = esc(text);
    text = text.replace(/`([^`\n]+)`/g, "<code>$1</code>");
    text = text.replace(/\*\*([^*]+)\*\*/g, "<b>$1</b>");
    text = text.replace(/(^|\s)\*([^*\n]+)\*/g, "$1<i>$2</i>");
    text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$1" data-url="$2">$1</a>');
    var lines = text.split("\n");
    var html = "", list = null, para = [], table = null;
    function flushPara() { if (para.length) { html += "<p>" + para.join("<br>") + "</p>"; para = []; } }
    function closeList() { if (list) { html += "</" + list + ">"; list = null; } }
    function closeTable() {
      if (table) {
        html += "</tbody></table>";
        table = null;
      }
    }
    for (var i = 0; i < lines.length; i++) {
      var line = lines[i], t = line.trim(), m;
      if (!t) { flushPara(); closeList(); closeTable(); continue; }
      if (/^\u0000B\d+\u0000$/.test(t)) {
        flushPara(); closeList(); closeTable();
        html += "\u0000B" + t.replace(/\u0000B(\d+)\u0000/, "$1") + "\u0000";
        continue;
      }
      if (t.indexOf("|") === 0 && t.lastIndexOf("|") > 0) {
        flushPara(); closeList();
        var cells = t.replace(/^\||\|$/g, "").split("|").map(function (c) { return c.trim(); });
        if (/^[-:\s|]+$/.test(t)) continue;
        if (!table) { html += "<table><tbody>"; table = true; }
        html += "<tr>" + cells.map(function (c) { return "<td>" + c + "</td>"; }).join("") + "</tr>";
        continue;
      }
      if ((m = t.match(/^#{1,4}\s+(.*)$/))) { flushPara(); closeList(); closeTable(); html += "<h3>" + m[1] + "</h3>"; continue; }
      if ((m = t.match(/^[-*+•]\s+(.*)$/))) { flushPara(); closeTable(); if (list !== "ul") { closeList(); html += "<ul>"; list = "ul"; } html += "<li>" + m[1] + "</li>"; continue; }
      if ((m = t.match(/^\d+[.)]\s+(.*)$/))) { flushPara(); closeTable(); if (list !== "ol") { closeList(); html += "<ol>"; list = "ol"; } html += "<li>" + m[1] + "</li>"; continue; }
      if ((m = t.match(/^&gt;\s?(.*)$/))) { flushPara(); closeList(); closeTable(); html += "<blockquote>" + m[1] + "</blockquote>"; continue; }
      para.push(line);
    }
    flushPara(); closeList(); closeTable();
    return html.replace(/\u0000B(\d+)\u0000/g, function (m, idx) {
      return "<pre><code>" + esc(blocks[+idx].replace(/\n$/, "")) + "</code></pre>";
    });
  }

  function loadSessions() {
    try { state.sessions = JSON.parse(store.get("lumen.sessions", "[]")); } catch (e) { state.sessions = []; }
  }
  function saveSessions() { store.set("lumen.sessions", JSON.stringify(state.sessions.slice(-40))); }
  function session() {
    if (!state.current) newSession();
    return state.current;
  }
  function newSession() {
    var s = { id: "s" + Date.now(), created: Date.now(), updated: Date.now(), model: state.model, summary: "", messages: [] };
    state.sessions.push(s);
    state.current = s;
    saveSessions();
    renderChat();
    updateSubtitle();
    LumenPlugins.emit("session", { id: s.id, new: true });
    return s;
  }
  function sessionTitle(s) {
    for (var i = 0; i < s.messages.length; i++) {
      if (s.messages[i].role === "user" && !s.messages[i].hidden) {
        var t = s.messages[i].content.replace(/\s+/g, " ").trim();
        return t.length > 44 ? t.slice(0, 44) + "…" : (t || "Диалог");
      }
    }
    return "Новый диалог";
  }
  function touch() { var s = session(); s.updated = Date.now(); s.model = state.model; saveSessions(); }
  function updateSubtitle() {
    var s = session();
    $("#subTitle").textContent = state.streaming ? "работаю…" : (s.messages.length ? sessionTitle(s) : "готов к работе");
  }

  function renderChat() {
    var view = $("#chatView");
    var s = session();
    var visible = s.messages.filter(function (m) { return !m.hidden; });
    if (!visible.length) {
      view.innerHTML =
        '<div class="hero"><div class="big">✦</div>' +
        "<h2>Чем займёмся?</h2>" +
        "<p>Пишу код, работаю с файлами и запускаю команды в терминале.</p>" +
        '<div class="sugg">' +
        '<button data-q="Покажи, что лежит в рабочей папке">Показать файлы</button>' +
        '<button data-q="Создай файл hello.py с приветствием и запусти его">Создай и запусти скрипт</button>' +
        '<button data-q="Что ты умеешь?">Что ты умеешь?</button>' +
        '<button data-slash="/plugins">Плагины</button>' +
        "</div>" +
        '<div class="author">создатель — <a href="#" data-url="https://' + AUTHOR + '">' + AUTHOR + "</a></div></div>";
      view.querySelectorAll(".sugg button").forEach(function (b) {
        b.onclick = function () {
          if (b.dataset.slash) { setInput(b.dataset.slash); return; }
          setInput(b.dataset.q);
          send();
        };
      });
      return;
    }
    view.innerHTML = "";
    for (var i = 0; i < s.messages.length; i++) {
      if (!s.messages[i].hidden) view.appendChild(renderMessage(s.messages[i]));
    }
    scrollBottom(true);
  }

  function renderMessage(m) {
    var wrap = document.createElement("div");
    wrap.className = "msg " + m.role;
    if (m.role === "user") {
      wrap.innerHTML = '<div class="bubble">' + esc(m.content) + '<div class="meta">' + fmtTime(m.ts || Date.now()) + "</div></div>";
      return wrap;
    }
    var av = document.createElement("div");
    av.className = "avatar";
    av.textContent = "✦";
    var bubble = document.createElement("div");
    bubble.className = "bubble";
    var md = document.createElement("div");
    md.className = "md";
    md.innerHTML = mdToHtml(m.content || "");
    bubble.appendChild(md);
    if (state.showTools && m.tools && m.tools.length) {
      for (var i = 0; i < m.tools.length; i++) bubble.appendChild(renderToolCard(m.tools[i]));
    }
    var meta = document.createElement("div");
    meta.className = "meta";
    var parts = [(MODELS[m.model || state.model] || { label: state.model }).label];
    if (m.usage) parts.push("↓" + fmtTokens(m.usage.prompt_tokens) + " ↑" + fmtTokens(m.usage.completion_tokens));
    if (m.ms) parts.push((m.ms / 1000).toFixed(1) + " с");
    meta.textContent = parts.join(" · ");
    bubble.appendChild(meta);
    wrap.appendChild(av);
    wrap.appendChild(bubble);
    return wrap;
  }

  function renderToolCard(t) {
    var el = document.createElement("div");
    el.className = "tool";
    var detail = "";
    if (t.name === "bash") detail = t.args.command || "";
    else if (t.name === "write_file") detail = (t.args.path || "") + " (" + String(t.args.content || "").length + " симв.)";
    else detail = t.args.path || t.args.old_string || t.plugin || "";
    var status = t.running ? '<span class="pill run">выполняется…</span>'
      : (t.ok ? '<span class="pill ok">✔ ' + esc(fmtDur(t.secs || 0)) + "</span>" : '<span class="pill err">✖ ошибка</span>');
    el.innerHTML = '<div class="thead"><span style="color:var(--a)">⏺</span><span class="tname">' + esc(t.name) + "</span>" +
      '<span class="tcmd">' + esc(detail) + "</span>" + status + "</div>" +
      '<pre class="' + (t.collapsed ? "hidden" : "") + '">' + esc(t.output || "") + "</pre>" +
      (String(t.output || "").length > 400 ? '<div class="tfoot">тап — свернуть или развернуть вывод</div>' : "");
    var pre = el.querySelector("pre");
    el.onclick = function () { if (pre.textContent.length > 200) pre.classList.toggle("hidden"); };
    return el;
  }

  function scrollBottom(force) {
    var v = $("#chatView").parentElement;
    var near = v.scrollHeight - v.scrollTop - v.clientHeight < 240;
    if (force || near) v.scrollTop = v.scrollHeight;
  }

  function setInput(v) { var i = $("#input"); i.value = v; i.focus(); autoGrow(); }
  function autoGrow() { var i = $("#input"); i.style.height = "auto"; i.style.height = Math.min(i.scrollHeight, 132) + "px"; }
  function setStreaming(on) {
    state.streaming = on;
    $("#sendBtn").classList.toggle("stop", on);
    $("#sendIcon").innerHTML = on ? '<rect x="7" y="7" width="10" height="10" rx="2"></rect>' : '<path d="M4 12l16-8-6 8 6 8-16-8z"/>';
    updateSubtitle();
  }

  function nativeEnv() {
    if (!HAS_NATIVE) return;
    try {
      var e = JSON.parse(Native.env());
      state.native.termux = !!e.termux;
      state.native.home = e.home || state.native.home;
      state.native.sandbox = e.sandbox || "";
      state.native.device = e.device || "";
      $("#stAbout").textContent = "Lumen Agent " + (e.version || APP_VERSION) + " · Android " + (e.sdk || "?") + " · " + (e.device || "");
      $("#termTermux").innerHTML = e.termux ? '<b style="color:var(--ok)">Termux найден</b>' : '<span style="color:var(--muted-2)">Termux не установлен</span>';
    } catch (err) {}
  }

  function requestChat(payload, cbs) {
    var reqId = "r" + Date.now() + Math.random().toString(36).slice(2, 6);
    state.pending[reqId] = cbs;
    var body = Object.assign({}, payload, { apiKey: state.apiKey, baseUrl: state.baseUrl });
    if (HAS_NATIVE) Native.chat(reqId, JSON.stringify(body), true);
    else demoResponse(reqId, cbs.payload || payload, cbs);
  }

  window.__lumen = {
    onDelta: function (reqId, text) { var p = state.pending[reqId]; if (p && p.onDelta) p.onDelta(text); },
    onEnd: function (reqId, json) {
      var p = state.pending[reqId];
      if (!p) return;
      delete state.pending[reqId];
      var data = {};
      try { data = JSON.parse(json); } catch (e) {}
      p.onEnd(data);
    },
    onError: function (reqId, msg) {
      var p = state.pending[reqId];
      if (!p) return;
      delete state.pending[reqId];
      if (p.onError) p.onError(String(msg || "неизвестная ошибка"));
    },
    onToast: toast,
    onBack: function () {
      if ($("#onboard") && !$("#onboard").classList.contains("off")) return false;
      if ($$(".sheet.on").length) { closeSheets(); return true; }
      if ($$(".backdrop.on").length) { $$(".backdrop.on").forEach(function (b) { if (!b.closest(".sheet")) b.classList.remove("on"); }); return true; }
      return false;
    }
  };

  function demoResponse(reqId, payload, cbs) {
    var last = (payload.messages || []).slice(-1)[0] || {};
    var text = String(last.content || "");
    var out;
    if (/ls|файл|папк/i.test(text)) out = 'Смотрю, что лежит в рабочей папке.\n\n```tool\n{"name": "ls", "arguments": {"path": "."}}\n```';
    else if (/fastfetch/i.test(text)) out = "В Termux это ставится так:\n\n```bash\npkg update && pkg install fastfetch\nfastfetch\n```";
    else out = "Это **предпросмотр интерфейса**. В APK я:\n\n- работаю с моделями Lumen (`mini`, `fast`, `max`, `ultra`, `bare`);\n- читаю и пишу файлы, запускаю команды в терминале;\n- поддерживаю **плагины** — загляни в «Настройки → Плагины».\n\nСоздатель: [t.me/frizovdanya](https://t.me/frizovdanya)";
    var i = 0;
    (function step() {
      if (i < out.length) {
        cbs.onDelta(out.slice(i, i + 3));
        i += 3;
        setTimeout(step, 12);
      } else {
        var call = out.match(/```tool\s*(\{[\s\S]*?\})\s*```/);
        var calls = [];
        if (call) { try { var o = JSON.parse(call[1]); calls.push({ name: o.name, arguments: JSON.stringify(o.arguments || {}) }); } catch (e) {} }
        cbs.onEnd({ content: out, toolCalls: calls, usage: { prompt_tokens: 380, completion_tokens: 90 } });
      }
    })();
  }

  function parseTextToolCalls(text) {
    var calls = [], rx = /```(?:tool|json)\s*(\{[\s\S]*?\})\s*```/g, m;
    while ((m = rx.exec(text))) {
      try {
        var o = JSON.parse(m[1]);
        var name = o.name || o.tool;
        var args = o.arguments || o.args || {};
        if (typeof args === "string") { try { args = JSON.parse(args); } catch (e) { args = { command: args }; } }
        if (name) calls.push({ name: name, arguments: JSON.stringify(args) });
      } catch (e) {}
    }
    return calls;
  }
  function stripToolBlocks(text) { return String(text || "").replace(/```(?:tool|json)\s*\{[\s\S]*?\}\s*```/g, "").trim(); }
  function filterStream(text) {
    var out = "", i = 0, inFence = false;
    while (i < text.length) {
      if (!inFence && text.indexOf("```tool", i) === i) { inFence = true; i += 7; continue; }
      if (inFence) {
        var end = text.indexOf("```", i);
        if (end === -1) break;
        inFence = false;
        i = end + 3;
        continue;
      }
      out += text[i];
      i++;
    }
    return out.replace(/\n{3,}/g, "\n\n");
  }

  function toolSpecText() {
    var lines = [
      "- bash(command) — shell-команда",
      "- read_file(path, start_line=1, end_line=0)",
      "- write_file(path, content)",
      "- edit_file(path, old_string, new_string)",
      '- ls(path=".")'
    ];
    LumenPlugins.toolDefs().forEach(function (t) {
      var params = Object.keys((t.parameters && t.parameters.properties) || {}).join(", ");
      lines.push("- " + t.name + "(" + params + ") — " + (t.description || "плагин"));
    });
    return lines.join("\n");
  }

  function systemPrompt() {
    var m = MODELS[state.model] || MODELS.mini;
    var p = "Ты Lumen Agent — ассистент-программист внутри Android-приложения. Рабочая папка: " + (state.native.sandbox || state.native.home) +
      ".\nСегодня: " + new Date().toLocaleString("ru-RU") + ". Модель: Lumen " + m.label + ".\n\n" +
      "Правила:\n- отвечай по-русски, кратко и по делу;\n- пользуйся инструментами, а не догадками;\n" +
      "- перед правкой файла прочитай его (read_file), правь через edit_file;\n" +
      "- не выдумывай вывод команд: сначала вызови инструмент и дождись результата;\n" +
      "- код оформляй в ```блоках.";
    var extra = LumenPlugins.promptExtra();
    if (extra) p += "\n\n" + extra;
    if (m.tools === "native") {
      p += "\nИнструменты доступны через function calling.";
    } else {
      p += "\n\nИНСТРУМЕНТЫ (текстовый протокол):\n" + toolSpecText() +
        "\n\nЧтобы вызвать инструмент, выведи ТОЛЬКО такой блок:\n\n```tool\n{\"name\": \"bash\", \"arguments\": {\"command\": \"ls -la\"}}\n```\n\n" +
        "После блока остановись и жди [TOOL RESULT]. Один вызов за раз.";
    }
    return p;
  }

  function buildMessages() {
    var s = session();
    var out = [{ role: "system", content: systemPrompt() }];
    if (s.summary) out.push({ role: "system", content: "Краткая история ранее:\n" + s.summary });
    var msgs = s.messages.filter(function (m) { return m.role === "user" || m.role === "assistant"; });
    var keep = Math.min(msgs.length, 30);
    var size = msgs.reduce(function (a, m) { return a + String(m.content || "").length; }, 0);
    var ctx = (MODELS[state.model] || MODELS.mini).ctx;
    if (state.autoCompact && size > ctx * 2.2 && keep < msgs.length) {
      out.push({ role: "system", content: "(часть старых сообщений скрыта для экономии контекста)" });
    }
    for (var i = msgs.length - keep; i < msgs.length; i++) out.push({ role: msgs[i].role, content: msgs[i].content || "" });
    return out;
  }

  function execTool(name, args) {
    var t0 = performance.now();
    var pluginTool = LumenPlugins.toolDefs().filter(function (t) { return t.name === name; })[0];
    if (pluginTool) {
      var pr = LumenPlugins.runTool(name, args);
      return Promise.resolve({ ok: pr.ok, output: pr.output, secs: (performance.now() - t0) / 1000, engine: "плагин" });
    }
    if (!HAS_NATIVE) {
      return new Promise(function (res) {
        setTimeout(function () {
          if (name === "bash") res({ ok: true, output: "(демо) команда «" + (args.command || "") + "» выполнена", secs: (performance.now() - t0) / 1000, engine: "демо" });
          else if (name === "ls") res({ ok: true, output: "(демо) workspace:\n📄 hello.py  (128 б)\n📁 data/", secs: (performance.now() - t0) / 1000, engine: "демо" });
          else res({ ok: true, output: "(демо) " + name, secs: (performance.now() - t0) / 1000, engine: "демо" });
        }, 220);
      });
    }
    var useTermux = name === "bash" && state.native.termux &&
      (state.engine === "termux" || (state.engine === "auto" && !BUILTIN_CMDS.test(args.command || "")));
    if (useTermux) {
      var tr = JSON.parse(Native.termuxRun(args.command || "", state.native.home, 60000));
      return Promise.resolve({ ok: !!tr.ok, output: tr.output || "", secs: (performance.now() - t0) / 1000, engine: "termux" });
    }
    var r = JSON.parse(Native.tool(name, JSON.stringify(args || {})));
    var outText = r.output || "";
    if (!r.ok) outText = outText.replace(/^ошибка:\s*/, "");
    return Promise.resolve({ ok: !!r.ok, output: outText, secs: (performance.now() - t0) / 1000, engine: "встроенный" });
  }

  function addToolResult(name, output, nativeTools) {
    var s = session();
    var clipped = output.length > 4000 ? output.slice(0, 4000) + "\n… [обрезано]" : output;
    s.messages.push({ role: "user", content: "[TOOL RESULT: " + name + "]\n" + clipped, hidden: true });
    touch();
    LumenPlugins.emit("tool", { name: name, output: clipped, native: nativeTools });
  }

  function runSlashes(text) {
    var parts = text.split(/\s+/);
    var cmd = parts[0].slice(1);
    var arg = text.slice(parts[0].length).trim();
    var res = LumenPlugins.runCommand(cmd, arg);
    if (!res.ok) return false;
    var s = session();
    s.messages.push({ role: "assistant", content: "`/" + cmd + "`\n\n" + (res.output || "_готово_") + "", ts: Date.now() });
    touch();
    renderChat();
    return true;
  }

  function sendText(text) { setInput(text); send(); }

  function send() {
    var input = $("#input");
    var text = input.value.trim();
    if (!text || state.streaming) return;
    if (text.charAt(0) === "/" && runSlashes(text)) {
      input.value = "";
      autoGrow();
      return;
    }
    if (!state.apiKey) {
      toast("Вставь API-ключ");
      openSettings();
      return;
    }
    input.value = "";
    autoGrow();
    var s = session();
    s.messages.push({ role: "user", content: text, ts: Date.now() });
    touch();
    renderChat();
    LumenPlugins.emit("user", { text: text });

    var msg = { role: "assistant", content: "", tools: [], model: state.model, ts: Date.now() };
    s.messages.push(msg);
    renderChat();
    setStreaming(true);
    lastAssistantNode = null;

    var t0 = performance.now();
    var steps = 0;
    var seen = {};

    function finish() {
      msg.content = stripToolBlocks(msg.content);
      msg.ms = performance.now() - t0;
      touch();
      renderChat();
      setStreaming(false);
      saveSessions();
      lastAssistantNode = null;
      if (msg.content) LumenPlugins.emit("assistant", { text: msg.content, message: msg });
    }

    function step() {
      steps++;
      if (steps > 12) { toast("Слишком много шагов инструментов"); finish(); return; }
      var nativeTools = (MODELS[state.model] || MODELS.mini).tools === "native";
      var payload = { model: state.model, messages: buildMessages(), temperature: 0.3, max_tokens: 4096 };
      if (nativeTools) {
        payload.tools = TOOLS_NATIVE.concat(LumenPlugins.toolDefs().map(function (t) {
          return { type: "function", function: { name: t.name, description: t.description || "инструмент плагина", parameters: t.parameters || { type: "object", properties: {} } } };
        }));
        payload.tool_choice = "auto";
      }
      var buf = "";
      requestChat(Object.assign(payload, { payload: payload }), {
        onDelta: function (chunk) {
          buf += chunk;
          msg.content = nativeTools ? buf : filterStream(buf);
          var node = findLastAssistant();
          if (node) {
            var md = node.querySelector(".md");
            md.innerHTML = mdToHtml(msg.content) + (state.streaming ? '<span class="caret"></span>' : "");
          }
          scrollBottom(false);
        },
        onEnd: function (data) {
          msg.usage = data.usage || msg.usage;
          msg.content = stripToolBlocks(data.content || buf);
          var calls = data.toolCalls || [];
          if (!calls.length && !nativeTools) calls = parseTextToolCalls(data.content || buf);
          if (!calls.length) {
            if (!msg.content.trim()) {
              if (steps === 1) { step(); return; }
              msg.content = "_Модель вернула пустой ответ — обычно это перегрузка. Попробуй другую модель._";
            }
            finish();
            return;
          }
          var sig = calls.map(function (c) { return c.name + ":" + c.arguments; }).join("|");
          if (seen[sig]) { msg.content += "\n\n_Повторный вызов остановлен._"; finish(); return; }
          seen[sig] = 1;
          renderChat();
          var chain = Promise.resolve();
          calls.forEach(function (c) {
            chain = chain.then(function () {
              var args = {};
              try { args = JSON.parse(c.arguments || "{}"); } catch (e) {}
              var card = { name: c.name, args: args, running: true, output: "" };
              msg.tools.push(card);
              renderChat();
              var guard = state.confirm && c.name === "bash" && DANGEROUS.test(args.command || "");
              return (guard ? askConfirm("Опасная команда:\n\n" + (args.command || "")) : Promise.resolve(true)).then(function (allowed) {
                if (!allowed) {
                  card.running = false; card.ok = false; card.output = "отменено пользователем"; card.secs = 0;
                  addToolResult(c.name, "пользователь запретил выполнение", nativeTools);
                  renderChat();
                  return;
                }
                return execTool(c.name, args).then(function (r) {
                  card.running = false; card.ok = r.ok; card.output = r.output; card.secs = r.secs; card.engine = r.engine;
                  addToolResult(c.name, r.output, nativeTools);
                  renderChat();
                });
              });
            });
          });
          chain.then(step);
        },
        onError: function (text) {
          msg.content += (msg.content ? "\n\n" : "") + "**Ошибка:** " + text;
          if (/перегруж|overload|429|50\d/i.test(text)) msg.content += "\n\n_Попробуй другую модель — чип сверху._";
          finish();
        }
      });
    }
    step();
  }

  function findLastAssistant() {
    var nodes = $$("#chatView .msg.assistant");
    if (nodes.length) { lastAssistantNode = nodes[nodes.length - 1]; return lastAssistantNode; }
    return lastAssistantNode;
  }

  function askConfirm(text) {
    return new Promise(function (resolve) {
      var back = document.createElement("div");
      back.className = "backdrop on";
      back.style.zIndex = 71;
      back.innerHTML = '<div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(92vw,420px);' +
        'background:var(--bg-2);border:1px solid var(--line-2);border-radius:18px;padding:18px">' +
        '<div style="font-weight:700;margin-bottom:8px">Подтверждение</div>' +
        '<pre class="code" style="max-height:190px">' + esc(text) + "</pre>" +
        '<div style="display:flex;gap:8px;margin-top:12px">' +
        '<button class="btn" style="flex:1" data-no>Отмена</button>' +
        '<button class="btn primary" style="flex:1" data-yes>Выполнить</button></div></div>';
      document.body.appendChild(back);
      back.querySelector("[data-yes]").onclick = function () { back.remove(); resolve(true); };
      back.querySelector("[data-no]").onclick = function () { back.remove(); resolve(false); };
    });
  }

  function openSheet(sel) {
    closeSheets();
    $(sel).classList.add("on");
    $("#backdrop").classList.add("on");
  }
  function closeSheets() {
    $$(".sheet").forEach(function (s) { s.classList.remove("on"); });
    $("#backdrop").classList.remove("on");
  }

  function renderModels() {
    var list = $("#modelList");
    list.innerHTML = "";
    MODEL_KEYS.forEach(function (k) {
      var m = MODELS[k];
      var el = document.createElement("div");
      el.className = "modelcard" + (k === state.model ? " on" : "");
      el.innerHTML = '<div class="lbl"><b>' + m.label + ' <span class="tag' + (k === "mini" ? " hot" : "") + '">' +
        (m.tools === "native" ? "native tools" : "текстовые tools") + "</span></b><span>" + m.desc + " · окно " + (m.ctx / 1000).toFixed(0) + "k</span></div>";
      el.onclick = function () {
        state.model = k;
        store.set("lumen.model", k);
        $("#modelChipText").textContent = k;
        renderModels();
        closeSheets();
        updateHints();
        touch();
        toast("Модель: " + m.label);
      };
      list.appendChild(el);
    });
  }

  function updateHints() {
    $("#hintEngine").textContent = "движок: " + ({ builtin: "встроенный", termux: "Termux", auto: "авто" }[state.engine] || "встроенный");
    $("#hintCtx").textContent = "модель " + state.model + " · окно " + (((MODELS[state.model] || MODELS.mini).ctx) / 1000).toFixed(0) + "k";
    var badge = $("#pluginsBadge");
    var active = LumenPlugins.registry().filter(function (p) { return p.enabled; }).length;
    badge.textContent = String(active);
    badge.style.display = active ? "grid" : "none";
  }

  function applyCustom() {
    var c = state.custom;
    var acc = ACCENTS[c.accent] || ACCENTS[0];
    document.documentElement.style.setProperty("--a", acc.a);
    document.documentElement.style.setProperty("--a2", acc.a2);
    setAccentVars(acc);
    document.documentElement.style.setProperty("--fs", c.fontSize + "px");
    document.documentElement.setAttribute("data-theme", c.theme === "light" ? "light" : (c.theme === "auto" && WALLS[c.wall] && WALLS[c.wall].light ? "light" : "dark"));
    document.documentElement.setAttribute("data-radius", c.radius);
    document.documentElement.setAttribute("data-density", c.density);
    document.documentElement.setAttribute("data-avatars", c.avatars);
    var wall = WALLS[c.wall] || WALLS[0];
    document.body.style.background = wall.css;
    if (c.theme === "light" || (c.theme === "auto" && wall.light)) {
      document.documentElement.setAttribute("data-theme", "light");
      document.body.style.background = WALLS[5].css;
    } else {
      document.documentElement.setAttribute("data-theme", "dark");
    }
    LumenPlugins.emit("theme", { accent: acc.a, name: acc.name, light: document.documentElement.getAttribute("data-theme") === "light" });
  }

  function setAccentVars(acc) {
    var hex = acc.a.replace("#", "");
    var r = parseInt(hex.slice(0, 2), 16), g = parseInt(hex.slice(2, 4), 16), b = parseInt(hex.slice(4, 6), 16);
    document.documentElement.style.setProperty("--a-soft", "rgba(" + r + "," + g + "," + b + ",.14)");
    document.documentElement.style.setProperty("--a-line", "rgba(" + r + "," + g + "," + b + ",.4)");
  }

  function setAccent(color, color2) {
    var hex = String(color).replace("#", "");
    if (!/^[0-9a-f]{6}$/i.test(hex)) return;
    document.documentElement.style.setProperty("--a", "#" + hex);
    document.documentElement.style.setProperty("--a2", color2 ? "#" + String(color2).replace("#", "") : "#" + hex);
    setAccentVars({ a: "#" + hex });
  }

  function openSettings() {
    $("#stKeyMask").textContent = state.apiKey ? ("сохранён: " + state.apiKey.slice(0, 10) + "…" + state.apiKey.slice(-4)) : "не задан";
    $("#stKey").value = "";
    $("#stBase").value = state.baseUrl;
    $("#stModelDesc").textContent = (MODELS[state.model] || MODELS.mini).label + " — " + (MODELS[state.model] || MODELS.mini).desc;
    $("#stHome").textContent = state.native.home;
    $$("#stEngine button").forEach(function (b) { b.classList.toggle("on", b.dataset.v === state.engine); });
    $("#stShowTools").classList.toggle("on", state.showTools);
    $("#stConfirm").classList.toggle("on", state.confirm);
    $("#stAuto").classList.toggle("on", state.autoCompact);
    renderSwatches();
    $$("#stTheme button").forEach(function (b) { b.classList.toggle("on", b.dataset.v === state.custom.theme); });
    $$("#stRadius button").forEach(function (b) { b.classList.toggle("on", b.dataset.v === state.custom.radius); });
    $$("#stDensity button").forEach(function (b) { b.classList.toggle("on", b.dataset.v === state.custom.density); });
    $("#stFS").value = state.custom.fontSize;
    $("#stFSVal").textContent = state.custom.fontSize + "px";
    $("#stAvatars").classList.toggle("on", state.custom.avatars === "on");
    openSheet("#sheetSettings");
  }

  function renderSwatches() {
    var box = $("#stAccents");
    box.innerHTML = "";
    ACCENTS.forEach(function (a, i) {
      var el = document.createElement("div");
      el.className = "swatch" + (i === state.custom.accent ? " on" : "");
      el.style.background = "linear-gradient(135deg," + a.a + "," + a.a2 + ")";
      el.title = a.name;
      el.onclick = function () {
        state.custom.accent = i;
        store.set("lumen.c.accent", String(i));
        applyCustom();
        renderSwatches();
      };
      box.appendChild(el);
    });
    var walls = $("#stWalls");
    walls.innerHTML = "";
    WALLS.forEach(function (w, i) {
      var el = document.createElement("div");
      el.className = "swatch" + (i === state.custom.wall ? " on" : "");
      el.style.background = w.css.replace(/^radial-gradient[^,]*,/, "linear-gradient(135deg, ");
      el.style.background = w.light ? "linear-gradient(135deg,#fff4e6,#e8ddcd)" : "linear-gradient(135deg,#1b1b1f,#0b0b0d)";
      el.style.borderWidth = "2px";
      el.title = w.name;
      el.onclick = function () {
        state.custom.wall = i;
        store.set("lumen.c.wall", String(i));
        applyCustom();
        renderSwatches();
      };
      walls.appendChild(el);
    });
  }

  function renderSessions() {
    var box = $("#sessList");
    var list = state.sessions.slice().sort(function (a, b) { return b.updated - a.updated; });
    box.innerHTML = "";
    if (!list.length) {
      box.innerHTML = '<div class="note" style="padding:12px 0">Пока пусто</div>';
      return;
    }
    list.forEach(function (s) {
      var el = document.createElement("div");
      el.className = "sess" + (state.current && s.id === state.current.id ? " on" : "");
      el.innerHTML = '<div class="lbl"><b>' + esc(sessionTitle(s)) + "</b><span>" +
        new Date(s.updated).toLocaleString("ru-RU") + " · " + s.messages.filter(function (m) { return !m.hidden; }).length +
        " сообщ. · " + esc(s.model || "") + '</span></div><button class="btn ghost sm" data-del>Удалить</button>';
      el.onclick = function (e) {
        if (e.target.dataset.del !== undefined) {
          state.sessions = state.sessions.filter(function (x) { return x.id !== s.id; });
          if (state.current && state.current.id === s.id) state.current = null;
          saveSessions();
          renderSessions();
          newSession();
          closeSheets();
          return;
        }
        state.current = s;
        if (s.model && MODELS[s.model]) state.model = s.model;
        $("#modelChipText").textContent = state.model;
        saveSessions();
        renderChat();
        closeSheets();
        updateSubtitle();
        LumenPlugins.emit("session", { id: s.id });
      };
      box.appendChild(el);
    });
  }

  function renderPlugins() {
    var box = $("#pluginList");
    var list = LumenPlugins.registry();
    box.innerHTML = "";
    if (!list.length) {
      box.innerHTML = '<div class="note" style="padding:10px 0">Плагинов нет. Нажми «Установить плагин», чтобы добавить .plugin файл.</div>';
      return;
    }
    list.forEach(function (p) {
      var el = document.createElement("div");
      el.className = "plugincard" + (p.enabled ? " on" : "");
      el.innerHTML = '<div class="plugin-icon">' + esc(p.icon || "🧩") + '</div><div class="lbl"><b>' + esc(p.name) +
        ' <span class="tag">v' + esc(p.version || "1.0") + "</span>" + (p.source === "builtin" ? ' <span class="tag hot">встроенный</span>' : "") +
        "</b><span>" + esc((p.description || "").slice(0, 90)) + "</span><span>" + esc(p.author || "") + "</span></div>" +
        '<div class="switch' + (p.enabled ? " on" : "") + '" data-toggle><i></i></div>';
      el.querySelector("[data-toggle]").onclick = function (e) {
        e.stopPropagation();
        if (p.enabled) { LumenPlugins.disable(p.id); toast(p.name + " выключен"); }
        else { LumenPlugins.enable(p.id); toast(p.name + " включён"); }
        renderPlugins();
        updateHints();
      };
      el.onclick = function () { openPlugin(p.id); };
      box.appendChild(el);
    });
  }

  function openPlugin(id) {
    var p = LumenPlugins.get(id);
    if (!p) return;
    var box = $("#pluginDetail");
    var schema = p.settings || [];
    var html = '<div class="plugincard" style="cursor:default"><div class="plugin-icon">' + esc(p.icon || "🧩") + '</div><div class="lbl"><b>' +
      esc(p.name) + ' <span class="tag">v' + esc(p.version || "1.0") + '</span></b><span>' + esc(p.author || "") + "</span></div></div>" +
      '<div class="note" style="margin:8px 0 12px;white-space:pre-wrap">' + esc(p.description || "") + "</div>";
    if (schema.length) {
      html += '<div class="row" style="border:0;padding:4px 0"><div class="lbl"><b>Настройки плагина</b></div></div>';
      schema.forEach(function (f) {
        var val = LumenPlugins.settingOf(p.id, f.key, f.default);
        html += '<div class="row"><div class="lbl"><b>' + esc(f.label || f.key) + "</b>" + (f.hint ? "<span>" + esc(f.hint) + "</span>" : "") + "</div>";
        if (f.type === "switch") html += '<div class="switch' + (val ? " on" : "") + '" data-set="' + esc(f.key) + '" data-type="switch"><i></i></div>';
        else if (f.type === "select") {
          html += '<select class="btn" data-set="' + esc(f.key) + '" data-type="select" style="padding:9px">' +
            (f.options || []).map(function (o) { return '<option value="' + esc(o.value) + '"' + (String(val) === String(o.value) ? " selected" : "") + ">" + esc(o.label || o.value) + "</option>"; }).join("") + "</select>";
        } else if (f.type === "range") {
          html += '<input type="range" data-set="' + esc(f.key) + '" data-type="range" min="' + (f.min || 0) + '" max="' + (f.max || 100) + '" value="' + val + '" style="width:120px">';
        } else html += '<input class="btn" data-set="' + esc(f.key) + '" data-type="text" value="' + esc(val) + '" style="max-width:150px">';
        html += "</div>";
      });
    }
    html += '<div style="display:flex;gap:8px;margin-top:14px;flex-wrap:wrap">' +
      '<button class="btn" data-act="export">Скачать .plugin</button>' +
      '<button class="btn danger" data-act="remove">Удалить плагин</button></div>';
    box.innerHTML = html;
    box.querySelectorAll("[data-set]").forEach(function (el) {
      el.addEventListener("change", function () {
        var key = el.dataset.set, type = el.dataset.type;
        var value = type === "switch" ? !el.classList.contains("on") : (type === "range" ? parseInt(el.value, 10) : el.value);
        if (type === "switch") el.classList.toggle("on", value);
        LumenPlugins.setSetting(p.id, key, value);
        LumenPlugins.emit("plugin-settings", { id: p.id, key: key, value: value });
        toast("Сохранено");
      });
      if (el.dataset.type === "switch") {
        el.addEventListener("click", function () {
          var value = !el.classList.contains("on");
          el.classList.toggle("on", value);
          LumenPlugins.setSetting(p.id, el.dataset.set, value);
          LumenPlugins.emit("plugin-settings", { id: p.id, key: el.dataset.set, value: value });
        });
      }
    });
    box.querySelector('[data-act="export"]').onclick = function () { downloadPlugin(p); };
    box.querySelector('[data-act="remove"]').onclick = function () {
      LumenPlugins.uninstall(p.id);
      toast(p.name + " удалён");
      renderPlugins();
      updateHints();
      box.innerHTML = "";
    };
    openSheet("#sheetPlugin");
  }

  function pluginJson(p) {
    return JSON.stringify({
      id: p.id, name: p.name, version: p.version, author: p.author, description: p.description,
      icon: p.icon, homepage: p.homepage, tags: p.tags || [], settings: p.settings || [], code: p.code
    }, null, 2);
  }
  function downloadPlugin(p) {
    var blob = new Blob([pluginJson(p)], { type: "application/json" });
    var a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = p.id + ".plugin";
    a.click();
    toast("Файл " + p.id + ".plugin сохранён");
  }

  function installFromText(text) {
    var data;
    try { data = JSON.parse(text); } catch (e) { toast("Это не JSON: " + e.message); return; }
    var res = LumenPlugins.install(data, "file");
    if (!res.ok) { toast(res.message); return; }
    toast("Установлен: " + res.plugin.name);
    $("#pluginPaste").value = "";
    renderPlugins();
    updateHints();
    applyCustom();
  }

  function termLine(html, cls) {
    var body = $("#termBody");
    var div = document.createElement("div");
    div.className = cls || "out";
    div.innerHTML = html;
    body.appendChild(div);
    body.scrollTop = body.scrollHeight;
  }

  function runShell(cmd) {
    cmd = String(cmd || "").trim();
    if (!cmd) return Promise.resolve({ ok: true, output: "" });
    if (typeof Native === "undefined") return Promise.resolve({ ok: true, output: "(демо) " + cmd });
    var useTermux = state.native.termux && (state.engine === "termux" || (state.engine === "auto" && !BUILTIN_CMDS.test(cmd)));
    if (useTermux) {
      var tr = JSON.parse(Native.termuxRun(cmd, state.native.home, 60000));
      return Promise.resolve({ ok: !!tr.ok, output: tr.output || "", engine: "termux" });
    }
    var r = JSON.parse(Native.tool("bash", JSON.stringify({ command: cmd })));
    return Promise.resolve({ ok: !!r.ok, output: (r.output || "").replace(/^ошибка:\s*/, ""), engine: "встроенный" });
  }

  function nativeTool(name, args) {
    if (typeof Native === "undefined") return Promise.resolve({ ok: true, output: "(демо)" });
    var r = JSON.parse(Native.tool(name, JSON.stringify(args || {})));
    return Promise.resolve({ ok: !!r.ok, output: r.output || "" });
  }

  function runTerm(cmd) {
    cmd = String(cmd || "").trim();
    if (!cmd) return;
    state.termHist.push(cmd);
    state.termIdx = state.termHist.length;
    store.set("lumen.termHist", JSON.stringify(state.termHist.slice(-100)));
    termLine('<span style="color:var(--a)">❯</span> ' + esc(cmd), "in");
    if (cmd === "clear") { $("#termBody").innerHTML = ""; return; }
    var proceed = Promise.resolve(true);
    if (state.confirm && DANGEROUS.test(cmd)) proceed = askConfirm(cmd);
    proceed.then(function (okFlag) {
      if (!okFlag) { termLine("отменено", "note"); return; }
      runShell(cmd).then(function (r) {
        if (r.output) termLine(esc(r.output), r.ok ? "out" : "err");
        termLine('<span class="note">[' + (r.engine || "встроенный") + "]</span>");
      });
    });
  }

  function termBoot() {
    var body = $("#termBody");
    if (body.childElementCount) return;
    termLine('<span class="note">Lumen Agent · встроенный терминал · ' + esc(state.native.device || "preview") + "</span>", "note");
    termLine('<span class="note">папка: ' + esc(state.native.home) + "</span>", "note");
    termLine('<span class="note">команда help — список; движок переключается в настройках</span>', "note");
    try {
      state.termHist = JSON.parse(store.get("lumen.termHist", "[]"));
      state.termIdx = state.termHist.length;
    } catch (e) {}
  }

  function switchTab(which) {
    var chat = which === "chat";
    $("#chatView").style.display = chat ? "" : "none";
    $("#termView").style.display = chat ? "none" : "flex";
    $("#dock").style.display = chat ? "" : "none";
    $("#tabChat").classList.toggle("active", chat);
    $("#tabTerm").classList.toggle("active", !chat);
    if (!chat) setTimeout(function () { $("#termInput").focus(); }, 120);
  }

  function checkKey(key, cb) {
    if (!HAS_NATIVE) {
      setTimeout(function () { cb({ ok: /^lum_/.test(key), message: /^lum_/.test(key) ? "Ключ принят (демо-проверка)" : "Ключ должен начинаться с lum_" }); }, 450);
      return;
    }
    try { cb(JSON.parse(Native.ping(key, state.baseUrl))); } catch (e) { cb({ ok: false, message: "ошибка проверки: " + e.message }); }
  }

  function finishOnboard() {
    $("#onboard").classList.add("off");
    $("#modelChipText").textContent = state.model;
    updateHints();
    renderChat();
  }

  function pluginHelpText() {
    var lines = LumenPlugins.commandList().map(function (c) { return "`/" + c.name + "` — " + c.description; });
    var builtins = ["`/help` — команды", "`/new` — новый диалог", "`/model` — модель", "`/plugins` — плагины",
      "`/settings` — настройки", "`/theme` — оформление", "`/export` — экспорт диалога", "`/clear` — очистить"];
    return builtins.concat(lines).join("\n");
  }

  function builtinSlash(text) {
    var cmd = text.split(/\s+/)[0].toLowerCase();
    var arg = text.slice(cmd.length).trim();
    if (cmd === "/help") {
      session().messages.push({ role: "assistant", content: pluginHelpText(), ts: Date.now() });
      touch(); renderChat();
      return true;
    }
    if (cmd === "/new") { newSession(); toast("Новый диалог"); return true; }
    if (cmd === "/model") { renderModels(); openSheet("#sheetModel"); return true; }
    if (cmd === "/plugins") { renderPlugins(); openSheet("#sheetPlugins"); return true; }
    if (cmd === "/settings") { openSettings(); return true; }
    if (cmd === "/theme") { openSettings(); toast("Оформление — в настройках"); return true; }
    if (cmd === "/terminal") { switchTab("term"); return true; }
    if (cmd === "/clear") { session().messages = []; touch(); renderChat(); return true; }
    if (cmd === "/export") {
      var md = "# Диалог\n\n";
      session().messages.filter(function (m) { return !m.hidden; }).forEach(function (m) {
        md += "## " + m.role + "\n\n" + (m.content || "") + "\n\n";
      });
      var blob = new Blob([md], { type: "text/markdown" });
      var a = document.createElement("a");
      a.href = URL.createObjectURL(blob);
      a.download = "dialog-" + session().id + ".md";
      a.click();
      toast("Экспортировано");
      return true;
    }
    return false;
  }

  function init() {
    loadSessions();
    applyCustom();
    nativeEnv();
    if (!HAS_NATIVE) $("#demoBanner").classList.add("on");
    $("#modelChipText").textContent = state.model;
    $("#modelChip").onclick = function () { renderModels(); openSheet("#sheetModel"); };

    if (!state.current && state.sessions.length) {
      state.sessions.sort(function (a, b) { return a.updated - b.updated; });
      state.current = state.sessions[state.sessions.length - 1];
    }
    LumenPlugins.restore();
    LumenPlugins.loadBuiltin(window.BUILTIN_PLUGINS || []);
    renderChat();
    updateHints();
    termBoot();

    if (!state.apiKey) $("#onboard").classList.remove("off");
    else finishOnboard();

    $("#keySave").onclick = function () {
      var v = $("#keyInput").value.trim();
      var st = $("#keyStatus");
      if (!/^[\x21-\x7e]{12,}$/.test(v)) {
        st.className = "status err";
        st.textContent = "Похоже, это не ключ — нужна строка вида lum_…";
        return;
      }
      st.className = "status busy";
      st.textContent = "Проверяю ключ…";
      checkKey(v, function (res) {
        state.apiKey = v;
        store.set("lumen.key", v);
        if (res.ok) {
          st.className = "status ok";
          st.textContent = "✔ " + (res.message || "Готово");
          setTimeout(finishOnboard, 420);
        } else {
          st.className = "status err";
          st.textContent = "✖ " + (res.message || "не сработало") + " — сохранил, исправишь в настройках";
        }
      });
    };
    $("#keyWhere").onclick = function () { openUrl("https://lumen.unionium.org/chat"); };
    $("#keyInput").addEventListener("keydown", function (e) { if (e.key === "Enter") $("#keySave").click(); });

    $("#btnNew").onclick = function () { newSession(); toast("Новый диалог"); };
    $("#btnSessions").onclick = function () { renderSessions(); openSheet("#sheetSessions"); };
    $("#btnSettings").onclick = openSettings;
    $("#btnPlugins").onclick = function () { renderPlugins(); openSheet("#sheetPlugins"); };
    $("#backdrop").onclick = closeSheets;
    $("#sessNew").onclick = function () { newSession(); renderSessions(); closeSheets(); };

    var input = $("#input");
    input.addEventListener("input", autoGrow);
    input.addEventListener("keydown", function (e) {
      if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); send(); }
    });
    $("#sendBtn").onclick = function () {
      if (state.streaming) {
        state.pending = {};
        setStreaming(false);
        toast("Остановлено");
      } else send();
    };

    $("#stKeySave").onclick = function () {
      var v = $("#stKey").value.trim();
      if (!v) { toast("Введи ключ"); return; }
      state.apiKey = v;
      store.set("lumen.key", v);
      $("#stKeyMask").textContent = "сохранён: " + v.slice(0, 10) + "…" + v.slice(-4);
      checkKey(v, function (res) { toast((res.ok ? "✔ " : "✖ ") + (res.message || "")); });
    };
    $("#stKeyLink").onclick = function (e) { e.preventDefault(); openUrl("https://lumen.unionium.org/chat"); };
    $("#stPing").onclick = function () {
      if (!state.apiKey) { toast("Сначала сохрани ключ"); return; }
      checkKey(state.apiKey, function (r) { toast((r.ok ? "✔ " : "✖ ") + (r.message || "")); });
    };
    $("#stBase").onchange = function () {
      state.baseUrl = $("#stBase").value.trim() || state.baseUrl;
      store.set("lumen.base", state.baseUrl);
      toast("Base URL сохранён");
    };
    $("#stModelPick").onclick = function () { renderModels(); openSheet("#sheetModel"); };
    $$("#stEngine button").forEach(function (b) {
      b.onclick = function () {
        state.engine = b.dataset.v;
        store.set("lumen.engine", state.engine);
        $$("#stEngine button").forEach(function (x) { x.classList.toggle("on", x === b); });
        updateHints();
        toast("Движок: " + b.textContent);
        if (state.engine !== "builtin" && !state.native.termux) toast("Termux не найден — команды идут во встроенный шелл");
      };
    });
    $("#stShowTools").onclick = function () {
      state.showTools = !state.showTools;
      store.set("lumen.showTools", state.showTools ? "1" : "0");
      $("#stShowTools").classList.toggle("on", state.showTools);
      renderChat();
    };
    $("#stConfirm").onclick = function () {
      state.confirm = !state.confirm;
      store.set("lumen.confirm", state.confirm ? "1" : "0");
      $("#stConfirm").classList.toggle("on", state.confirm);
    };
    $("#stAuto").onclick = function () {
      state.autoCompact = !state.autoCompact;
      store.set("lumen.autocompact", state.autoCompact ? "1" : "0");
      $("#stAuto").classList.toggle("on", state.autoCompact);
    };
    $$("#stTheme button").forEach(function (b) {
      b.onclick = function () {
        state.custom.theme = b.dataset.v;
        store.set("lumen.c.theme", b.dataset.v);
        $$("#stTheme button").forEach(function (x) { x.classList.toggle("on", x === b); });
        applyCustom();
      };
    });
    $$("#stRadius button").forEach(function (b) {
      b.onclick = function () {
        state.custom.radius = b.dataset.v;
        store.set("lumen.c.radius", b.dataset.v);
        $$("#stRadius button").forEach(function (x) { x.classList.toggle("on", x === b); });
        applyCustom();
      };
    });
    $$("#stDensity button").forEach(function (b) {
      b.onclick = function () {
        state.custom.density = b.dataset.v;
        store.set("lumen.c.density", b.dataset.v);
        $$("#stDensity button").forEach(function (x) { x.classList.toggle("on", x === b); });
        applyCustom();
      };
    });
    $("#stFS").oninput = function () {
      state.custom.fontSize = parseInt(this.value, 10);
      store.set("lumen.c.fs", String(state.custom.fontSize));
      $("#stFSVal").textContent = state.custom.fontSize + "px";
      applyCustom();
    };
    $("#stAvatars").onclick = function () {
      state.custom.avatars = state.custom.avatars === "on" ? "off" : "on";
      store.set("lumen.c.avatars", state.custom.avatars);
      $("#stAvatars").classList.toggle("on", state.custom.avatars === "on");
      applyCustom();
    };
    $("#stClear").onclick = function () {
      state.sessions = [];
      state.current = null;
      saveSessions();
      newSession();
      renderChat();
      toast("Диалоги очищены");
    };
    $("#stReset").onclick = function () {
      Object.keys(localStorage).forEach(function (k) { if (k.indexOf("lumen.") === 0) localStorage.removeItem(k); });
      location.reload();
    };

    $("#pluginInstallBtn").onclick = function () { $("#pluginInstall").style.display = ""; $("#pluginPaste").focus(); };
    $("#pluginPasteGo").onclick = function () { installFromText($("#pluginPaste").value); };
    $("#pluginFile").onchange = function () {
      var f = this.files && this.files[0];
      if (!f) return;
      var r = new FileReader();
      r.onload = function () { installFromText(String(r.result)); };
      r.readAsText(f);
      this.value = "";
    };
    $("#pluginUrlGo").onclick = function () {
      var url = $("#pluginUrl").value.trim();
      if (!url) return;
      fetch(url).then(function (r) { return r.text(); }).then(installFromText).catch(function (e) { toast("Не скачалось: " + e.message); });
    };
    $("#pluginDocs").onclick = function () { openUrl("https://frizovdanyash.github.io/agentlumenapi/plugins.html"); };

    $("#termRun").onclick = function () {
      var v = $("#termInput").value;
      $("#termInput").value = "";
      runTerm(v);
    };
    $("#termInput").addEventListener("keydown", function (e) {
      if (e.key === "Enter") {
        e.preventDefault();
        var v = e.target.value;
        e.target.value = "";
        runTerm(v);
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        if (state.termIdx > 0) {
          state.termIdx--;
          e.target.value = state.termHist[state.termIdx] || "";
        }
      } else if (e.key === "ArrowDown") {
        e.preventDefault();
        if (state.termIdx < state.termHist.length - 1) {
          state.termIdx++;
          e.target.value = state.termHist[state.termIdx] || "";
        } else {
          state.termIdx = state.termHist.length;
          e.target.value = "";
        }
      }
    });
    $$(".termkeys button").forEach(function (b) {
      b.onclick = function () {
        var v = b.dataset.cmd;
        if (v === "__termux__") {
          state.engine = "termux";
          store.set("lumen.engine", "termux");
          updateHints();
          toast("Движок: Termux");
          return;
        }
        $("#termInput").value = v;
        $("#termInput").focus();
      };
    });
    $("#tabChat").onclick = function () { switchTab("chat"); };
    $("#tabTerm").onclick = function () { switchTab("term"); };

    document.addEventListener("click", function (e) {
      var a = e.target.closest ? e.target.closest("[data-url]") : null;
      if (a) { e.preventDefault(); openUrl(a.dataset.url); }
    });
    window.addEventListener("resize", function () { scrollBottom(true); });
  }

  function openUrl(url) {
    if (HAS_NATIVE) Native.openUrl(url);
    else window.open(url, "_blank");
  }

  window.UI = {
    toast: toast,
    runShell: runShell,
    nativeTool: nativeTool,
    sendText: sendText,
    injectSystem: function (text) {
      session().messages.push({ role: "system", content: text, hidden: true });
      touch();
    },
    messages: function () { return session().messages.filter(function (m) { return !m.hidden; }); },
    setAccent: setAccent,
    themeName: function () { return (ACCENTS[state.custom.accent] || ACCENTS[0]).name; },
    onPluginLoaded: function () { updateHints(); },
    onPluginsChanged: function () { renderPlugins(); updateHints(); },
    openSettings: openSettings,
    version: APP_VERSION,
    author: AUTHOR
  };

  document.addEventListener("DOMContentLoaded", init);
})();
