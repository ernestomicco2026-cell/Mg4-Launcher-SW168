#!/usr/bin/env bash
# Compila MG4 Launcher (SWI68) senza Gradle: aapt2 -> javac -> D8 -> firma (v1+v2).
# Uso: ./build.sh            (versione da version.properties)
#      DEMO=1 ./build.sh     versione demo: dati simulati, pacchetto com.mg4.launcher.demo, non diventa la home
set -euo pipefail

ROOT="$(cd "$(dirname "$0")" && pwd)"
T="$ROOT/tools"
APP="$ROOT/app"
OUT="$ROOT/build"
ANDROID_JAR="$T/android.jar"
KEYSTORE="${KEYSTORE:-$ROOT/keys/platform.p12}"
KEY_PASS="${KEY_PASS:-android}"
KEY_ALIAS="${KEY_ALIAS:-platform}"

source "$ROOT/version.properties"   # VERSION_CODE, VERSION_NAME
DEMO="${DEMO:-0}"
OUT_NAME="MG4Launcher-$VERSION_NAME.apk"
[ "$DEMO" = "1" ] && OUT_NAME="MG4Launcher-$VERSION_NAME-DEMO.apk"

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/tools"

# demo: sorgenti con Flavor.DEMO = true; manifest senza HOME (si apre dall'elenco app) e con nome "DEMO"
SRC="$APP/java"
MANIFEST="$APP/AndroidManifest.xml"
RENAME=()
if [ "$DEMO" = "1" ]; then
  echo "==> versione DEMO"
  cp -r "$APP/java" "$OUT/src"
  sed -i 's/DEMO = false/DEMO = true/' "$OUT/src/com/mg4/launcher/Flavor.java"
  SRC="$OUT/src"
  sed -e '/android.intent.category.HOME/d' -e '/android.intent.category.DEFAULT/d' \
      -e 's#android:label="@string/app_name"#android:label="MG4 Launcher DEMO"#' "$APP/AndroidManifest.xml" > "$OUT/AndroidManifest.xml"
  MANIFEST="$OUT/AndroidManifest.xml"
  RENAME=(--rename-manifest-package com.mg4.launcher.demo)
fi

echo "==> risorse (aapt2)"
"$T/aapt2" compile --dir "$APP/res" -o "$OUT/res/compiled.zip"
"$T/aapt2" link \
  -I "$ANDROID_JAR" \
  --manifest "$MANIFEST" "${RENAME[@]}" \
  --min-sdk-version 26 --target-sdk-version 28 \
  --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" \
  --java "$OUT/gen" \
  -o "$OUT/base.apk" \
  "$OUT/res/compiled.zip"

echo "==> java (javac, bytecode 8)"
find "$SRC" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
# API Java 8 dal JDK (lambda), API Android da android.jar
if ! javac -nowarn -encoding UTF-8 --release 8 -classpath "$ANDROID_JAR" \
     -d "$OUT/classes" @"$OUT/sources.txt" > "$OUT/javac.log" 2>&1; then
  grep -v 'Picked up JAVA_TOOL_OPTIONS' "$OUT/javac.log"
  echo "compilazione fallita"; exit 1
fi
grep -v -E '^(Picked up JAVA_TOOL_OPTIONS|Note: )' "$OUT/javac.log" || true

echo "==> dex (D8)"
find "$OUT/classes" -name '*.class' > "$OUT/classes.txt"
java -cp "$T/d8.jar" com.android.tools.r8.D8 --release --min-api 26 \
  --lib "$ANDROID_JAR" --output "$OUT/dex" @"$OUT/classes.txt" 2>&1 | grep -v 'Picked up JAVA_TOOL_OPTIONS' || true
test -f "$OUT/dex/classes.dex" || { echo "D8 fallito"; exit 1; }

echo "==> pacchetto"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -X "$OUT/unsigned.apk" classes*.dex)

echo "==> firma"
javac -nowarn -d "$OUT/tools" -cp "$T/d8.jar" "$T/src/Sign.java" 2>&1 | grep -v 'Picked up' || true
java -cp "$OUT/tools:$T/d8.jar" Sign "$KEYSTORE" "$KEY_PASS" "$KEY_ALIAS" \
  "$OUT/unsigned.apk" "$OUT/$OUT_NAME" 2>&1 | grep -v 'Picked up' || true
test -f "$OUT/$OUT_NAME"
ls -la "$OUT/$OUT_NAME"
