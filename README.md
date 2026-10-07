# ✦ Lumen Agent

Android-приложение с AI-агентом: чат с моделями Lumen, работа с файлами, терминал и система плагинов.

**Создатель:** [@frizovdanya](https://t.me/frizovdanya) · **Сайт и документация:** https://frizovdanyash.github.io/agentlumenapi/

---

## Что внутри

- **Свой API-ключ.** Ключ Lumen вводит сам пользователь — берётся на [lumen.unionium.org/chat](https://lumen.unionium.org/chat)
  и хранится в `localStorage`. Никуда, кроме API Lumen, не отправляется.
- **5 моделей Lumen:** `mini` (самая дешёвая), `fast`, `max`, `ultra`, `bare` — переключение чипом в шапке.
  Для `mini` автоматически включается текстовый протокол инструментов, для остальных — нативные function calling.
- **Инструменты агента:** `bash`, `read_file`, `write_file`, `edit_file`, `ls` плюс инструменты плагинов.
  Опасные команды подсвечиваются и требуют подтверждения.
- **Терминал.** Встроенный шелл-песочница (`ls cat cd echo mkdir touch rm mv cp head tail wc grep find du`)
  или настоящий bash в Termux (`pkg`, `python`, `git`). Режим «Авто» сам выбирает, куда отправить команду.
- **Плагины.** Файл `.plugin` = JSON-манифест + JS-код: команды `/команда`, инструменты для модели, виджеты
  поверх интерфейса, свои настройки, вклад в системный промпт. В комплекте **Clawd** (пиксельный питомец)
  и **Быстрые заметки**.
- **Настройки кастомизации.** 8 акцентов, 6 фонов, светлая/тёмная тема, размер шрифта, скругления, плотность, аватары.
- **Память диалога.** Вся история в localStorage, контекст автоматически сжимается — агент не забывает разговор.

## Установка

1. Скачай APK из [Releases](https://github.com/frizovdanyash/agentlumenapi/releases).
2. Разреши установку из неизвестных источников (подпись debug-ключа).
3. Вставь ключ `lum_…` на экране приветствия → «Сохранить и проверить».

Требуется Android 7.0+ (minSdk 24, targetSdk 34).

### Настоящий bash через Termux

```bash
echo allow-external-apps=true >> ~/.termux/termux.properties
pkg install python git fastfetch
```

Перезапусти Termux, затем в приложении: Настройки → Движок команд → Termux (или «Авто»).

## Сборка APK

Нужны JDK 17 и Android SDK build-tools 34 (`platforms;android-34`, `build-tools;34.0.0`).
Скрипт ищет их в `~/.cache/android-sdk` и `~/.cache/jdk17` (или укажи `ANDROID_SDK` / `JAVA_HOME`).

```bash
bash app/build.sh
# → releases/LumenAgent-1.1.0.apk
```

Пересборка встроенных плагинов (после правки `plugins/src/*`):

```bash
python3 tools/pack_plugins.py
```

Предпросмотр интерфейса в браузере (один HTML-файл, без нативных функций):

```bash
python3 tools/build_preview.py
# → docs/preview.html
```

## Структура

```
app/                     Android-приложение
├── assets/              интерфейс (WebView)
│   ├── index.html       разметка
│   ├── app.css          оформление и темы
│   ├── app.js           логика: чат, инструменты, настройки, терминал
│   ├── plugins.js       движок плагинов
│   └── builtin-plugins.js  собранный набор встроенных плагинов
├── java/com/lumen/agent/
│   ├── MainActivity.java   WebView, выбор файла плагина, кнопка «назад»
│   ├── Bridge.java         мост JS↔Android: чат (SSE), Termux, проверка ключа
│   └── MiniShell.java      встроенный шелл и инструменты агента
├── res/                 иконка и тема
├── AndroidManifest.xml
└── build.sh             сборка без Gradle
plugins/                 готовые .plugin и исходники (src/)
docs/                    сайт и документация (GitHub Pages)
tools/                   pack_plugins.py, build_preview.py
releases/                собранные APK
```

## Формат плагина

```json
{
  "id": "hello",
  "name": "Привет",
  "version": "1.0",
  "author": "t.me/username",
  "icon": "👋",
  "settings": [],
  "code": "function onLoad(api) { api.command({ name: 'hi', description: 'поздороваться', run: function () { return 'Привет!'; } }); }"
}
```

Полный справочник API — в [документации](https://frizovdanyash.github.io/agentlumenapi/plugins.html).

## Лицензия

MIT — см. [LICENSE](LICENSE).
