# Claude Remote

Android client for Claude Code CLI - control your AI coding assistant remotely via Tailscale.

Claude Remote lets you interact with Claude Code CLI from your Android device. The backend runs on your Mac/PC where Claude CLI is installed, while the Android app connects securely via Tailscale. Features include a real-time terminal emulator, project selection, QR code pairing, and full keyboard support for mobile-friendly AI-assisted coding on the go.

## Architecture

```
┌─────────────────┐      Tailscale      ┌──────────────────┐
│   Android App   │◄────────────────────►│   Mac Backend    │
│                 │      Socket.io       │                  │
│  - QR Scanner   │      + API Key       │  - Express.js    │
│  - Projects     │                      │  - Socket.io     │
│  - Sessions     │      REST API        │  - Claude CLI    │
│  - Terminal     │◄────────────────────►│  - node-pty      │
│  - Browser (*)  │      + API Key       │                  │
└─────────────────┘                      └──────────────────┘

(*) Browser preview is a work in progress
```

## Features

- **QR Code Pairing**: Scan the QR code displayed on startup to configure the app instantly
- **Secure Authentication**: API key authentication for all connections
- **Project Discovery**: Automatically discovers projects from `~/.claude/projects/`
- **Real-time Terminal**: Full PTY streaming with ANSI color support
- **Mobile Keyboard**: Custom control bar with arrow keys, Tab, Esc, Ctrl+C
- **Secure Connection**: Uses Tailscale for private networking
- **Browser Preview**: View dev server output *(work in progress)*

## Prerequisites

- **Mac/PC (server)**:
  - Node.js 20+
  - Claude CLI installed and working
  - Tailscale active

- **Android (client)**:
  - Android 8.0+ (API 26)
  - Tailscale active on the same network

## Quick Start

### 1. Start the Backend (Mac/PC)

```bash
cd backend
npm install
npm run dev
```

On startup, a QR code will be displayed:

```
========================================
Scan this QR code with RemoteCli for Claude app:
========================================

▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄
█ ▄▄▄▄▄ █▀ █▀▀██▀ █▀▄█▄▄▄▄▄▄▀▀█ ▄▄▄▄▄ █
...
█▄▄▄▄▄▄▄█▄████▄█▄█▄██▄███▄▄▄███▄▄█▄▄▄▄█

----------------------------------------
Host: your.tailscale.device.address
Port: 3190
API Key: cc193bf6e8687b7257994db205fde9...
----------------------------------------
```

### 2. Install and Configure the App

1. Install the APK on your Android device (see [Building an APK](#building-an-installable-apk))
2. Open the app and go to **Settings**
3. Tap **"Scan QR Code"** and point your camera at the QR code
4. The connection details will be filled automatically
5. Tap **"Test Connection"** to verify
6. Tap **"Save"**

That's it! You're connected.

## Authentication

Claude Remote uses API key authentication to secure all connections:

- **API Key File**: Generated automatically on first backend startup
- **Location**: `backend/.remote-cli-key`
- **Format**: 64-character hex string
- **Security**: File is created with `600` permissions (owner read/write only)

The API key is required for:
- All REST API requests (`X-API-Key` header)
- Socket.io connections (`auth` parameter)

To regenerate the API key, simply delete the `.remote-cli-key` file and restart the backend.

## Backend Setup (Mac/PC)

1. Install dependencies:
   ```bash
   cd backend
   npm install
   ```

2. Configure environment (optional):
   ```bash
   cp .env.example .env
   # Edit .env if needed (default port: 3190)
   ```

3. Start the server:
   ```bash
   npm run dev
   ```

4. The QR code and connection details will be displayed in the terminal

## Android Setup

### Option A: Android Studio (Recommended)

1. Open Android Studio
2. File → Open → select the `android/` folder
3. Wait for Gradle sync (may take a few minutes)
4. Connect your phone via USB with USB Debugging enabled
5. Click Run ▶ (or Shift+F10)

### Option B: Command Line

```bash
cd android
./gradlew installDebug
```

### Enable USB Debugging on your phone

1. Settings → About phone → tap "Build number" 7 times
2. Settings → Developer options → enable "USB Debugging"
3. Connect phone via USB and authorize the PC

### Configure the App

**Option 1: QR Code (Recommended)**
1. In the app, go to Settings
2. Tap "Scan QR Code"
3. Point your camera at the QR code shown in the backend terminal
4. Connection details are filled automatically

**Option 2: Manual Entry**
1. Enter your Mac's Tailscale IP or hostname (e.g., `100.x.x.x` or `your-mac.tailnet-xxx.ts.net`)
2. Enter the port (default: `3190`)
3. Enter the API key (shown in the backend terminal)
4. Tap "Test Connection" - should show "Connected"
5. Tap "Save"

## Building an Installable APK

### Debug APK (simplest, for personal use)

```bash
cd android
./gradlew assembleDebug
```

The APK will be at: `android/app/build/outputs/apk/debug/app-debug.apk`

### Release APK (signed, for distribution)

1. **Create a keystore** (only once):
   ```bash
   keytool -genkey -v -keystore my-release-key.jks -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```

2. **Configure signing** in `android/app/build.gradle.kts`:
   ```kotlin
   android {
       signingConfigs {
           create("release") {
               storeFile = file("my-release-key.jks")
               storePassword = "your-password"
               keyAlias = "my-key-alias"
               keyPassword = "your-password"
           }
       }
       buildTypes {
           release {
               signingConfig = signingConfigs.getByName("release")
               isMinifyEnabled = true
           }
       }
   }
   ```

3. **Build**:
   ```bash
   ./gradlew assembleRelease
   ```

The APK will be at: `android/app/build/outputs/apk/release/app-release.apk`

### Installing the APK

**Via ADB** (with USB debugging enabled):
```bash
adb install app-debug.apk
```

**Manually**: Transfer the APK to your phone and open it. You'll need to enable "Install from unknown sources" in your device settings.

## Usage

1. **Projects**: List of automatically discovered projects
2. **Sessions**: Claude session history per project
3. **Terminal**: Send commands to Claude CLI with full terminal emulation
4. **Browser**: Preview dev server *(work in progress)*

## Terminal Control Bar

The terminal screen includes a control bar with special keys for mobile-friendly interaction:

| Button | Function | Description |
|--------|----------|-------------|
| ← | Arrow Left | Move cursor left in the input line |
| ↑ | Arrow Up | Navigate command history (previous) |
| ↓ | Arrow Down | Navigate command history (next) |
| → | Arrow Right | Move cursor right in the input line |
| Tab | Tab | Trigger autocompletion |
| Esc | Escape | Cancel current operation or exit prompts |
| 🚫 | Ctrl+C | Interrupt/stop the currently running command (tap twice to go back to sessions) |
| ⏎ ENTER | Enter | Submit the current input |

**Note**: Arrow Up/Down, Tab, Esc, and 🚫 are only active when a command is running. Arrow Left/Right and Enter are always available.

## Security

Claude Remote implements several security measures:

- **API Key Authentication**: All requests require a valid API key
- **Rate Limiting**: 100 requests per minute per IP
- **Security Headers**: Helmet.js for HTTP security headers
- **Input Validation**: PTY input and terminal resize validation
- **CORS Disabled**: No browser-based connections allowed
- **Tailscale**: Private network encryption

## Important Notes

- **Tailscale** handles all network security
- Keep your Mac awake (use `caffeinate -d` or Amphetamine app)
- Terminal output is limited to 1000 lines in memory
- One active Claude session at a time
- The `.remote-cli-key` file should not be shared or committed to git

## Development

### Backend
```bash
cd backend
npm run dev      # Development with hot reload
npm run build    # Build TypeScript
npm start        # Production
```

### Android
Open `android/` in Android Studio and use standard build/debug tools.

## Project Structure

```
claude-remote/
├── backend/                 # Node.js/TypeScript Backend
│   ├── src/
│   │   ├── index.ts        # Entry point
│   │   ├── config.ts       # Configuration
│   │   ├── types/          # TypeScript types
│   │   ├── services/       # Business logic (auth, claude, projects)
│   │   └── handlers/       # API and Socket handlers
│   ├── .remote-cli-key     # API key (generated, gitignored)
│   └── package.json
│
├── android/                 # Android Kotlin/Compose App
│   └── app/src/main/java/com/clauderemote/
│       ├── data/           # Model, Repository, Remote, Terminal
│       ├── ui/             # Screens and ViewModels
│       │   └── screens/
│       │       └── settings/  # QR Scanner included
│       ├── service/        # Foreground Service
│       └── di/             # Hilt modules
│
├── IMPROVEMENTS.md         # Security audit documentation
├── LICENSE
└── README.md
```

## Troubleshooting

### "Connection failed" error
- Verify Tailscale is running on both devices
- Check that the host/IP is correct
- Ensure the API key matches

### "Unauthorized" error
- The API key in the app doesn't match the backend
- Re-scan the QR code or manually enter the correct API key

### QR code not scanning
- Ensure camera permission is granted
- Try moving closer/further from the screen
- Check that the QR code is fully visible

### Port already in use
- Another instance may be running
- Kill it with: `lsof -ti :3190 | xargs kill -9`

## License

This project is licensed under a **Non-Commercial License**.

You are free to use, copy, and modify this software for personal, non-commercial purposes only. Commercial use is prohibited without prior written permission.

See [LICENSE](LICENSE) for full details.
