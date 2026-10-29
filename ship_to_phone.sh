#!/usr/bin/env bash
# ==============================================================================
# SHADOW SENTINEL: NATIVE APK PROVISION & SHIPPER
# Builds, installs, grants godmode hardware permissions, and launches
# shadow_eyes.apk directly onto your Samsung Galaxy S9+ over USB.
# ==============================================================================

set -e

COLOR_CYAN='\033[0;36m'
COLOR_GREEN='\033[0;32m'
COLOR_RED='\033[0;31m'
COLOR_YELLOW='\033[1;33m'
COLOR_RESET='\033[0m'

echo -e "${COLOR_CYAN}"
cat << "EOF"
  ____  _               _                 _____                  
 / ___|| |__   __ _  __| | _____      __ | ____|   _  ___  ___  
 \___ \| '_ \ / _` |/ _` |/ _ \ \ /\ / / |  _|  | | | |/ _ \/ __| 
  ___) | | | | (_| | (_| | (_) \ V  V /  | |___ | |_| |  __/\__ \ 
 |____/|_| |_|\__,_|\__,_|\___/ \_/\_/   |_____| \__, |\___||___/ 
                                                 |___/            
          [ NATIVE ANDROID CONTROLLER // 0-NIT SENTINEL ]
EOF
echo -e "${COLOR_RESET}"

# 1. Check ADB
if ! command -v adb &> /dev/null; then
    echo -e "${COLOR_RED}[!] adb is not found in PATH.${COLOR_RESET}"
    exit 1
fi

# 2. Wait for S9+ Device
echo -e "${COLOR_YELLOW}[1/4] Detecting Samsung Galaxy S9+ over USB...${COLOR_RESET}"
DEVICE_COUNT=$(adb devices | grep -v "List" | grep "device" | wc -l)

if [ "$DEVICE_COUNT" -eq 0 ]; then
    echo -e "${COLOR_YELLOW}[*] Waiting for device. Connect your S9+ via USB with USB Debugging enabled.${COLOR_RESET}"
    adb wait-for-device
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
DEVICE_NAME=$(adb shell getprop ro.product.name 2>/dev/null | tr -d '\r')
echo -e "${COLOR_GREEN}[✓] Device Connected: ${DEVICE_MODEL} (${DEVICE_NAME})${COLOR_RESET}"

# 3. Build APK
echo ""
echo -e "${COLOR_YELLOW}[2/4] Building latest shadow_eyes.apk binary...${COLOR_RESET}"
./build_apk.sh

# 4. Install APK over USB Cable
echo ""
echo -e "${COLOR_YELLOW}[3/4] Installing shadow_eyes.apk over USB...${COLOR_RESET}"
if ! adb install -r -d shadow_eyes.apk; then
    echo -e "${COLOR_YELLOW}[*] Updating package installation (clean reinstall)...${COLOR_RESET}"
    adb uninstall com.shadow.eyes || true
    adb install -r -d shadow_eyes.apk
fi

# 5. Grant Godmode Hardware Permissions via ADB (No popups!)
echo -e "[*] Granting Camera, Mic, and Storage permissions..."
adb shell pm grant com.shadow.eyes android.permission.CAMERA 2>/dev/null || true
adb shell pm grant com.shadow.eyes android.permission.RECORD_AUDIO 2>/dev/null || true
adb shell pm grant com.shadow.eyes android.permission.WRITE_EXTERNAL_STORAGE 2>/dev/null || true
adb shell pm grant com.shadow.eyes android.permission.READ_EXTERNAL_STORAGE 2>/dev/null || true

# Hardware Display Optimizations
echo -e "[*] Locking screen awake on USB power..."
adb shell svc power stayon usb

echo -e "[*] Enforcing true 0-nit edge-to-edge immersive black mode..."
adb shell settings put global policy_control immersive.full=com.shadow.eyes

# Wake up and unlock device
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard 2>/dev/null || true

# 6. Launch the Native Sentinel
echo ""
echo -e "${COLOR_YELLOW}[4/4] Launching Shadow Eyes Native Sentinel...${COLOR_RESET}"
adb shell am start -n com.shadow.eyes/.MainActivity --activity-clear-task

echo ""
echo -e "${COLOR_GREEN}================================================================${COLOR_RESET}"
echo -e "${COLOR_GREEN}[✓] SHADOW SENTINEL DEPLOYED & ARMED!${COLOR_RESET}"
echo -e "    Package        : ${COLOR_CYAN}com.shadow.eyes${COLOR_RESET}"
echo -e "    OLED Display   : ${COLOR_GREEN}0-Nit True Black (Centered Menacing Eyes)${COLOR_RESET}"
echo -e "    Hardware Sensor: ${COLOR_CYAN}Camera2 Face Detection Armed${COLOR_RESET}"
echo -e "    Action Sequence: ${COLOR_GREEN}When Face Spotted -> 2 Photos Burst -> 1080p Video${COLOR_RESET}"
echo -e "    Storage Vault  : ${COLOR_CYAN}/sdcard/DCIM/Shadow/${COLOR_RESET}"
echo -e "${COLOR_GREEN}================================================================${COLOR_RESET}"
