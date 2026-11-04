# EcoCash Forwarder (version 1: email)

Receives EcoCash SMS, saves them first, then emails each one from THIS phone's own Gmail
to bluehousezim@gmail.com with subject "SMS from <sender>". Your Apps Script already reads those.

## Build the APK (once, on any PC)
1. Install Android Studio (latest). File > Open > this folder. Let it sync.
2. Build > Generate App Bundles or APKs > Generate APKs (debug is fine). Copy app-debug.apk to the phones.
   The file is at app/build/outputs/apk/debug/app-debug.apk

## Per phone
1. Make a Gmail app password in THAT phone's Gmail (Google Account > Security > 2-Step Verification > App passwords).
2. Install the APK, open it, go to Setup: grant SMS, turn off battery limits, type line name,
   the phone's Gmail, the app password, an owner PIN. Save.
3. Turn OFF SMS2Email on that phone (otherwise every SMS is emailed twice; the script dedupes by reference,
   but keep it clean). Pilot on Zengeza first.
4. Test offline: airplane mode, send yourself an EcoCash-style SMS from an allowed sender or wait for a real one,
   see "1 waiting" on Status, switch airplane mode off, it should go within a minute or two.

## Safety built in
- Save first (Room), send after (WorkManager, retries 1,2,4..30 min until done).
- Catch-up scan of the phone inbox every 15 min and after boot, duplicates blocked by a unique index.
- PIN/OTP/token text is removed before sending.
- Only the allowed senders are read; other SMS are ignored.
