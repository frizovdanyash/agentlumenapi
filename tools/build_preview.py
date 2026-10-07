#!/usr/bin/env python3
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent.parent
ASSETS = ROOT / "app" / "assets"
TARGET = ROOT / "docs" / "preview.html"


def build():
    html = (ASSETS / "index.html").read_text("utf-8")
    css = (ASSETS / "app.css").read_text("utf-8")
    plugins = (ASSETS / "plugins.js").read_text("utf-8")
    builtin = (ASSETS / "builtin-plugins.js").read_text("utf-8")
    app = (ASSETS / "app.js").read_text("utf-8")

    banner = ("<!-- собранный предпросмотр интерфейса: без нативных функций Android, "
              "команды и чат работают в демо-режиме -->\n")
    html = html.replace('<link rel="stylesheet" href="app.css">', "<style>\n" + css + "\n</style>")
    scripts = "<script>\n" + plugins + "\n</script>\n<script>\n" + builtin + "\n</script>\n<script>\n" + app + "\n</script>"
    html = html.replace('<script src="plugins.js"></script>\n<script src="builtin-plugins.js"></script>\n<script src="app.js"></script>', scripts)
    html = re.sub(r"^<!DOCTYPE html>\n", "<!DOCTYPE html>\n" + banner, html)
    TARGET.write_text(html, "utf-8")
    print("собран", TARGET, len(html), "байт")
    return 0


if __name__ == "__main__":
    raise SystemExit(build())
