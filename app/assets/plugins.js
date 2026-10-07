(function () {
  "use strict";

  var STORE_KEY = "lumen.plugins";
  var registry = [];
  var commands = {};
  var tools = {};
  var hooks = {};
  var promptBits = [];
  var running = {};
  var storageNS = {};

  function readStore() {
    try { return JSON.parse(localStorage.getItem(STORE_KEY) || "[]"); } catch (e) { return []; }
  }
  function writeStore(list) {
    try { localStorage.setItem(STORE_KEY, JSON.stringify(list)); } catch (e) {}
  }

  function emit(name, payload) {
    var list = (hooks[name] || []).slice();
    for (var i = 0; i < list.length; i++) {
      try { list[i](payload); } catch (e) { log("ошибка хука " + name, e); }
    }
  }

  function log() {
    var args = Array.prototype.slice.call(arguments);
    try { console.log.apply(console, ["[plugin]"].concat(args)); } catch (e) {}
  }

  function ns(id) {
    if (!storageNS[id]) {
      storageNS[id] = {
        get: function (k, d) {
          var v = localStorage.getItem("lumen.plugin." + id + "." + k);
          if (v === null) return d;
          try { return JSON.parse(v); } catch (e) { return v; }
        },
        set: function (k, v) { try { localStorage.setItem("lumen.plugin." + id + "." + k, JSON.stringify(v)); } catch (e) {} },
        del: function (k) { try { localStorage.removeItem("lumen.plugin." + id + "." + k); } catch (e) {} },
        keys: function () {
          var out = [];
          for (var i = 0; i < localStorage.length; i++) {
            var key = localStorage.key(i);
            if (key && key.indexOf("lumen.plugin." + id + ".") === 0) out.push(key.split(".").pop());
          }
          return out;
        }
      };
    }
    return storageNS[id];
  }

  function makeApi(rec) {
    var api = {
      id: rec.id,
      name: rec.name,
      version: rec.version,
      author: rec.author,
      store: ns(rec.id),
      log: function () { log.apply(null, [rec.id].concat(Array.prototype.slice.call(arguments))); },
      toast: function (m) { if (window.UI && UI.toast) UI.toast(m); },
      on: function (name, fn) { (hooks[name] = hooks[name] || []).push(fn); rec._hooks.push([name, fn]); },
      onMessage: function (fn) { api.on("user", fn); },
      onAssistant: function (fn) { api.on("assistant", fn); },
      onSend: function (fn) { api.on("before-send", fn); },
      onToolResult: function (fn) { api.on("tool", fn); },
      onChatOpen: function (fn) { api.on("session", fn); },
      onTheme: function (fn) { api.on("theme", fn); },
      onTick: function (fn) { api.on("tick", fn); },
      command: function (def) {
        commands[def.name] = { plugin: rec.id, def: def };
        rec._commands.push(def.name);
      },
      tool: function (def) {
        tools[def.name] = { plugin: rec.id, def: def };
        rec._tools.push(def.name);
      },
      systemPrompt: function (text) {
        promptBits.push({ id: rec.id, text: text });
        rec._prompt = true;
      },
      layer: function () {
        var el = document.getElementById("pluginLayer");
        if (!el) {
          el = document.createElement("div");
          el.id = "pluginLayer";
          el.className = "plugin-ui";
          document.body.appendChild(el);
        }
        return el;
      },
      mount: function (el) { var host = api.layer(); host.appendChild(el); return el; },
      shell: function (cmd) { return window.UI.runShell(cmd); },
      fs: {
        read: function (p) { return window.UI.nativeTool("read_file", { path: p }); },
        write: function (p, c) { return window.UI.nativeTool("write_file", { path: p, content: c }); },
        ls: function (p) { return window.UI.nativeTool("ls", { path: p || "." }); }
      },
      chat: {
        send: function (t) { if (window.UI && UI.sendText) UI.sendText(t); },
        inject: function (t) { if (window.UI && UI.injectSystem) UI.injectSystem(t); },
        messages: function () { return window.UI ? UI.messages() : []; },
        last: function () {
          var m = api.chat.messages();
          return m.length ? m[m.length - 1] : null;
        }
      },
      theme: {
        accent: function (color, color2) { if (window.UI && UI.setAccent) UI.setAccent(color, color2 || color); },
        current: function () { return window.UI ? UI.themeName() : "default"; }
      },
      asset: function (name) { return "assets/" + name; },
      cleanup: function (fn) { rec._cleanup.push(fn); }
    };
    return api;
  }

  function instantiate(rec) {
    if (rec._api) return rec._api;
    rec._hooks = [];
    rec._cleanup = [];
    rec._commands = [];
    rec._tools = [];
    rec._prompt = false;
    var api = makeApi(rec);
    rec._api = api;
    running[rec.id] = api;
    try {
      var factory = new Function("api", "Lumen", "window", "document", rec.code + "\n;return (typeof onLoad === 'function') ? onLoad(api) : undefined;");
      var cleanup = factory(api, api, window, document);
      if (typeof cleanup === "function") rec._cleanup.push(cleanup);
      api.log("загружен, версия " + (rec.version || "?"));
      if (window.UI && UI.onPluginLoaded) UI.onPluginLoaded(rec);
    } catch (e) {
      rec.error = String(e && e.message ? e.message : e);
      log("не удалось запустить " + rec.id, e);
      if (window.UI && window.UI.toast) window.UI.toast("Плагин " + rec.name + ": " + rec.error);
    }
    return api;
  }

  function teardown(rec) {
    for (var i = 0; i < rec._cleanup.length; i++) {
      try { rec._cleanup[i](); } catch (e) {}
    }
    for (var j = 0; j < rec._hooks.length; j++) {
      var pair = rec._hooks[j];
      var list = hooks[pair[0]] || [];
      var idx = list.indexOf(pair[1]);
      if (idx >= 0) list.splice(idx, 1);
    }
    for (var k = 0; k < rec._commands.length; k++) delete commands[rec._commands[k]];
    for (var m = 0; m < rec._tools.length; m++) delete tools[rec._tools[m]];
    if (rec._prompt) {
      for (var n = promptBits.length - 1; n >= 0; n--) if (promptBits[n].id === rec.id) promptBits.splice(n, 1);
    }
    var layer = document.getElementById("pluginLayer");
    if (layer) layer.querySelectorAll('[data-plugin="' + rec.id + '"]').forEach(function (el) { el.remove(); });
    delete running[rec.id];
    rec._api = null;
  }

  function validate(data) {
    if (!data || typeof data !== "object") return "файл не похож на .plugin";
    if (!data.id || !/^[a-z0-9_.-]{2,48}$/i.test(data.id)) return "нужен корректный id (латиница, цифры, _ . -)";
    if (!data.name) return "нужно поле name";
    if (!data.code || typeof data.code !== "string") return "нет кода плагина (поле code)";
    return null;
  }

  function storeItem(rec) {
    var list = readStore();
    for (var i = 0; i < list.length; i++) {
      if (list[i].id === rec.id) {
        list[i] = slim(rec);
        writeStore(list);
        return;
      }
    }
    list.push(slim(rec));
    writeStore(list);
  }

  function slim(rec) {
    return {
      id: rec.id, name: rec.name, version: rec.version || "1.0", author: rec.author || "",
      description: rec.description || "", icon: rec.icon || "🧩", homepage: rec.homepage || "",
      tags: rec.tags || [], settings: rec.settings || [], code: rec.code, enabled: rec.enabled !== false,
      source: rec.source || "file", installed: rec.installed || Date.now()
    };
  }

  function restore() {
    var list = readStore();
    registry = list.map(function (item) {
      item.enabled = item.enabled !== false;
      return item;
    });
    for (var i = 0; i < registry.length; i++) {
      if (registry[i].enabled) instantiate(registry[i]);
    }
    return registry;
  }

  function install(data, source) {
    var bad = validate(data);
    if (bad) return { ok: false, message: bad };
    var existing = get(data.id);
    if (existing) uninstall(data.id, true);
    var rec = slim(data);
    rec.source = source || "file";
    rec.installed = Date.now();
    rec.enabled = true;
    registry.push(rec);
    storeItem(rec);
    instantiate(rec);
    return { ok: true, plugin: rec };
  }

  function uninstall(id, silent) {
    var rec = get(id);
    if (!rec) return { ok: false, message: "плагин не найден" };
    if (rec._api) teardown(rec);
    registry = registry.filter(function (r) { return r.id !== id; });
    writeStore(registry.map(slim));
    if (!silent && window.UI && UI.onPluginsChanged) UI.onPluginsChanged();
    return { ok: true };
  }

  function enable(id) {
    var rec = get(id);
    if (!rec) return { ok: false, message: "плагин не найден" };
    rec.enabled = true;
    storeItem(rec);
    instantiate(rec);
    return { ok: true };
  }

  function disable(id) {
    var rec = get(id);
    if (!rec) return { ok: false, message: "плагин не найден" };
    rec.enabled = false;
    storeItem(rec);
    if (rec._api) teardown(rec);
    return { ok: true };
  }

  function get(id) {
    for (var i = 0; i < registry.length; i++) if (registry[i].id === id) return registry[i];
    return null;
  }

  function all() { return registry.slice(); }

  function settingOf(id, key, def) {
    var rec = get(id);
    if (!rec) return def;
    var store = ns(id);
    var v = store.get("setting." + key, undefined);
    if (v !== undefined) return v;
    var schema = rec.settings || [];
    for (var i = 0; i < schema.length; i++) if (schema[i].key === key && schema[i].default !== undefined) return schema[i].default;
    return def;
  }

  function setSetting(id, key, value) {
    ns(id).set("setting." + key, value);
    var rec = get(id);
    if (rec && rec._api) emit("plugin-settings", { id: id, key: key, value: value });
  }

  function toolDefs() {
    var out = [];
    for (var name in tools) if (tools[name]) out.push(tools[name].def);
    return out;
  }

  function runTool(name, args) {
    if (!tools[name]) return { ok: false, output: "инструмент не найден: " + name };
    try {
      var res = tools[name].def.run(args || {});
      if (res && typeof res === "object") return { ok: res.ok !== false, output: String(res.output === undefined ? JSON.stringify(res) : res.output) };
      return { ok: true, output: String(res === undefined ? "готово" : res) };
    } catch (e) {
      return { ok: false, output: "ошибка плагина: " + (e && e.message ? e.message : e) };
    }
  }

  function runCommand(name, argText) {
    if (!commands[name]) return { ok: false, output: "нет такой команды" };
    try {
      var out = commands[name].def.run(argText || "");
      return { ok: true, output: String(out === undefined ? "" : out) };
    } catch (e) {
      return { ok: false, output: "ошибка: " + (e && e.message ? e.message : e) };
    }
  }

  function commandList() {
    var out = [];
    for (var name in commands) {
      out.push({ name: name, description: commands[name].def.description || "", plugin: commands[name].plugin });
    }
    return out;
  }

  function promptExtra() {
    var bits = [];
    for (var i = 0; i < promptBits.length; i++) bits.push(promptBits[i].text);
    return bits.join("\n");
  }

  function loadBuiltin(list) {
    var installed = readStore().map(function (r) { return r.id; });
    var fresh = [];
    for (var i = 0; i < (list || []).length; i++) {
      var item = list[i];
      if (installed.indexOf(item.id) === -1) {
        var rec = slim(item);
        rec.source = "builtin";
        registry.push(rec);
        storeItem(rec);
        fresh.push(rec);
        if (rec.enabled) instantiate(rec);
      }
    }
    return fresh;
  }

  function tick() {
    emit("tick", { t: Date.now() });
  }

  window.LumenPlugins = {
    registry: all, get: get, install: install, uninstall: uninstall, enable: enable, disable: disable,
    setSetting: setSetting, settingOf: settingOf, restore: restore, loadBuiltin: loadBuiltin,
    toolDefs: toolDefs, runTool: runTool, runCommand: runCommand, commandList: commandList,
    promptExtra: promptExtra, emit: emit, tick: tick, validate: validate
  };

  setInterval(tick, 1000);
})();
