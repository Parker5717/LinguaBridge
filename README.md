# LinguaBridge 🦉

**Fully offline Android app for Russian speakers preparing for an English-taught
foundation year at a Chinese university.**

Built for one very specific journey: native Russian → studying STEM subjects *in
English* → learning Chinese *through English* — the exact situation of a student
entering a Foundation Program at a Chinese university (and later facing the CSCA
exam). Every screen, word list and exercise serves that path.

> 100% offline. Zero network permission in the manifest — nothing to track,
> nothing to leak, works in a dorm with no SIM card.

## Features

- **Duolingo-style spaced repetition** — SM-2 scheduler behind a swappable
  interface; each word climbs an exercise ladder: intro card → choose the
  translation → choose the word → type it → fill the gap in a real example →
  translate the whole sentence (RU → EN, word-level diff feedback). Auto-graded,
  typo-tolerant (Levenshtein ≤ 1).
- **Two study tracks** — English (via Russian) and Chinese HSK 1–2 (via English,
  simulating real classes), switchable on the home screen.
- **4,120 study cards, all bundled**: 2,200 English words (A2→B2) with IPA,
  definitions and translated examples · 500 STEM terms (math + physics) with
  CSCA-style example problems · 120 grammar-metalanguage terms Chinese teachers
  use when teaching through English · 300 HSK words + 40 grammar points.
- **Library** — 45 graded texts (A2/B1/B2, campus life in China, accessible STEM)
  with tap-any-word translation popups, and 20 dialogues (classroom, dorm, clinic,
  DiDi) with per-line TTS, pinyin and Russian notes.
- **Listening** — TTS dictation with word-level diff highlighting, and
  listening-comprehension passages with questions (15 passages).
- **240 quiz questions** across 8 categories, a placement test, and a CSCA mode
  where the challenge is decoding the English wording of math/physics problems.
- **Offline dictionary** — instant search across all four content tables, in any
  direction (English, Russian, hanzi, pinyin).
- **Word game** — Wordle on the app's own vocabulary; solving a puzzle teaches
  you the word.
- **Progress** — streaks, GitHub-style activity heatmap, 30-day retention,
  per-skill progress; word-of-the-moment notifications (AlarmManager, survives
  Doze); full RU/EN interface switch; warm paper-and-clay theme (light + dark)
  with Lora serif display type.

## Architecture

Single-module Kotlin app: Jetpack Compose + Material 3, MVVM with manual DI
(one `AppContainer`, no Hilt), Room, DataStore, platform TextToSpeech.

The interesting part is the **content pipeline**:

```
content/*.json  ──validate──▶  tools/pack_content.py  ──▶  app/src/main/assets/content.db
   (source of truth,             JSON Schema + cross-file        (prepackaged Room DB)
    human-readable)              checks: duplicate ids/words,
                                 pinyin tone marks, MCQ answers,
                                 passage refs, dialogue order
```

- Two Room databases: `content.db` (read-only, shipped via `createFromAsset`,
  regenerated freely) and `user.db` (on-device progress) — content updates never
  touch your learning history.
- The packer builds `content.db` **from Room's exported schema JSON**
  (`app/schemas/`), so the Kotlin entities are the single source of truth and the
  asset can never drift from the code.
- The packer and the SM-2 scheduler are both covered by tests
  (`tools/tests/`, `app/src/test/`).

## Building

Requirements: JDK 17, Android SDK (platform 35), Python 3.10+ with `jsonschema`.

```bash
# 1. Pack the content database (validates all JSON first)
python tools/pack_content.py

# 2. Build the APK
./gradlew assembleDebug          # Windows: .\gradlew.bat assembleDebug

# Tests
./gradlew test
python -m pytest tools/tests/test_pack.py -q
```

The APK lands in `app/build/outputs/apk/debug/`. min SDK 29, target SDK 35.

## Content

All learning content was generated with Claude (Anthropic) in validated batches —
every batch passes JSON Schema plus cross-file consistency checks before it can
be packed (malformed batches are regenerated, never hand-patched). Russian
translations are natural Russian; all pinyin carries tone marks; polyphones
(多音字) are handled as distinct vocabulary items.

## License

Code: [MIT](LICENSE). Bundled Lora font: SIL OFL 1.1 (see [LICENSES.md](LICENSES.md)).
