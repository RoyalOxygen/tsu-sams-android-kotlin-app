# TSU SAMS

Android WebView app for the Taraba State University Student Attendance Management System.

Portal: `https://sams.tsuniversity.edu.ng/`

## Features

- Splash screen with TSU logo and **TSU SAMS**
- Full-screen WebView of the SAMS attendance portal
- Camera access for facial biometric attendance
- Image upload from camera or gallery
- Native **No Internet Connection** screen
- Native **page error** screen with retry and go-home
- Pull to refresh
- Hardware back button navigates WebView history

## Open in Android Studio

1. Open Android Studio
2. Select **Open** and choose this folder
3. Wait for Gradle sync
4. Connect a device or start an emulator (API 24+)
5. Run the `app` configuration

## Build from command line

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Related apps

- **TSU SAMS** (this app) — lecture attendance with facial biometrics and geofencing
- **TSU Exam Capture** — exam biometric verification (separate build)

Both share student biometric verification on the same SAMS backend.

## Package

`ng.edu.tsu.examcapture`
