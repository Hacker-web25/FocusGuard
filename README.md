# FocusGuard

A personal Android app that intercepts distracting apps (Instagram, etc.) with a fullscreen task review overlay and countdown timer.

## How it works

1. **Task Manager** — Add tasks with deadlines, priorities, and link them to long-term goals
2. **App Interceptor** — When you open a monitored app, a fullscreen overlay shows all your tasks with a 1-minute countdown timer
3. **Forced Review** — You can't dismiss the overlay until the timer completes, forcing you to think about your tasks before mindless scrolling

## Building (GitHub Actions)

No local tooling needed — the APK is built in the cloud.

### First-time setup

```bash
# 1. Create a new repo on GitHub

# 2. Push this project
cd FocusGuard
git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/FocusGuard.git
git push -u origin main
```

### Getting your APK

1. Go to your repo on GitHub
2. Click the **Actions** tab
3. The "Build APK" workflow runs automatically on every push
4. Click the latest run → scroll to **Artifacts** → download **FocusGuard-debug**
5. Unzip and sideload the `.apk` to your phone

You can also trigger a build manually: Actions tab → "Build APK" → "Run workflow".

### Making changes

Edit any file (even directly on GitHub), push/commit, and a new APK is built automatically. Download from Actions.

## Phone Setup (required once)

After installing the APK, grant these two permissions:

1. **Accessibility Service**: Settings → Accessibility → FocusGuard → ON
2. **Overlay Permission**: Settings → Apps → Special access → Display over other apps → FocusGuard → Allow

Both are also linked from the in-app Settings tab.

## Configuration

In the app's **Settings** tab:

| Setting | Default | What it does |
|---------|---------|-------------|
| Interception Active | ON | Master toggle |
| Timer Duration | 60s | How long you must review tasks |
| Cooldown Period | 5 min | Won't re-trigger within this window |

Monitored apps default to Instagram (`com.instagram.android`). To add more, update the default set in `TaskRepository.kt` — or extend the Settings screen with a UI for it.

## Project Structure

```
app/src/main/java/com/dhruv/focusguard/
├── data/
│   ├── model/          Task, Goal, Priority
│   ├── db/             Room database, DAOs
│   └── repository/     TaskRepository (data + settings)
├── ui/
│   ├── theme/          Material3 colors, typography
│   ├── screens/        TaskList, AddEdit, Goals, Settings
│   ├── navigation/     Bottom nav + routes
│   └── viewmodel/      TaskViewModel
├── service/
│   ├── AppDetectorService.kt   AccessibilityService
│   └── OverlayActivity.kt      Fullscreen task overlay
├── receiver/
│   └── BootReceiver.kt
├── FocusGuardApp.kt             Application class
└── MainActivity.kt
```

## Tech Stack

- Kotlin + Jetpack Compose + Material 3
- Room for local database
- Navigation Compose for routing
- AccessibilityService for app detection
- No internet permission — fully offline, fully private
