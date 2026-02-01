# Claude Remote

Android client for Claude Code CLI - control your AI coding assistant remotely via Tailscale.

Claude Remote lets you interact with Claude Code CLI from your Android device. The backend runs on your Mac/PC where Claude CLI is installed, while the Android app connects securely via Tailscale. Features include a real-time terminal emulator, project selection, and full keyboard support for mobile-friendly AI-assisted coding on the go.

## Architecture

```
┌─────────────────┐      Tailscale      ┌──────────────────┐
│   Android App   │◄────────────────────►│   Mac Backend    │
│                 │      Socket.io       │                  │
│  - Projects     │                      │  - Express.js    │
│  - Sessions     │      REST API        │  - Socket.io     │
│  - Terminal     │◄────────────────────►│  - Claude CLI    │
│  - Browser (*)  │                      │                  │
└─────────────────┘                      └──────────────────┘

(*) Browser preview is a work in progress
```

## Features

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

4. Note your Tailscale hostname (e.g., `mac.tailnet-xxx.ts.net`)

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

On first launch:
1. Enter your Mac's Tailscale IP or hostname (e.g., `100.x.x.x` or `your-mac.tailnet-xxx.ts.net`)
2. Enter the port (default: `3190`)
3. Tap "Test Connection" - should show "Connected"
4. Tap "Save"

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

## Important Notes

- **Tailscale** handles all network security
- Keep your Mac awake (use `caffeinate -d` or Amphetamine app)
- Terminal output is limited to 1000 lines in memory
- One active Claude session at a time

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
│   │   ├── services/       # Business logic
│   │   └── handlers/       # API and Socket handlers
│   └── package.json
│
├── android/                 # Android Kotlin/Compose App
│   └── app/src/main/java/com/clauderemote/
│       ├── data/           # Model, Repository, Remote
│       ├── ui/             # Screens and ViewModels
│       ├── service/        # Foreground Service
│       └── di/             # Hilt modules
│
├── LICENSE
└── README.md
```

## License

This project is licensed under a **Non-Commercial License**.

You are free to use, copy, and modify this software for personal, non-commercial purposes only. Commercial use is prohibited without prior written permission.

See [LICENSE](LICENSE) for full details.
