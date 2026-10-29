# Shadow Sentinel 👁️
> **Native Android Hardware Sentinel & Controller with Centered AMOLED Eyes.**  
> Built for Samsung Galaxy S9+ (Super AMOLED 0-Nit True Black) & Cloudflare Edge.

---

## What It Does

This is **not a wallpaper and not a browser tab**. It is a **native Android app binary (`shadow_eyes.apk`)** with direct hardware-level access to the camera, microphone, and storage.

1. **The Visual Vessel (0-Nit Obsidian AMOLED)**:
   - Pure `#000000` true black display (diodes off).
   - The exact menacing eyes from `eyes.png` locked **dead-center** on the screen.
   - Natural organic blinking and autonomous gaze direction shifts.
   - Real-time gaze tracking (pupils follow touch and phone movement).

2. **The Hardware Surveillance Agent (Auto Face-Triggered Action)**:
   - Uses the Android **Camera2 API** with hardware **Face Detection** active in the background.
   - **Autonomous Trigger Sequence**:
     1. The moment a human face appears in front of the phone:
     2. The eyes narrow into calculating slits.
     3. **Burst Phase**: Snaps **2 high-resolution photos** in quick succession.
     4. **Video Phase**: Switches instantly to **1080p video recording with audio**.
     5. Archives all captured evidence into `/sdcard/DCIM/Shadow/`.
     6. Returns to silent sentinel watch.

3. **Cloudflare Edge Brain**:
   - Integrates with your Cloudflare Worker / custom domain for AI thought reflection and remote command telemetry.

---

## 🚀 How to Ship to Your S9+ via USB

### 1. Connect Phone via USB
1. Plug your Samsung S9+ into your computer with a USB cable.
2. Ensure **USB Debugging** is enabled (`Settings -> Developer Options -> USB Debugging`).

### 2. Ship in 1 Command
Run:
```bash
npm run ship
```
*(or `./ship_to_phone.sh`)*

### What the USB Cable Does Automatically:
1. Pushes and installs [`shadow_eyes.apk`](file:///home/librarianist/Documents/SCRIPTS/samsung%20s9/shadow_eyes.apk) via `adb install -r`.
2. Pre-grants all dangerous hardware permissions (`CAMERA`, `RECORD_AUDIO`, `WRITE_EXTERNAL_STORAGE`) over ADB so there are **zero prompt popups**.
3. Enforces true 0-nit edge-to-edge immersive black kiosk mode.
4. Locks the screen awake while plugged into power.
5. Launches the app directly on your S9+.

---

## 🛠️ Rebuilding the APK
If you ever modify the code or assets:
```bash
./build_apk.sh
```
This re-compiles, DEXes with D8, aligns, and signs `shadow_eyes.apk`.
