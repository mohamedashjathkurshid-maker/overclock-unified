
# OVERCLOCK//UNIFIED — HUD launcher (Concept E)

Custom hacker-themed overlay UI for Android. D-core hybrid:
live system HUD + command console + stealth edge tab + app fan.

## Build with GitHub — no PC tools needed (easiest)
1. Create a free account at github.com.
2. Click **New repository** → name it `overclock-unified` → Public → **Create**
   (do NOT add a README).
3. In the new repo click **Add file → Upload files**, then drag the WHOLE
   unzipped `OverclockUnified` folder into the page → **Commit changes**.
4. Open the **Actions** tab — you should see "Build APK" running.
   First run takes ~5–10 minutes (GitHub sets up the Android SDK).
5. When it finishes green, click the run → scroll to **Artifacts** →
   download **overclock-unified-apk** → inside is `app-debug.apk`.
6. Copy it to your phone, install, open, press START HUD.

Every time you edit any file on GitHub and commit, a fresh APK rebuilds
automatically. To rebuild manually: Actions → Build APK → Run workflow.

## Build in Android Studio (alternative)
File > Open > this folder → let Gradle sync → Build > Build APK(s).

## First run on the phone
1. Open the app → press **START HUD**.
2. Grant **"Display over other apps"** when prompted → START again.
3. Grant notifications if asked.

## Controls
- **HUD bar**: tap = sysboost · hold = kill background apps · drag = move.
- **Console bar** (`root@a12:~$`): `help`, `open shapr3d`, `boost`,
  `killall`, `status`, `hud off`.
- **Amber edge tab**: tap = fan of 5 apps · drag = reposition.

## Notes
- Real FPS needs `dumpsys SurfaceFlinger` polling — currently placeholder 60.
- `boost` uses root only if your phone is rooted; otherwise it purges RAM only.
- Works fully on stock A07 — no unlock needed. Bake into the ROM later
  as a system/priv-app with signature permissions.
