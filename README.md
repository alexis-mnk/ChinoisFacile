# EasyMandarin

**EasyMandarin** is a free, unlimited, **offline-first** Android app for learning Mandarin Chinese (Simplified Chinese) across the official **Hanban HSK 2.0 (2012)** curriculum — **HSK 1 to HSK 6 (4,995 unique words)**.

Built for focused, distraction-free study: **100% functional offline by default, no ads, no hearts/lives, no artificial XP grind, no account required, and no tracking**, with **optional email Cloud Backup** powered by **Supabase** (`public.user_backups`).

## Download

Download the ready-to-install Android APK (Android 8.0+ / API 26+, ~43.4 MB with all offline neural audio packs embedded) from [**GitHub Releases**](https://github.com/alexis-mnk/EasyMandarin/releases).

---

## Key Features

- **Audited HSK 1–6 Catalog (4,995 Words & 4,995 Unique Sentences):** Complete HSK 1–6 curriculum pairing interactive discovery lessons (**Cards** & **List** modes) with **56 embedded grammar pattern cards** across HSK 1–3, plus 4,995 unique, idiomatically natural example sentences with zero mechanical templates (validated across all 6 levels by `tools/validate_catalog.py`).
- **Offline Neural Audio (10,102 Opus Clips):** Natural Mandarin speech (`zh-CN-XiaoxiaoNeural`) packed into 7 compact binary `.pack` containers (`EMAU` v1, 39.10 MB uncompressed in APK assets for zero-copy `AssetFileDescriptor` playback), supporting **Normal 1.0x** and **Slow 0.7x** speeds with automatic silent **Android `TextToSpeech` (`zh-CN`)** fallback.
- **Interactive Example Sentences & Tone Colors:** Toggleable tone-colored pinyin and Hanzi with an in-app 5-tone legend, target-word highlighting, and **tap-to-peek** inline vocabulary cards with instant pronunciation playback.
- **Progressive Quizzes & SM-2 Spaced Repetition:**
  - **Tier 1 (Recognition):** `Hanzi → English` and audio-first `Audio → English` (including a *"Can't listen now"* skip that avoids penalizing accuracy or SM-2).
  - **Tier 2 (Contextual & Recall):** `English → Hanzi` and contextual fill-in-the-blank **`Cloze`** exercises.
  - **Tier 3 (Production):** `SentenceOrder` exercises strictly gated on already-learned vocabulary.
  - **End-of-Session Mistake Review & Quick Review:** Missed exercises return in a dedicated review loop at the end of a quiz without masking first-attempt SM-2 outcomes, plus a conditional **Quick Review** banner whenever words are due.
- **4,995-Word Lexicon & Clean Progress Dashboard:**
  - **Lexicon:** Alphabetical pinyin sorting, instant hanzi/pinyin/English search, mastery status filters (`All`, `Due`, `Learning`, `Mastered`, `New`), uniform 3-column grid or compact list view, and an SM-2 memory detail sheet for every word.
  - **Progress:** Clean streak hero card with a 7-day activity strip, session-based average accuracy, completed sessions counter, conditional Quick Review shortcut, and an interactive HSK 1–6 mastery overview ring with direct Lexicon jump.
- **Optional Cloud Backup (Supabase):** Opt-in email & password sign-in in Settings for manual **Save now / Restore** and silent background auto-sync across devices via `public.user_backups` (restoring SM-2 progress, streaks, preferences, and lesson completion), while remaining 100% functional offline without an account.
- **Material 3 UI & Local Privacy:** Light, Dark, and System themes, ergonomic portrait-only layout, and local-first SQLite + DataStore storage with one-tap progress reset.

---

## Tech Stack & Architecture

- **Language & UI:** Kotlin, Jetpack Compose, Material 3, Navigation Compose
- **Architecture:** Unidirectional MVVM (`StateFlow` in ViewModels, stateless UI composables, pure testable domain engines, manual DI via `AppContainer`)
- **Local Persistence:** Room (SQLite, schema v3) + Jetpack DataStore Preferences
- **Optional Cloud Sync:** `supabase-kt` (`auth-kt`, `postgrest-kt`) + `ktor-client-okhttp`, configured via optional `local.properties` `BuildConfig` fields (`SUPABASE_URL`, `SUPABASE_ANON_KEY`, empty by default)
- **Offline Audio Engine:** Custom `EMAU` v1 binary `.pack` reader (`PackedAudioPlayer` reading direct `AssetFileDescriptor` offsets into `MediaPlayer`), `HybridAudioPlayer` with silent `TtsAudioPlayer` fallback, plus bundled PCM WAV feedback chimes
- **Build Configuration:** `minSdk = 26`, `targetSdk = 35`, R8 code minification & resource shrinking

> **Package ID Note:** The Android `applicationId` remains `com.chinoisfacile.mandarin` to preserve seamless in-place update continuity for existing installations.

---

## Building from Source

### Prerequisites

- **JDK 21** (e.g., JetBrains Runtime 21 or Temurin 21)
- **Android Studio** / Android SDK Platform 35 (`compileSdk = 35`, `minSdk = 26`)
- **Python 3.10+** *(optional, for catalog validation and neural audio pack generation)*
- **Optional Cloud Backup keys:** To enable Supabase sync in your own build, define `SUPABASE_URL` and `SUPABASE_ANON_KEY` in `local.properties` (excluded from Git). If omitted, both default to empty strings and builds work offline out of the box.

### Gradle & Catalog Commands

Run unit tests and build APKs from the repository root:

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Release APK (R8 minified)
./gradlew assembleRelease

# Validate HSK 1-6 catalogs (vocabulary ceilings, tokens, pinyin alignment, Cloze rules)
python tools/validate_catalog.py
```

*(On Windows PowerShell, use `.\gradlew.bat` instead of `./gradlew`.)*

### Generating Offline Neural Audio Packs

To keep the Git repository lightweight, `app/src/main/assets/audio/*.pack` and `tools/.audio_cache/` are listed in `.gitignore`.

- **Out-of-the-box Git builds** work immediately without `.pack` files by falling back automatically to Android's built-in `TextToSpeech` (`zh-CN`).
- **To build with all 7 neural Opus audio packs embedded** (`hsk1.pack`–`hsk6.pack` + `grammar.pack`), install `edge-tts` and `ffmpeg`, then run the pack generator:

```bash
pip install edge-tts

# Install ffmpeg if not already available:
#   Windows: winget install Gyan.FFmpeg
#   macOS:   brew install ffmpeg
#   Linux:   sudo apt install ffmpeg

python tools/generate_audio_packs.py --levels all --concurrency 4
```

Clips are trimmed, encoded to 20 kbps Opus (`.ogg`), and cached by SHA-256 hash in `tools/.audio_cache/` for automatic resume and fast incremental rebuilds.

---

## Data Sources & Attribution

- **Curriculum Word Lists:** Official **Hanban HSK 2.0 (2012)** vocabulary levels 1–6 (Simplified Chinese).
- **Dictionary & Pinyin:** **CC-CEDICT**, licensed under **Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0)**, used for English glosses, pinyin references, and context-aware Traditional-to-Simplified disambiguation.
- **Example Sentences:** Curated sentences from **Tatoeba** (**CC BY 2.0 FR**) and filtered **OPUS** parallel `cmn-eng` corpora (`TED2020`, `QED`, `News-Commentary`), alongside handcrafted pedagogical sentences verified by `tools/validate_catalog.py`.
