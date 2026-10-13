# FreeFloat Workspace

A Kotlin + Jetpack Compose Android 13+ floating workspace inspired by modern Android window behavior.

## What is included

- Onboarding that explains the overlay permission and foreground notification.
- Right-edge swipe sidebar plus a visible fallback button.
- Multiple movable floating panels with rounded shells, focus state, close/minimize controls, resize handles, transparency toggle, and panel swapping.
- Built-in Notes, To-do, Calculator, Clock, Calendar preview, Music mock, System information, and Web panel.
- Minimized edge bubbles that restore panels.
- Settings dialog for theme, opacity, and gesture guidance.
- `FloatingWindowService` using `TYPE_APPLICATION_OVERLAY`, a persistent notification, and safe cleanup.
- DataStore repository scaffold for workspace preferences.

## Important limitation

This is a normal APK. Android does not allow it to turn arbitrary third-party applications into embedded, system-level resizable windows. FreeFloat only provides windows for its own panels; external apps remain separate and can be opened normally by the user. It does not capture screens, read other apps, simulate taps, or use AccessibilityService.

## Build

1. Open the project in Android Studio Ladybug or newer.
2. Use JDK 17 and Android SDK 35 (the app supports Android 13/API 33 and newer).
3. Run `./gradlew assembleDebug`.
4. Install `app/build/outputs/apk/debug/app-debug.apk` on a device.

The debug build is unsigned and intended for local testing. The release block is configured for a standard Android Studio signing setup.

## Permissions

The only functional special permission is **Display over other apps**. It is requested only after the onboarding explanation and must be granted manually in system settings. The foreground service notification remains visible while the workspace service is active. On Android 13+, notification permission may also be presented by the host app if a production build adds the runtime request.

## Gestures

- Swipe from the extreme right edge toward the center: open sidebar.
- Use the bottom-right sidebar button when edge navigation conflicts.
- Drag a window body/header to move it; movement is clamped to usable bounds.
- Drag the bottom-right corner to resize; minimum and maximum sizes are enforced.
- Tap the three-dot menu for full screen, minimize, swap panel, transparency, center, and close.
- Tap a bubble to restore a minimized panel.

## Manual test checklist

- Deny overlay permission: onboarding remains usable and no crash occurs.
- Grant permission and tap Start workspace: notification and small overlay service affordance appear.
- Open at least three panels, move and resize them, minimize/restore, swap content, and close.
- Try the right-edge gesture in gesture navigation and three-button navigation.
- Toggle dark theme and adjust default opacity in Settings.
- Rotate the device and verify the activity recreates without a crash.
