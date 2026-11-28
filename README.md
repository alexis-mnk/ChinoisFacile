# EasyMandarin

**EasyMandarin** is a free, **offline-first** Android app for learning Mandarin Chinese (Simplified) across the official **HSK 1 to HSK 6** curriculum (**4,995 words**). No ads, no lives/hearts, no tracking, and no account required.

Download the latest Android APK from [**GitHub Releases**](https://github.com/alexis-mnk/EasyMandarin/releases) *(Android 8.0+)*.

## Features

- **Structured HSK 1–6 Path:** Unit-by-unit lessons and quizzes covering all 4,995 official words, with built-in grammar cards for HSK 1–3.
- **Offline Mandarin Audio:** Natural pronunciation for every word and example sentence at normal (1.0x) and slow (0.7x) speeds.
- **Natural Example Sentences & Tone Colors:** Unique, context-rich sentences with optional tone-colored pinyin and tap-to-peek word cards.
- **Spaced Repetition & Varied Quizzes:** Recognition, listening, fill-in-the-blank, and sentence-ordering exercises powered by SM-2 spaced repetition and quick reviews.
- **Searchable Lexicon & Progress Tracking:** Filterable 4,995-word dictionary, daily streak, accuracy stats, and mastery overview per HSK level.
- **Optional Email Cloud Backup:** Sign in from Settings to sync and restore your progress across devices, or stay 100% offline.

## Build from Source

Requires **JDK 21** and **Android SDK 35**:

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

Supabase keys in `local.properties` are optional — leave them out to build and run the app completely offline.
