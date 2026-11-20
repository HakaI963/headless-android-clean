#!/usr/bin/env bash
# Clean, Java-only build for Headless Android. No Gradle, no Kotlin.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
BUILD="$ROOT/build/local"
DEPS="$BUILD/deps"
JARS="$DEPS/jars"
GEN="$BUILD/gen"
CLASSES="$BUILD/classes"
DEX="$BUILD/dex"

JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-arm64}"
if [ ! -x "$JAVA_HOME/bin/javac" ]; then
  for c in /usr/lib/jvm/java-17-openjdk-arm64 /usr/lib/jvm/java-25-openjdk-arm64; do
    [ -x "$c/bin/javac" ] && JAVA_HOME="$c" && break
  done
fi
JAVA="$JAVA_HOME/bin/java"
JAVAC="$JAVA_HOME/bin/javac"
KEYTOOL="$JAVA_HOME/bin/keytool"

ANDROID_JAR="${ANDROID_JAR:-/root/workspace/.android-sdk/platforms/android-36/android.jar}"
BUILD_TOOLS="${BUILD_TOOLS:-/root/workspace/.android-sdk/build-tools/35.0.0}"
SYSROOT="${SYSROOT:-/tmp/x86root}"

case "$(uname -m)" in
  x86_64|amd64) native() { "$@"; } ;;
  *) native() { QEMU_LD_PREFIX="$SYSROOT" qemu-x86_64 -L "$SYSROOT" "$@"; } ;;
esac

rm -rf "$BUILD"; mkdir -p "$JARS" "$GEN" "$CLASSES" "$DEX"

for artifact in api provider aidl shared; do
  aar="$DEPS/shizuku-$artifact.aar"
  [ -f "$aar" ] || curl -fsSL -o "$aar" \
    "https://repo1.maven.org/maven2/dev/rikka/shizuku/$artifact/13.1.5/$artifact-13.1.5.aar"
  [ -f "$JARS/shizuku-$artifact.jar" ] || unzip -p "$aar" classes.jar > "$JARS/shizuku-$artifact.jar"
done
ANNOTATION="$DEPS/annotation.jar"
[ -f "$ANNOTATION" ] || curl -fsSL -o "$ANNOTATION" \
  "https://dl.google.com/dl/android/maven2/androidx/annotation/annotation/1.3.0/annotation-1.3.0.jar"

echo "== aapt2"
native "$BUILD_TOOLS/aapt2" compile --dir "$APP/src/main/res" -o "$BUILD/res.zip"
sed '0,/<manifest /s//<manifest package="com.headless.android" /' \
  "$APP/src/main/AndroidManifest.xml" > "$BUILD/AndroidManifest.xml"
native "$BUILD_TOOLS/aapt2" link \
  -o "$BUILD/unsigned.apk" -I "$ANDROID_JAR" \
  --manifest "$BUILD/AndroidManifest.xml" --java "$GEN" \
  --custom-package com.headless.android \
  --min-sdk-version 29 --target-sdk-version 36 \
  --version-code 1 --version-name 1.0.0 "$BUILD/res.zip"

echo "== javac"
CP="$ANDROID_JAR:$ANNOTATION"
for jar in "$JARS"/*.jar; do CP="$CP:$jar"; done
find "$APP/src/main/java" "$GEN" -name '*.java' > "$BUILD/sources.txt"
"$JAVAC" -source 17 -target 17 -nowarn -Xlint:-options -classpath "$CP" \
  -d "$CLASSES" @"$BUILD/sources.txt"

echo "== d8"
find "$CLASSES" -name '*.class' > "$BUILD/classes.txt"
"$JAVA" -Xmx3g -cp "$BUILD_TOOLS/lib/d8.jar" com.android.tools.r8.D8 \
  --release --lib "$ANDROID_JAR" --min-api 29 --output "$DEX" \
  @"$BUILD/classes.txt" "$JARS/shizuku-api.jar" "$JARS/shizuku-provider.jar" \
  "$JARS/shizuku-aidl.jar" "$JARS/shizuku-shared.jar"

echo "== package"
cp "$BUILD/unsigned.apk" "$BUILD/dexed.apk"
(cd "$DEX" && "$JAVA_HOME/bin/jar" uf "$BUILD/dexed.apk" classes.dex)
native "$BUILD_TOOLS/zipalign" -f 4 "$BUILD/dexed.apk" "$BUILD/aligned.apk"
KEYSTORE="${KEYSTORE:-$ROOT/local/debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  "$KEYTOOL" -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$JAVA" -jar "$BUILD_TOOLS/lib/apksigner.jar" sign \
  --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --ks-key-alias androiddebugkey --out "$BUILD/headless-android-clean.apk" \
  "$BUILD/aligned.apk"
"$JAVA" -jar "$BUILD_TOOLS/lib/apksigner.jar" verify "$BUILD/headless-android-clean.apk"
echo "APK: $BUILD/headless-android-clean.apk"
