#!/usr/bin/env bash
set -e

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SDK="${ANDROID_SDK:-$HOME/.cache/android-sdk}"
JDK="${JAVA_HOME:-$HOME/.cache/jdk17}"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="$ROOT/build"
REL="$ROOT/../releases"
NAME="LumenAgent-1.1.0"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

echo "▸ чищу build/"
rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex" "$REL"

echo "▸ aapt2 compile"
"$BT/aapt2" compile --dir "$ROOT/res" -o "$OUT/res.zip"

echo "▸ aapt2 link"
"$BT/aapt2" link \
  -o "$OUT/app-unsigned.apk" \
  -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -R "$OUT/res.zip" \
  -A "$ROOT/assets" \
  --java "$OUT/gen" \
  --min-sdk-version 24 --target-sdk-version 34 \
  --version-code 2 --version-name 1.1.0 \
  --auto-add-overlay

echo "▸ javac"
find "$ROOT/java" -name '*.java' > "$OUT/sources.txt"
find "$OUT/gen" -name '*.java' >> "$OUT/sources.txt"
"$JDK/bin/javac" -encoding UTF-8 --release 8 -nowarn \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" @"$OUT/sources.txt"

echo "▸ d8"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
"$BT/d8" --min-api 24 --lib "$PLATFORM" --output "$OUT/dex" @"$OUT/classes.txt"

echo "▸ упаковка dex"
cp "$OUT/app-unsigned.apk" "$OUT/app.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/app.apk" classes.dex)

echo "▸ zipalign"
"$BT/zipalign" -f -p 4 "$OUT/app.apk" "$OUT/app-aligned.apk"

if [ ! -f "$ROOT/../debug.keystore" ]; then
  echo "▸ генерация debug.keystore"
  "$JDK/bin/keytool" -genkeypair -keystore "$ROOT/../debug.keystore" \
    -storepass android -keypass android -alias lumen -keyalg RSA -keysize 2048 \
    -validity 10000 -dname "CN=Lumen Agent, O=Lumen, C=RU" >/dev/null 2>&1
fi

echo "▸ подпись"
"$BT/apksigner" sign --ks "$ROOT/../debug.keystore" --ks-pass pass:android --key-pass pass:android \
  --out "$REL/$NAME.apk" "$OUT/app-aligned.apk"

echo "▸ проверка"
"$BT/apksigner" verify "$REL/$NAME.apk" && echo "подпись ок"
"$BT/aapt2" dump badging "$REL/$NAME.apk" | grep -E "^package|launchable"

ls -lh "$REL/$NAME.apk"
echo "✔ готово: $REL/$NAME.apk"
