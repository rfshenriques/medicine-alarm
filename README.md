# Medicine Alarm (Alarme do Remédio)

A deliberately stubborn Android medicine reminder. At each scheduled time it takes over the
whole screen — even if the phone is locked or asleep — with a very large message, an alarm
sound and vibration. The dismiss button appears **in a different place every single time**,
so it can never be tapped from muscle memory.

The interface is English by default and switches automatically to Portuguese on a phone set
to Portuguese.

---

## 1. Getting the APK (no Android Studio needed)

1. Create a free GitHub account if you do not have one.
2. Create a new **empty** repository (private is fine).
3. Upload the contents of this folder to it. Either:
   - drag every file into GitHub's "upload files" page (keep the folder structure), or
   - `git init && git add . && git commit -m "first" && git remote add origin <your-repo> && git push -u origin main`
4. Open the **Actions** tab. The "Build APK" workflow starts on its own (if not, click it and
   press *Run workflow*).
5. After ~3 minutes, open the finished run and download the **medicine-alarm-apk** artifact.
   Inside is `app-debug.apk`.

> The `.github/workflows/build.yml` file does everything: installs Java, installs the Android
> SDK, and builds. You do not need anything installed on your own machine.

**Prefer Android Studio?** Open this folder as a project, let it sync, then
*Build → Build Bundle(s)/APK(s) → Build APK(s)*.

## 2. Installing on his phone

1. Send the `app-debug.apk` to the phone (WhatsApp, email, USB cable, Google Drive).
2. Open it. Android will ask to allow installing apps from that source — allow it.
3. This is a debug-signed APK, so Play Protect may show a warning; choose *Install anyway*.

## 3. Setup on the phone — do this once, it matters

Open the app. If a yellow box appears at the top, tap **Allow next permission** repeatedly
until the box disappears. It walks through:

| Permission | Why |
|---|---|
| Notifications | required for the alarm to be allowed to appear |
| Alarms & reminders | lets the alarm fire at the exact minute |
| Display over other apps | lets the alarm cover whatever is on screen |
| Unrestricted battery usage | stops Android from killing the alarm to save power |

**Also check the phone manufacturer's own battery manager.** Xiaomi, Samsung, Huawei, Oppo
and OnePlus aggressively kill background apps. Find the app in Settings → Apps → *Medicine
Alarm* and set battery to *Unrestricted* / *No restrictions*, and enable *Autostart* if the
brand offers it. On Xiaomi also enable "Show on lock screen" and "Display pop-up windows
while running in the background". Without this, no alarm app on Android is reliable.

Finally, make sure the **alarm volume** is up — the app raises it if it is below half, but
some phones block that.

## 4. Using it

- **+ Add reminder** — pick the time, type the message, tick the days. Add as many as you
  like; each one can have its own time and its own message, and each can run on a different
  set of days.
- **Test alarm** — shows the alarm screen right now, so you can check it looks right.
- **Settings**
  - *Delay before the button appears* (default 3 s) — the alarm stays on screen and the
    button is invisible for the first few seconds, so it cannot be swatted away instantly.
  - *Require press and hold* (default off) — the button must be held for 3 seconds. Turn
    this on if he starts dismissing it reflexively anyway.
  - *Show the 10-minute snooze button* (default off) — leave it off unless he genuinely
    needs it, since a fixed snooze button is exactly the habit you are trying to avoid.
- On the alarm screen, tapping any empty space **moves the button again**, so a stray tap
  never trains a position.
- The hardware Back button is disabled on the alarm screen.

## 5. Anti-autopilot design, in short

- Button lands at a random x/y each time, and the new spot must be at least ~35% of the
  screen away from the previous one — it never lands twice in a similar place.
- Invisible for the first few seconds, so there is nothing to jab at on reflex.
- The message is huge and centred, so it gets read on the way to hunting for the button.
- Optional press-and-hold turns dismissal into a deliberate act.

## 6. Project layout

```
app/src/main/java/com/remedio/alarme/
  MainActivity.kt    setup screen: reminder list, editor, permission helper
  AlarmActivity.kt   the full-screen alarm + randomized button logic
  AlarmReceiver.kt   fires on schedule, also handles boot/time-change rescheduling
  Notifier.kt        full-screen-intent notification (the reliable wake path)
  Scheduler.kt       AlarmManager.setAlarmClock scheduling, next-occurrence maths
  Store.kt           reminders + settings in SharedPreferences (JSON)
```

Minimum Android version: 8.0 (API 26). No third-party services, no internet permission,
nothing leaves the phone.
