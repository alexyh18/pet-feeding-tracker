# 🦎 피딩계산기 (Feeding Calculator) — Android App + Home Screen Widgets

A native Android (Kotlin) app to track pet feedings. Start with one pet and add more (up to 8). Swipe between a **Pets** page and a **Calendar** page, and use either of two **home-screen widgets** (a per-pet feed list and a monthly calendar) to log and review feedings without opening the app.

## Features

### App
- **Swipeable pages** — the main screen has two pages: **Pets** and a combined **Calendar** (swipe left/right)
- **Start with one pet, add more** — begins with a single pet; tap **➕ Add a pet** (up to 8) or 🗑 to remove one (always keeps at least one)
- Grid of pet cards (editable names)
- **Species per pet** — tap the icon to choose between 🦎 Crested Gecko and 🐍 Hognose Snake
- Large **Feed** button with a confirmation dialog; **tap again to undo** today's feeding
- **Last fed** time shown as `yyyy.MM.dd HH:mm`
- **Fed Today** visual feedback — card turns soft mint green + a "✓ Fed Today" badge
- **History** dialog listing all feeding timestamps for a pet
- **📅 Calendar** view per pet — a month grid that highlights the days it was fed; tap a day to view or cancel a logged feeding
- **⏰ Reminders** — set a feeding interval (in days) per pet; when a feeding is due you get a notification. Uses battery-friendly inexact alarms (no special permission needed) and re-arms after reboot.

### Home Screen Widgets
Two widgets are available (long-press home screen → Widgets → "피딩계산기"):
1. **Pet list widget** — every pet in a scrolling list with a Feed button (tap to log, tap again to undo), icon, and a short last-fed label. Fed-today rows turn green. Adapts automatically as you add/remove pets.
2. **Calendar widget** — the current month as a grid, highlighting every day any pet was fed (green) and today (outlined). Tap the title to open the app's Calendar page.

### Pet list widget details
- One row per pet with:
  - the pet's name
  - a short **last-fed label** (e.g. `Today 14:20`, `Yesterday 09:05`, `09.28 18:40`, or `Not fed yet`)
  - a **Feed** button that records a feeding instantly via a broadcast `PendingIntent` — no app launch (tap again to undo today's feeding)
- the pet's custom icon shown next to its name
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
4. Long-press the home screen → **Widgets** → add **피딩계산기**.

**Option B — Command line**
```bash
cd android
./gradlew assembleDebug      # builds app/build/outputs/apk/debug/app-debug.apk
# install on a connected device:
./gradlew installDebug
```

**Option C — Build in the cloud (no local setup)**

A GitHub Actions workflow (`.github/workflows/android-build.yml`) builds a debug APK automatically:
1. Go to the repo's **Actions** tab → **Android Build**.
2. It runs on every push/PR touching `android/` — or click **Run workflow** to trigger it manually.
3. When it finishes (green ✓), open the run and download the **`pet-feeding-tracker-debug-apk`** artifact (a zip containing `app-debug.apk`).
4. Transfer the APK to your Android phone and install it (you may need to allow "Install unknown apps").

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
