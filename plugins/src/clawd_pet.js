function onLoad(api) {
  var W = 18, H = 13;
  var PALETTES = [
    { name: "Claude", body: "#d97757", dark: "#a9512f", eye: "#1a1a1a" },
    { name: "Мятный", body: "#4fbf98", dark: "#2f8c6d", eye: "#12241d" },
    { name: "Лавандовый", body: "#9b8aee", dark: "#6c5cc4", eye: "#1b1631" },
    { name: "Небесный", body: "#4fa8e8", dark: "#2d7cba", eye: "#0e1c29" },
    { name: "Розовый", body: "#ee85ae", dark: "#c4567f", eye: "#2a1220" },
    { name: "Графит", body: "#8b939c", dark: "#5c646c", eye: "#f2f2f2" }
  ];
  var SIZES = [3, 4, 5];
  var SLEEP_SEC = [60, 180, 600, 0];
  var HEART = "#ff5c7a", ZZZ = "#8fa3bf", SPARK = "#ffc94d";

  var SPRITE = [
    "..................",
    ".....b......b.....",
    "....bb......bb....",
    "...bbbbbbbbbbbb...",
    "..bbbbbbbbbbbbbb..",
    "..bbwwbbbbbbwwbb..",
    "..bbwkwbbbbwkwbb..",
    "..bbwwbbbbbbwwbb..",
    "..bbbbbbmmbbbbbb..",
    "..BbbbbbbbbbbbbB..",
    "...b.bb.bb.bb.b...",
    "...f.f..ff..f.f...",
    ".................."
  ];
  var PHRASES = {
    hello: ["Привет!", "Я Clawd!", "О, это ты!"],
    idle: ["Чем помочь?", "Хм…", "Думаю…", "Можно я тут посижу?", "Ты молодец!", "Не забудь попить воды", "Пип-пип!", "Мне нравится это приложение", "*оглядывается*", "Пишу код… шучу"],
    tap: ["Ой!", "Эй!", "Чего?", "Я тут!", "Хи!"],
    annoyed: ["Хватит тыкать!", "Щекотно же!", "Ну всё, обиделся", "Эй, полегче!"],
    pet: ["Мрр…", "Ещё!", "Приятно ♥", "Хи-хи", "Лучший день!", "♥♥♥"],
    drag: ["Ааа!", "Куда мы?", "Поставь меня!"],
    throw: ["Уиии!", "Я лечу!", "Ваааа!"],
    dizzy: ["Голова кружится…", "@_@", "Где я?"],
    wake: ["А? Что?", "Я не спал!", "*зевает*"],
    jump: ["Хоп!", "Йе!", "Ура!"],
    eat: ["Ням!", "Хрум-хрум", "Вкусная кнопка", "*жуёт*", "Ммм, пиксели", "Добавки!"],
    spit: ["Ладно, отдаю!", "Тьфу!", "Ой, простите", "Забирай…", "Оно само!"],
    mail: ["Тебе пишут!", "Сообщение!", "Кто-то написал!", "Динь-динь!"],
    sent: ["Отправлено!", "Улетело!", "Хорошо сказано!", "Пиу!"],
    essay: ["Ого, целое эссе", "Это роман?", "Много букв!"],
    love: ["Ааа, мило ♥", "♥♥♥", "Как трогательно!", "Любовь!"],
    laugh: ["Ахаха!", "Хи-хи-хи", "Смешно!", "Ха-ха!"],
    hi: ["Привет-привет!", "И тебе привет!", "Здрасьте!", "*машет*"],
    poop: ["Упс…", "Это не я!", "Ой…", "*краснеет*"],
    clean: ["Спасибо!", "Прости…", "Больше не буду!", "Ты лучший"],
    peek: ["Ку-ку!", "Я тебя вижу", "*подглядывает*"],
    found: ["Нашёл меня!", "Эх, раскусил", "Я просто смотрел!"]
  };
  var KEYWORDS = [
    { key: "love", rx: /❤|♥|💕|💖|😘|😍|🥰|\bлюблю\b|\bобнима\w*|\bцелую\b|\blove\b/i },
    { key: "laugh", rx: /(?:ха){2,}|(?:хе){2,}|(?:хи){2,}|\bлол\b|\blol\b|\bржу\w*|😂|🤣/i },
    { key: "hi", rx: /\b(?:привет\w*|здравствуй\w*|хай|салют|hello|hi|hey)\b/i }
  ];
  var EAT_TARGETS = "#modelChip, header .iconbtn, nav button, .sugg button, .tool .tname, .md pre code, .chip, .plugin-icon, .btn.sm";
  var MAX_EATEN = 5, MAX_POOPS = 3;

  var cfg = {
    size: api.store.get("size", 1),
    palette: api.store.get("palette", 0),
    sleep: api.store.get("sleep", 1),
    poop: api.store.get("poop", true),
    talk: api.store.get("talk", true),
    particles: api.store.get("particles", true),
    eat: api.store.get("eat", true)
  };

  var layer = api.layer();
  var canvas = document.createElement("canvas");
  canvas.width = 10;
  canvas.height = 10;
  canvas.style.position = "fixed";
  canvas.style.inset = "0";
  canvas.style.width = "100%";
  canvas.style.height = "100%";
  canvas.style.zIndex = "31";
  canvas.style.pointerEvents = "auto";
  canvas.setAttribute("data-plugin", api.id);
  layer.appendChild(canvas);
  var ctx = canvas.getContext("2d");
  var dpr = window.devicePixelRatio || 1;
  var rafFn = window.requestAnimationFrame ? window.requestAnimationFrame.bind(window) : function (fn) { return setTimeout(function () { fn(Date.now()); }, 16); };
  var cancelRaf = window.cancelAnimationFrame ? window.cancelAnimationFrame.bind(window) : clearTimeout;

  var unit = SIZES[cfg.size] || 4;
  var pw = W * unit, ph = H * unit;
  var pet = {
    x: 40, y: 0, vx: 0, vy: 0, look: 1, onGround: true, state: "idle",
    mouth: 0, chew: 0, expr: "normal", exprUntil: 0, squash: 0, hop: 0,
    walkTarget: null, nextAction: 0, nextEat: 0, sleeping: false, lastActive: Date.now(),
    poopMeter: api.store.get("poopMeter", 0), poopNeed: 3, poopAt: 0, drag: false
  };
  var bubble = { text: "", until: 0 };
  var particles = [];
  var eaten = [];
  var poops = [];
  var flying = [];
  var pointer = { id: null, x: 0, y: 0, t0: 0, moved: false, petSince: 0, lastX: 0, taps: 0, tapTimer: 0, holdStart: 0, holdStroke: 0 };
  var raf = 0, last = performance.now();
  var disposed = false;

  function floorY() {
    var dock = document.querySelector(".dock");
    var h = window.innerHeight;
    if (dock && dock.offsetParent !== null) {
      var r = dock.getBoundingClientRect();
      h = Math.min(h, r.top);
    }
    return Math.max(80, h - ph - 6);
  }
  function maxX() { return Math.max(8, window.innerWidth - pw - 8); }

  function resize() {
    canvas.width = Math.floor(window.innerWidth * dpr);
    canvas.height = Math.floor(window.innerHeight * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }
  resize();
  window.addEventListener("resize", resize);

  function say(group) {
    if (!cfg.talk) return;
    var list = PHRASES[group];
    if (!list || !list.length) return;
    bubble.text = list[Math.floor(Math.random() * list.length)];
    bubble.until = performance.now() + 2400;
  }

  function spawn(kind, x, y) {
    if (!cfg.particles) return;
    particles.push({ kind: kind, x: x, y: y, vx: (Math.random() - 0.5) * 22, vy: -30 - Math.random() * 20, life: 1.1, t: 0, size: 1 + Math.random() * 0.5 });
    if (particles.length > 40) particles.shift();
  }

  function setExpr(name, secs) {
    pet.expr = name;
    pet.exprUntil = performance.now() + secs * 1000;
  }

  function hop(power) {
    if (!pet.onGround) return;
    pet.vy = -power;
    pet.onGround = false;
    pet.squash = 0.14;
  }

  function drawSprite(px, py, scale, palette, expr) {
    var u = scale;
    var c = PALETTES[palette] || PALETTES[0];
    var mouthOpen = expr === "open" || expr === "eat";
    for (var row = 0; row < H; row++) {
      var line = SPRITE[row];
      for (var col = 0; col < W; col++) {
        var ch = line.charAt(col);
        if (ch === "." || ch === " ") continue;
        var color = null;
        if (ch === "b") color = c.body;
        else if (ch === "B") color = c.dark;
        else if (ch === "f") color = c.dark;
        else if (ch === "w" || ch === "k") color = ch === "w" ? "#ffffff" : c.eye;
        else if (ch === "m") color = "#7a2b1f";
        if (!color) continue;
        if (ch === "k") {
          if (expr === "sleep") continue;
          if (expr === "happy") color = "#3a1a12";
        }
        if (ch === "w") {
          if (expr === "sleep") { color = c.body; }
          else if (expr === "dizzy") color = "#ffffff";
          else if (expr === "love") color = HEART;
        }
        if (ch === "k" && expr === "dizzy") color = "#ffffff";
        if (ch === "m" && mouthOpen) {
          ctx.fillStyle = "#5c1f16";
          ctx.fillRect(px + col * u, py + row * u - u, u, u * 2);
          continue;
        }
        ctx.fillStyle = color;
        ctx.fillRect(px + col * u, py + row * u, u, u);
      }
    }
    if (expr === "dizzy") {
      ctx.fillStyle = "#7a2b1f";
      ctx.font = "bold " + (u * 5) + "px monospace";
      ctx.textAlign = "center";
      ctx.fillText("@", px + pw / 2, py + u * 8);
    }
    if (expr === "love") {
      ctx.fillStyle = HEART;
      ctx.fillRect(px + u * 6, py + u * 5, u, u);
      ctx.fillRect(px + u * 11, py + u * 5, u, u);
    }
  }

  function drawBubble() {
    if (!bubble.text || performance.now() > bubble.until) return;
    ctx.font = "600 " + Math.max(11, unit * 3) + "px -apple-system,system-ui,sans-serif";
    var w = ctx.measureText(bubble.text).width + 20;
    var h = Math.max(26, unit * 7);
    var bx = Math.min(Math.max(6, pet.x + pw / 2 - w / 2), window.innerWidth - w - 6);
    var by = Math.max(6, pet.y - h - 8);
    ctx.fillStyle = "rgba(27,23,20,.94)";
    ctx.strokeStyle = "rgba(255,138,61,.4)";
    ctx.lineWidth = 1;
    roundRect(bx, by, w, h, 10);
    ctx.fill();
    ctx.stroke();
    ctx.fillStyle = "#f7f2ec";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    ctx.fillText(bubble.text, bx + w / 2, by + h / 2 + 1);
    ctx.textBaseline = "alphabetic";
  }

  function roundRect(x, y, w, h, r) {
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
  }

  function drawParticles() {
    for (var i = 0; i < particles.length; i++) {
      var p = particles[i];
      var alpha = Math.max(0, 1 - p.t / p.life);
      ctx.globalAlpha = alpha;
      if (p.kind === "heart") {
        ctx.fillStyle = HEART;
        var s = unit;
        ctx.fillRect(p.x - s, p.y - s, s, s);
        ctx.fillRect(p.x + s, p.y - s, s, s);
        ctx.fillRect(p.x - s * 1.5, p.y, s * 3, s);
        ctx.fillRect(p.x - s * 0.5, p.y + s, s, s);
      } else if (p.kind === "zzz") {
        ctx.fillStyle = ZZZ;
        ctx.font = "bold " + (10 * p.size) + "px monospace";
        ctx.fillText("z", p.x, p.y);
      } else if (p.kind === "note") {
        ctx.fillStyle = "#6fb8ff";
        ctx.fillRect(p.x, p.y, unit, unit * 1.4);
      } else {
        ctx.fillStyle = SPARK;
        ctx.fillRect(p.x, p.y, unit * 0.9, unit * 0.9);
        ctx.fillRect(p.x - unit, p.y, unit, unit * 0.9);
        ctx.fillRect(p.x + unit, p.y, unit, unit * 0.9);
      }
      ctx.globalAlpha = 1;
    }
  }

  function drawPoops() {
    for (var i = 0; i < poops.length; i++) {
      var p = poops[i];
      var u = unit;
      ctx.fillStyle = "#8a5a33";
      ctx.fillRect(p.x - u * 3, p.y - u, u * 6, u);
      ctx.fillRect(p.x - u * 2, p.y - u * 2, u * 4, u);
      ctx.fillRect(p.x - u, p.y - u * 3, u * 2, u);
      ctx.fillStyle = "#b27d4e";
      ctx.fillRect(p.x - u * 2.5, p.y - u * 0.6, u * 5, u * 0.5);
      ctx.fillStyle = "#ffffff";
      ctx.fillRect(p.x - u * 1.5, p.y - u * 1.4, u * 0.6, u * 0.6);
      ctx.fillRect(p.x + u, p.y - u * 1.4, u * 0.6, u * 0.6);
      ctx.fillStyle = "#1a1a1a";
      ctx.fillRect(p.x - u * 1.3, p.y - u * 1.2, u * 0.35, u * 0.35);
      ctx.fillRect(p.x + u * 1.2, p.y - u * 1.2, u * 0.35, u * 0.35);
    }
  }

  function draw() {
    ctx.clearRect(0, 0, window.innerWidth, window.innerHeight);
    var bob = Math.sin(performance.now() / 240) * (pet.state === "walk" ? 1.2 : 0.4);
    var squash = pet.squash > 0 ? 1 + pet.squash : 1;
    var expr = pet.expr;
    if (performance.now() > pet.exprUntil) expr = pet.sleeping ? "sleep" : (pet.chew > performance.now() ? "eat" : "normal");
    ctx.save();
    ctx.translate(pet.x + pw / 2, pet.y + ph + bob * unit * 0.2);
    ctx.scale(pet.look < 0 ? -1 : 1, squash);
    ctx.translate(-pw / 2, -ph);
    drawSprite(0, 0, unit, cfg.palette, expr);
    ctx.restore();
    if (pet.sleeping && Math.random() < 0.02) spawn("zzz", pet.x + pw * 0.7, pet.y);
    if (pet.state === "walk" && Math.random() < 0.05) spawn("spark", pet.x + pw / 2, pet.y + ph);
    drawPoops();
    drawParticles();
    drawBubble();
  }

  function update(dt) {
    var now = performance.now();
    var f = floorY();
    if (pet.drag) {
      pet.vx = 0;
      pet.vy = 0;
    } else {
      pet.vy += 900 * dt;
      pet.y += pet.vy * dt;
      pet.x += pet.vx * dt * 60;
      pet.vx *= 0.92;
      if (pet.y >= f) {
        if (!pet.onGround && pet.vy > 260) {
          spawn("spark", pet.x + pw / 2, f + ph);
          pet.squash = 0.16;
          setExpr("happy", 0.8);
        }
        pet.y = f;
        pet.vy = 0;
        pet.onGround = true;
      } else {
        pet.onGround = false;
      }
      pet.x = Math.max(4, Math.min(maxX(), pet.x));
      if (pet.onGround && pet.walkTarget !== null) {
        var dir = pet.walkTarget > pet.x ? 1 : -1;
        pet.look = dir;
        pet.x += dir * 46 * dt;
        pet.state = "walk";
        if (Math.abs(pet.walkTarget - pet.x) < 4) {
          pet.walkTarget = null;
          pet.state = "idle";
        }
      }
    }
    if (pet.squash > 0) pet.squash = Math.max(0, pet.squash - dt * 0.7);
    if (pet.onGround && !pet.drag) {
      if (now > pet.nextAction && !pet.sleeping) {
        pet.nextAction = now + 2500 + Math.random() * 5000;
        var roll = Math.random();
        if (roll < 0.42) pet.walkTarget = 8 + Math.random() * (maxX() - 8);
        else if (roll < 0.55) hop(260);
        else if (roll < 0.7) say("idle");
        else if (roll < 0.78) spawn("heart", pet.x + pw / 2, pet.y);
      }
      var idleFor = (Date.now() - pet.lastActive) / 1000;
      var sleepAfter = SLEEP_SEC[cfg.sleep] || 0;
      if (!pet.sleeping && sleepAfter && idleFor > sleepAfter) {
        pet.sleeping = true;
        setExpr("sleep", 999);
      }
    }
    if (cfg.eat && !pet.sleeping && pet.onGround && now > pet.nextEat && eaten.length < MAX_EATEN) {
      pet.nextEat = now + 25000 + Math.random() * 35000;
      tryEat();
    }
    if (pet.poopAt && now > pet.poopAt) {
      pet.poopAt = 0;
      doPoop();
    }
    for (var i = particles.length - 1; i >= 0; i--) {
      var p = particles[i];
      p.t += dt;
      p.x += p.vx * dt;
      p.y += (p.vy - 40) * dt;
      p.vy *= 0.98;
      if (p.t > p.life) particles.splice(i, 1);
    }
  }

  function loop(t) {
    if (disposed) return;
    var dt = Math.min(0.05, (t - last) / 1000);
    last = t;
    update(dt);
    draw();
    raf = rafFn(loop);
  }
  raf = rafFn(loop);

  function pickTarget() {
    var nodes = Array.prototype.slice.call(document.querySelectorAll(EAT_TARGETS));
    var out = [];
    for (var i = 0; i < nodes.length; i++) {
      var el = nodes[i];
      if (el.closest("#pluginLayer") || el.closest(".sheet") || el.closest("#onboard")) continue;
      var r = el.getBoundingClientRect();
      if (r.width < 12 || r.height < 12) continue;
      if (r.bottom <= 0 || r.top >= window.innerHeight) continue;
      if (r.top < pet.y + ph) continue;
      out.push({ el: el, rect: r });
    }
    if (!out.length) return null;
    return out[Math.floor(Math.random() * out.length)];
  }

  function snapshotOf(el, rect) {
    var clone = el.cloneNode(true);
    clone.style.position = "fixed";
    clone.style.left = rect.left + "px";
    clone.style.top = rect.top + "px";
    clone.style.width = rect.width + "px";
    clone.style.height = rect.height + "px";
    clone.style.margin = "0";
    clone.style.zIndex = "32";
    clone.style.pointerEvents = "none";
    clone.style.transition = "transform .5s cubic-bezier(.4,-0.4,.6,1.1), opacity .5s linear";
    clone.setAttribute("data-plugin", api.id);
    document.body.appendChild(clone);
    return clone;
  }

  function tryEat() {
    var target = pickTarget();
    if (!target) return;
    var rect = target.rect;
    var clone = snapshotOf(target.el, rect);
    var item = { el: target.el, alpha: target.el.style.opacity, clone: clone, rect: rect };
    eaten.push(item);
    target.el.style.opacity = "0";
    var mx = pet.x + pw / 2, my = pet.y + ph * 0.62;
    pet.look = rect.left + rect.width / 2 > mx ? 1 : -1;
    pet.mouth = performance.now() + 700;
    setExpr("open", 0.7);
    rafFn(function () {
      clone.style.transform = "translate(" + (mx - rect.left - rect.width / 2) + "px," + (my - rect.top - rect.height / 2) + "px) scale(.05) rotate(" + (Math.random() < 0.5 ? -260 : 260) + "deg)";
      clone.style.opacity = "0.2";
    });
    setTimeout(function () {
      if (clone.parentNode) clone.parentNode.removeChild(clone);
      if (disposed) return;
      pet.chew = performance.now() + 1400;
      pet.squash = 0.14;
      setExpr("happy", 1.5);
      say("eat");
      pet.poopMeter++;
      api.store.set("poopMeter", pet.poopMeter);
      if (cfg.poop && pet.poopMeter >= pet.poopNeed) {
        pet.poopMeter = 0;
        pet.poopNeed = 3 + Math.floor(Math.random() * 3);
        api.store.set("poopMeter", 0);
        pet.poopAt = performance.now() + 4000 + Math.random() * 4000;
      }
    }, 540);
  }

  function restore(item) {
    if (!item) return;
    try { item.el.style.opacity = item.alpha || ""; } catch (e) {}
    if (item.clone && item.clone.parentNode) item.clone.parentNode.removeChild(item.clone);
  }

  function spitAll() {
    if (!eaten.length) return;
    var items = eaten.slice();
    eaten = [];
    pet.mouth = performance.now() + 400;
    setExpr("surprised", 0.9);
    hop(240);
    say("spit");
    items.forEach(function (item, i) {
      setTimeout(function () {
        if (disposed) return;
        restore(item);
        var r = item.rect;
        for (var k = 0; k < 3; k++) spawn("spark", r.left + r.width / 2 + (Math.random() - 0.5) * 20, r.top + r.height / 2);
        if (i === 0) setExpr("happy", 1.2);
      }, i * 90);
    });
  }

  function doPoop() {
    if (poops.length >= MAX_POOPS) return;
    var px = pet.x + (pet.look > 0 ? -unit * 4 : pw + unit * 4);
    poops.push({ x: Math.max(unit * 6, Math.min(window.innerWidth - unit * 6, px)), y: floorY() + ph + unit * 3 });
    pet.squash = 0.2;
    setExpr("happy", 1.6);
    say("poop");
  }

  function cleanPoop(p) {
    poops = poops.filter(function (x) { return x !== p; });
    for (var i = 0; i < 4; i++) spawn("spark", p.x + (Math.random() - 0.5) * 18, p.y - 6);
    setExpr("happy", 1.4);
    say("clean");
    api.store.set("poopsCleaned", api.store.get("poopsCleaned", 0) + 1);
  }

  function hit(x, y) {
    return x >= pet.x - 6 && x <= pet.x + pw + 6 && y >= pet.y - 6 && y <= pet.y + ph + 10;
  }

  function pointerPos(e) {
    var r = canvas.getBoundingClientRect();
    return { x: e.clientX - r.left, y: e.clientY - r.top };
  }

  function onDown(e) {
    var p = pointerPos(e);
    pet.lastActive = Date.now();
    for (var i = poops.length - 1; i >= 0; i--) {
      var pp = poops[i];
      if (Math.abs(p.x - pp.x) < unit * 5 && Math.abs(p.y - pp.y) < unit * 6) {
        cleanPoop(pp);
        return;
      }
    }
    if (!hit(p.x, p.y)) {
      if (eaten.length && Math.random() < 0.5) spitAll();
      return;
    }
    if (pet.sleeping) {
      pet.sleeping = false;
      setExpr("surprised", 0.8);
      say("wake");
      pet.lastActive = Date.now();
      return;
    }
    pointer.id = e.pointerId;
    pointer.x = p.x;
    pointer.y = p.y;
    pointer.lastX = p.x;
    pointer.t0 = performance.now();
    pointer.holdStart = performance.now();
    pointer.holdStroke = 0;
    pointer.moved = false;
    pet.drag = true;
    pet.walkTarget = null;
    pet.state = "drag";
    try { canvas.setPointerCapture(e.pointerId); } catch (err) {}
  }

  function onMove(e) {
    if (!pet.drag || pointer.id !== e.pointerId) return;
    var p = pointerPos(e);
    var dx = p.x - pointer.x, dy = p.y - pointer.y;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) pointer.moved = true;
    pet.x = Math.max(0, Math.min(maxX(), pet.x + dx));
    pet.y = Math.max(0, Math.min(window.innerHeight - ph - 4, pet.y + dy));
    if (Math.abs(dx) > 1) pointer.holdStroke++;
    pet.vx = dx * 0.9;
    pet.vy = dy * 0.9;
    pointer.x = p.x;
    pointer.y = p.y;
    if (pointer.moved && performance.now() - pointer.t0 > 260 && pointer.holdStroke === 0) {
      if (Math.random() < 0.06) {
        setExpr("happy", 0.9);
        spawn("heart", pet.x + pw / 2, pet.y);
        say("pet");
      }
    }
  }

  function onUp(e) {
    if (pointer.id !== e.pointerId) return;
    var held = performance.now() - pointer.t0;
    pet.drag = false;
    pointer.id = null;
    if (!pointer.moved && held < 260) {
      pointer.taps++;
      if (pointer.taps >= 2) {
        pointer.taps = 0;
        hop(420);
        setExpr("happy", 1.2);
        say("jump");
        spawn("spark", pet.x + pw / 2, pet.y + ph);
        for (var i = 0; i < 3; i++) spawn("heart", pet.x + pw / 2, pet.y);
        return;
      }
      pointer.tapTimer = setTimeout(function () {
        if (pointer.taps > 0) {
          pointer.taps = 0;
          var annoyed = performance.now() - pet.lastActive < 3000;
          say(annoyed ? "annoyed" : "tap");
          setExpr("surprised", 0.6);
          pet.squash = 0.12;
          if (eaten.length) {
            say("spit");
            spitAll();
          }
        }
      }, 280);
      return;
    }
    if (held > 340 && !pointer.moved && pointer.holdStroke < 2) { say("pet"); setExpr("happy", 1.2); return; }
    var speed = Math.abs(pet.vx) + Math.abs(pet.vy);
    if (speed > 9) {
      pet.vx *= 1.5;
      pet.vy = Math.min(pet.vy * 1.2, -140);
      setExpr("dizzy", 1.6);
      say("throw");
    } else if (pointer.moved) {
      say("drag");
    }
  }

  canvas.addEventListener("pointerdown", onDown);
  canvas.addEventListener("pointermove", onMove);
  canvas.addEventListener("pointerup", onUp);
  canvas.addEventListener("pointercancel", onUp);

  var offMessage = api.onMessage(function (data) {
    pet.lastActive = Date.now();
    var text = String((data && data.text) || "").slice(0, 400);
    if (text.length > 260) { say("essay"); return; }
    for (var i = 0; i < KEYWORDS.length; i++) {
      if (KEYWORDS[i].rx.test(text)) {
        var key = KEYWORDS[i].key;
        say(key);
        setExpr(key === "love" ? "love" : "happy", 1.6);
        for (var k = 0; k < (key === "love" ? 5 : 3); k++) spawn("heart", pet.x + pw / 2, pet.y);
        hop(220);
        return;
      }
    }
  });
  var offAssistant = api.onAssistant(function () {
    pet.lastActive = Date.now();
    say("mail");
    setExpr("surprised", 1.1);
    hop(200);
    spawn("note", pet.x + pw - 4, pet.y);
  });
  var offTheme = api.onTheme(function () {
    if (Math.random() < 0.5) { say("found"); setExpr("happy", 1.2); }
  });
  var offSettings = api.on ? api.on("plugin-settings", function () {}) : function () {};

  api.command({
    name: "clawd",
    description: "Управление питомцем: /clawd pet | jump | sleep | wake | feed | spit | stats",
    run: function (arg) {
      var a = String(arg || "help").trim();
      if (a === "pet") { say("pet"); setExpr("love", 2); for (var i = 0; i < 6; i++) spawn("heart", pet.x + pw / 2, pet.y); return "Clawd доволен ♥"; }
      if (a === "jump") { hop(460); say("jump"); return "Clawd прыгнул!"; }
      if (a === "sleep") { pet.sleeping = true; return "Clawd уснул 😴"; }
      if (a === "wake") { pet.sleeping = false; say("wake"); return "Clawd проснулся!"; }
      if (a === "feed") { tryEat(); return "Clawd пошёл искать, что съесть"; }
      if (a === "spit") { spitAll(); return "Clawd вернул всё назад"; }
      if (a === "stats") {
        return "Съедено: " + api.store.get("eaten", 0) + " · убрано куч: " + api.store.get("poopsCleaned", 0) + " · палитра: " + PALETTES[cfg.palette].name + " · размер: " + (cfg.size + 1);
      }
      return "Команды: pet, jump, sleep, wake, feed, spit, stats";
    }
  });

  api.tool({
    name: "clawd_say",
    description: "Заставить пиксельного питомца Clawd сказать фразу на экране пользователя",
    parameters: { type: "object", properties: { text: { type: "string", description: "текст реплики" } }, required: ["text"] },
    run: function (args) {
      var t = String((args && args.text) || "").slice(0, 90);
      if (!t) return { ok: false, output: "нужен текст" };
      bubble.text = t;
      bubble.until = performance.now() + 3200;
      setExpr("happy", 1.5);
      hop(200);
      return { ok: true, output: "Clawd сказал: " + t };
    }
  });

  api.systemPrompt("На экране пользователя живёт пиксельный питомец Clawd (плагин). Ты можешь попросить его показать реплику инструментом clawd_say — это уместно для лёгкого дружелюбного тона, но не злоупотребляй.");

  function applyCfg() {
    unit = SIZES[cfg.size] || 4;
    pw = W * unit;
    ph = H * unit;
    pet.x = Math.min(pet.x, maxX());
    if (pet.sleeping === false && SLEEP_SEC[cfg.sleep] === 0) pet.sleeping = false;
    if (SLEEP_SEC[cfg.sleep] === 0) { pet.sleeping = false; setExpr("normal", 0); }
  }
  var offCfg = api.on("plugin-settings", function (e) {
    if (e.id !== api.id) return;
    cfg[e.key] = e.value;
    api.store.set(e.key, e.value);
    applyCfg();
    if (e.key === "palette") { say("found"); setExpr("happy", 1.2); }
  });

  setTimeout(function () {
    pet.y = floorY();
    say("hello");
    setExpr("happy", 1.4);
    for (var i = 0; i < 4; i++) spawn("heart", pet.x + pw / 2, pet.y);
  }, 600);

  api.log("питомец на месте, палитра " + PALETTES[cfg.palette].name);

  return function cleanup() {
    disposed = true;
    cancelRaf(raf);
    clearTimeout(pointer.tapTimer);
    window.removeEventListener("resize", resize);
    canvas.removeEventListener("pointerdown", onDown);
    canvas.removeEventListener("pointermove", onMove);
    canvas.removeEventListener("pointerup", onUp);
    canvas.removeEventListener("pointercancel", onUp);
    offMessage(); offAssistant(); offTheme(); offCfg(); offSettings();
    eaten.forEach(function (item) { restore(item); });
    eaten = [];
    if (canvas.parentNode) canvas.parentNode.removeChild(canvas);
  };
}
