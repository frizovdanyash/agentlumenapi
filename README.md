# ✦ Lumen Agent

Android-приложение с AI-агентом: чат с моделями Lumen, работа с файлами и настоящий терминал.
Полностью нативный интерфейс — обычные Android-виджеты, без WebView и HTML.

**Создатель:** [@frizovdanya](https://t.me/frizovdanya) · **Сайт:** https://frizovdanyash.github.io/agentlumenapi/

---

## Что внутри

- **Свой API-ключ.** Ключ Lumen вводит сам пользователь — берётся на [lumen.unionium.org/chat](https://lumen.unionium.org/chat)
  и хранится в настройках приложения. Никуда, кроме API Lumen, не отправляется.
- **5 моделей Lumen:** `mini` (самая дешёвая), `fast`, `max`, `ultra`, `bare` — переключение чипом в шапке.
  Для `mini` автоматически включается текстовый протокол инструментов, для остальных — нативные function calling.
- **Инструменты агента:** `bash`, `read_file`, `write_file`, `edit_file`, `ls`.
  Опасные команды требуют подтверждения перед выполнением.
- **Терминал.** Встроенный шелл-песочница (`ls cat cd echo mkdir touch rm mv cp head tail wc grep find du` и `python3`)
  или настоящий bash в Termux (`pkg`, `python`, `git`). Режим «Авто» сам выбирает, куда отправить команду.
- **Нативный UI.** Чат со стримингом, карточки вызовов инструментов, вкладка терминала с быстрыми клавишами,
  экран настроек — всё на стандартных Android-компонентах.
- **Настройки кастомизации.** 8 акцентов, светлая/тёмная тема, размер шрифта, скругления.
- **Память диалога.** Вся история хранится в приложении, контекст автоматически сжимается — агент не забывает разговор.

## Установка

1. Скачай APK из [Releases](https://github.com/frizovdanyash/agentlumenapi/releases).
2. Разреши установку из неизвестных источников (подпись debug-ключа).
3. Вставь ключ `lum_…` в настройках → «Проверить связь».

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
# → releases/LumenAgent-1.2.0.apk
```

## Структура

```
app/                     Android-приложение
├── java/com/lumen/agent/
│   ├── MainActivity.java      чат, стриминг, карточки инструментов, терминал
│   ├── SettingsActivity.java  ключ, модель, движок команд, тема
│   ├── Agent.java             цикл агента и инструменты
│   ├── ChatClient.java        SSE-клиент API Lumen
│   ├── Termux.java            запуск команд в Termux (RUN_COMMAND)
│   ├── MiniShell.java         встроенная песочница
│   ├── Sess.java              диалоги и сообщения
│   ├── Markdown.java          разметка ответов
│   ├── Theme.java / Palette.java / Views.java  оформление и виджеты
│   └── Store.java             настройки (SharedPreferences)
├── res/                 иконка и тема
├── AndroidManifest.xml
└── build.sh             сборка без Gradle
docs/                    сайт (GitHub Pages)
tools/publish.sh         публикация на GitHub и в Pages
releases/                собранные APK
```

## Лицензия

MIT — см. [LICENSE](LICENSE).
