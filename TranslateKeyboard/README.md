# Translate Keyboard (Android)

A real, installable Android system keyboard that shows a live translation
preview as you type and lets you insert the translated text into any app
(WhatsApp, Instagram, Messages, etc.) with one key. Powered by your free
Groq API key — no cost.

## ⚠️ Important: how to build this

This project needs the Android SDK/Gradle to compile, which requires
network access I don't have in my sandbox — so **I couldn't compile or
run this myself.** I wrote it carefully following standard, well-tested
Android patterns and checked everything I could without the SDK (XML
validity, ID/resource cross-references, Kotlin syntax), but the two paths
below are how you'll actually get a working APK.

You have two options — pick whichever's easier for you:

### Option A: No Android Studio — build in the cloud via GitHub Actions

This project includes a GitHub Actions workflow (`.github/workflows/build.yml`)
that builds the debug APK automatically on GitHub's servers. You never
install any Android tooling on your own machine.

1. Create a free account at [github.com](https://github.com) if you don't have one.
2. Click **New repository** (top right → the "+" icon → "New repository").
   Name it anything (e.g. `translate-keyboard`), leave it public or private,
   don't initialize with a README, then click **Create repository**.
3. On the empty repo page, click **"uploading an existing file"**.
4. Unzip this project on your laptop, then **drag the entire unzipped
   `TranslateKeyboard` folder's contents** into the upload box (drag the
   files/folders themselves, not the outer zip). Modern browsers (Chrome,
   Edge) preserve the folder structure when you drag a folder in.
5. Scroll down, click **Commit changes**.
6. Click the **Actions** tab at the top of the repo. You should see a
   workflow run start automatically (triggered by your upload). Wait for
   the green checkmark (~3-5 minutes).
7. Click into the finished run → scroll to **Artifacts** at the bottom →
   download `translate-keyboard-debug-apk`. It's a zip containing
   `app-debug.apk`.
8. Transfer that `.apk` file to your phone (email it to yourself, upload
   to Google Drive and download on-phone, USB cable, whatever's easiest).
9. On your phone, tap the `.apk` file to install it. Android will warn
   about installing from an unknown source — that's expected for any app
   outside the Play Store; allow it for this install.
10. Continue from **"How to use it on your phone"** below.

If the Actions run fails (red X), click into it, copy the error text from
the log, and paste it to me — I'll fix the code directly.

### Option B: Android Studio (more control, easier debugging)

1. Install [Android Studio](https://developer.android.com/studio) if you don't have it.
2. Open this folder as a project (`File > Open`, select the `TranslateKeyboard` folder).
3. Let Gradle sync — Android Studio will auto-generate the Gradle wrapper
   and download dependencies (needs internet).
4. Connect your Android phone (with USB debugging enabled) or use an emulator.
5. Click Run (▶). The app installs — it's mostly a settings screen since
   the actual keyboard runs system-wide.

## How to use it on your phone

1. Open the "Translate Keyboard" app → tap **Open Keyboard Settings** →
   enable "Translate Keyboard" in the list.
2. Tap **Choose Input Method Now** and select it (or switch keyboards the
   normal way from any text field's keyboard-switch icon).
3. Back in the app, paste your **free Groq API key** (get one at
   [console.groq.com/keys](https://console.groq.com/keys), no credit card
   needed) and set your source/target languages (just type language names,
   e.g. "English" → "Japanese"). Tap Save.
4. Open WhatsApp (or anything else), tap a text field, switch to Translate
   Keyboard, and start typing.

## How it works

- You type normally; letters accumulate as **composing text** (the usual
  underlined in-progress text you see in any keyboard) rather than being
  committed immediately.
- ~500ms after you stop typing, the app sends the current text to Groq
  (`llama-3.3-70b-versatile`) with a translation prompt, and the result
  appears in the preview bar above the keys.
- Press **"Translate ➤"** to swap the composing text for the translation
  and commit it into the app you're typing in. The buffer then resets for
  your next sentence.
- The **⇄** key swaps your source/target languages (handy for replying in
  a back-and-forth conversation).
- The **⚙** key opens the settings screen to change your API key or
  languages without leaving the keyboard.

## Files

```
app/src/main/
├── AndroidManifest.xml              # Declares the settings Activity + IME Service
├── java/com/example/translatekeyboard/
│   ├── MainActivity.kt              # Settings screen
│   ├── TranslateInputMethodService.kt  # The keyboard itself (core logic)
│   ├── TranslationClient.kt         # Groq API call
│   └── Prefs.kt                     # Stores API key + languages
└── res/
    ├── layout/activity_main.xml     # Settings screen layout
    ├── layout/input_view.xml        # Keyboard + translation bar layout
    ├── xml/keyboard_qwerty.xml      # Key layout (rows, codes, labels)
    ├── xml/method.xml               # Required IME descriptor
    └── drawable/key_background.xml  # Key press visual state
```

## Known limitations / things to expect

- **Translation isn't per-keystroke** — it's debounced ~500ms after you
  pause typing, both to stay well within Groq's free-tier rate limits and
  because translating a half-typed word isn't useful anyway.
- **Lowercase-first typing**: Shift capitalizes only the next letter (no
  caps-lock). Easy to extend if you want it.
- **This layout is intentionally minimal** (no numbers row, no emoji, no
  autocorrect) to keep the first version simple and readable — see
  "possible extensions" below.
- Since I couldn't compile-test this, if Android Studio flags an error,
  it's most likely something small — a missing import or a Gradle version
  mismatch (fixable by letting Android Studio "Upgrade Gradle" if prompted).

## Possible extensions
- Add a numbers/symbols row (second `Keyboard` XML + a key to switch between them)
- Add a "direct typing" mode toggle that bypasses translation entirely
- Cache recent translations to reduce repeat API calls
- Add a proper language picker (spinner with a real list) instead of free-text
- Show translation candidates for individual words, not just the whole buffer
