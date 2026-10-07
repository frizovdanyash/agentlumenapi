function onLoad(api) {
  function notes() {
    var list = api.store.get("items", []);
    return Array.isArray(list) ? list : [];
  }
  function save(list) {
    var limit = parseInt(api.store.get("setting.limit"), 10) || 50;
    api.store.set("items", list.slice(-limit));
  }
  function setting(key, def) {
    var v = api.store.get("setting." + key, undefined);
    return v === undefined ? def : v;
  }

  api.command({
    name: "clip",
    description: "Сохранить заметку: /clip купить хлеб",
    run: function (arg) {
      var text = String(arg || "").trim();
      if (!text) return "Напиши текст: `/clip купить хлеб`";
      var list = notes();
      list.push({ text: text, ts: Date.now() });
      save(list);
      return "Записал: **" + text + "** (всего " + notes().length + ")";
    }
  });

  api.command({
    name: "clips",
    description: "Показать все заметки",
    run: function () {
      var list = notes();
      if (!list.length) return "Заметок пока нет. Добавь: `/clip текст`";
      var prefix = setting("prefix", "•");
      return list.map(function (n, i) {
        return (i + 1) + ". " + prefix + " " + n.text + "  _(" + new Date(n.ts).toLocaleDateString("ru-RU") + ")_";
      }).join("\n");
    }
  });

  api.command({
    name: "clip-del",
    description: "Удалить заметку по номеру: /clip-del 2",
    run: function (arg) {
      var idx = parseInt(String(arg || "").trim(), 10) - 1;
      var list = notes();
      if (!(idx >= 0 && idx < list.length)) return "Нет заметки с таким номером";
      var removed = list.splice(idx, 1)[0];
      save(list);
      return "Удалил: " + removed.text;
    }
  });

  api.tool({
    name: "notes_add",
    description: "Добавить заметку пользователю в список быстрых заметок",
    parameters: { type: "object", properties: { text: { type: "string", description: "текст заметки" } }, required: ["text"] },
    run: function (args) {
      var text = String((args && args.text) || "").trim();
      if (!text) return { ok: false, output: "нужен текст заметки" };
      var list = notes();
      list.push({ text: text, ts: Date.now() });
      save(list);
      return { ok: true, output: "заметка добавлена, всего " + notes().length };
    }
  });

  api.tool({
    name: "notes_list",
    description: "Показать все заметки пользователя",
    parameters: { type: "object", properties: {}, required: [] },
    run: function () {
      var list = notes();
      if (!list.length) return { ok: true, output: "заметок нет" };
      return { ok: true, output: list.map(function (n, i) { return (i + 1) + ") " + n.text; }).join("\n") };
    }
  });

  api.systemPrompt("У пользователя включён плагин заметок: добавляй заметки инструментом notes_add, а смотри их через notes_list, когда это уместно.");

  api.log("заметки готовы");
  return function cleanup() {};
}
