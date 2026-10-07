#!/usr/bin/env bash
set -e
REPO="frizovdanyash/agentlumenapi"
TOKEN="${GH_TOKEN:?нужен GH_TOKEN}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
API="https://api.github.com"
AUTH="Authorization: token $TOKEN"

cd "$ROOT"

if [ ! -d .git ]; then
  git init -q
  git branch -M main
fi
git remote remove origin 2>/dev/null || true
git remote add origin "https://x-access-token:$TOKEN@github.com/$REPO.git"
git add -A
git -c user.name=frizovdanyash -c user.email=frizovdanyash@users.noreply.github.com commit -q -m "Lumen Agent 1.1.0: GUI, плагины, документация" || true
git push -u origin main --force

echo "▸ включаю GitHub Pages (папка /docs)"
curl -s -X POST -H "$AUTH" -H "Accept: application/vnd.github+json" \
  "$API/repos/$REPO/pages" \
  -d '{"source":{"branch":"main","path":"/docs"},"build_type":"legacy"}' \
  | head -c 400
echo
curl -s -X PUT -H "$AUTH" -H "Accept: application/vnd.github+json" \
  "$API/repos/$REPO/pages" \
  -d '{"source":{"branch":"main","path":"/docs"},"build_type":"legacy"}' \
  | head -c 400
echo

echo "▸ создаю релиз v1.1.0"
REL=$(curl -s -X POST -H "$AUTH" -H "Accept: application/vnd.github+json" \
  "$API/repos/$REPO/releases" \
  -d '{"tag_name":"v1.1.0","name":"Lumen Agent 1.1.0","body":"GUI-приложение: чат с моделями Lumen, инструменты, терминал (встроенный шелл или Termux), система плагинов.\n\nВ комплекте плагины Clawd и Быстрые заметки. Ключ Lumen вводит сам пользователь, хранится в localStorage.\n\nУстановка: скачать APK, разрешить установку из неизвестных источников, вставить ключ."}')
UPLOAD=$(echo "$REL" | python3 -c "import json,sys;print(json.load(sys.stdin).get('upload_url','').split('{')[0])")
echo "$REL" | python3 -c "import json,sys;d=json.load(sys.stdin);print('  релиз:', d.get('html_url') or d)"
if [ -n "$UPLOAD" ]; then
  curl -s -X POST -H "$AUTH" -H "Content-Type: application/vnd.android.package-archive" \
    --data-binary @"$ROOT/releases/LumenAgent-1.1.0.apk" \
    "$UPLOAD?name=LumenAgent-1.1.0.apk" | head -c 300
  echo
fi

echo "▸ проверяю Pages"
curl -s -H "$AUTH" "$API/repos/$REPO/pages" | head -c 500
echo
echo "✔ готово: https://frizovdanyash.github.io/agentlumenapi/"
