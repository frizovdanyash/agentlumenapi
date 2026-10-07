#!/usr/bin/env python3
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "plugins" / "src"
OUT = ROOT / "plugins"
ASSETS = ROOT / "app" / "assets"


def pack():
    metas = sorted(SRC.glob("*.meta.json"))
    if not metas:
        print("нет манифестов в plugins/src")
        return 1
    builtin = []
    for meta_path in metas:
        meta = json.loads(meta_path.read_text("utf-8"))
        code_path = SRC / meta.pop("code_file")
        if not code_path.exists():
            print("нет файла кода:", code_path)
            return 1
        meta["code"] = code_path.read_text("utf-8")
        pretty = json.dumps(meta, ensure_ascii=False, indent=2)
        target = OUT / (meta["id"] + ".plugin")
        target.write_text(pretty, "utf-8")
        builtin.append(meta)
        print("упакован %-12s %6d байт -> %s" % (meta["id"], len(pretty), target.name))
    js = "window.BUILTIN_PLUGINS = " + json.dumps(builtin, ensure_ascii=False, indent=1) + ";\n"
    (ASSETS / "builtin-plugins.js").write_text(js, "utf-8")
    print("собран app/assets/builtin-plugins.js:", len(js), "байт")
    return 0


if __name__ == "__main__":
    sys.exit(pack())
