# 🐾 Did They Eat Today? — Android App + Home Screen Widget

A native Android (Kotlin) app to track feedings for up to **4 pets**, with a **home-screen widget** that lets you log a feeding with one tap — without opening the app — and shows a short "last fed" summary for each pet.

## Features

### App
- 2×2 grid of pet cards (editable names, defaults Pet 1–4)
- Large **Feed** button with a confirmation dialog ("Did you just feed …?")
- **Last fed** time shown as `yyyy.MM.dd HH:mm`
- **Fed Today** visual feedback — card turns soft mint green + a "✓ Fed Today" badge
- **View History** dialog listing all feeding timestamps for a pet

### Home Screen Widget
- One row per pet with:
  - the pet's name
  - a short **last-fed label** (e.g. `Today 14:20`, `Yesterday 09:05`, `09.28 18:40`, or `Not fed yet`)
  - a **Feed** button that records a feeding instantly via a broadcast `PendingIntent` — no app launch
- Rows fed today are highlighted in soft green
- The widget and the app share the same data, so changes in one appear in the other

## Data & Persistence
- Stored in `SharedPreferences` as JSON (`FeedingStore`), shared between the Activity and the `AppWidgetProvider`
- Survives app close and device reboot

## Build & Install

This sandbox has no Android SDK and no internet access to download the Android Gradle Plugin, so the APK can't be built here. To build it yourself:

**Option A — Android Studio (easiest)**
1. Open the `android/` folder in Android Studio (Hedgehog or newer).
2. Let it sync Gradle (downloads AGP 8.5.2 + dependencies on first run).
3. Run on an emulator or device (`Run ▶`).
4. Long-press the home screen → **Widgets** → add **Did They Eat Today?**.

**Option B — Command line**
```bash
cd android
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
# install on a connected device:
./gradlew installDebug
```

## Requirements
- Android Studio / Android SDK with API 34
- `minSdk` 24 (Android 7.0+), `targetSdk` 34
- JDK 17 (Android Studio bundles one)

## Project Layout
```
android/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/wrapper/…            # Gradle 8.14.5 wrapper
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/petfeeding/
        │   ├── FeedingStore.kt      # shared JSON persistence
        │   ├── MainActivity.kt      # 4-pet card UI
        │   └── PetFeedingWidget.kt  # AppWidgetProvider + Feed buttons
        └── res/…                    # layouts, drawables, values, widget info
```
