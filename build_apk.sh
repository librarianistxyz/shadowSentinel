#!/usr/bin/env bash
# ==============================================================================
# SHADOW EYES APK BUILD SCRIPT
# Compiles, DEXes, aligns, and signs shadow_eyes.apk directly from CLI
# ==============================================================================

set -e

SDK_DIR="/home/librarianist/Android/Sdk"
BUILD_TOOLS="${SDK_DIR}/build-tools/34.0.0"
PLATFORM_JAR="${SDK_DIR}/platforms/android-34/android.jar"
JAVAC="/home/librarianist/vscode-backup/vscode-extensions/extensions/redhat.java-1.58.0-linux-x64/jre/25.0.4.1-linux-x86_64/bin/javac"
APP_DIR="android/app"
BUILD_DIR="android/build"

echo "[1/6] Cleaning and preparing build workspace..."
rm -rf "$BUILD_DIR" shadow_eyes.apk
mkdir -p "${BUILD_DIR}/compiled_res" "${BUILD_DIR}/gen" "${BUILD_DIR}/obj" "${BUILD_DIR}/apk"

# Ensure assets are up-to-date from public
mkdir -p "${APP_DIR}/src/main/assets"
cp -r public/* "${APP_DIR}/src/main/assets/"

echo "[2/6] Compiling Android resources with AAPT2..."
"${BUILD_TOOLS}/aapt2" compile --dir "${APP_DIR}/src/main/res" -o "${BUILD_DIR}/compiled_res/res.zip"

"${BUILD_TOOLS}/aapt2" link \
    -I "$PLATFORM_JAR" \
    --manifest "${APP_DIR}/src/main/AndroidManifest.xml" \
    --java "${BUILD_DIR}/gen" \
    -o "${BUILD_DIR}/apk/unaligned.apk" \
    -A "${APP_DIR}/src/main/assets" \
    "${BUILD_DIR}/compiled_res/res.zip" \
    --auto-add-overlay

echo "[3/6] Compiling Java source files..."
# Find all java files including generated R.java
JAVA_FILES=$(find "${APP_DIR}/src/main/java" "${BUILD_DIR}/gen" -name "*.java")

"$JAVAC" \
    -cp "$PLATFORM_JAR" \
    -d "${BUILD_DIR}/obj" \
    --release 8 \
    $JAVA_FILES

echo "[4/6] Converting Java bytecode to Dalvik Executable (classes.dex) with D8..."
CLASS_FILES=$(find "${BUILD_DIR}/obj" -name "*.class")
"${BUILD_TOOLS}/d8" \
    --lib "$PLATFORM_JAR" \
    --output "${BUILD_DIR}/apk" \
    $CLASS_FILES

# Add classes.dex into unaligned.apk
cd "${BUILD_DIR}/apk"
zip -u unaligned.apk classes.dex
cd - > /dev/null

echo "[5/6] Aligning APK with zipalign..."
"${BUILD_TOOLS}/zipalign" -f -p 4 "${BUILD_DIR}/apk/unaligned.apk" "${BUILD_DIR}/apk/aligned.apk"

echo "[6/6] Generating keystore and signing APK with apksigner..."
KEYSTORE="android/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Shadow,OU=Eyes,O=Sentinel,C=US" 2>/dev/null
fi

"${BUILD_TOOLS}/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-key-alias androiddebugkey \
    --ks-pass pass:android \
    --key-pass pass:android \
    --out "shadow_eyes.apk" \
    "${BUILD_DIR}/apk/aligned.apk"

echo ""
echo "=================================================================="
echo " [✓] SUCCESS: shadow_eyes.apk generated!"
ls -lh shadow_eyes.apk
echo "=================================================================="
