# Veloura 0.4.0: code review brief

For an independent reviewer (Codex, Grok or a person). Please review the code; do not push, merge or open pull
requests. Report findings with file, line, a concrete failure scenario, and a suggested fix.

## What to review

- Repository: `https://github.com/jmi-byte/Prism` (a fork of `Yigder/Prism`, GPL-3.0)
- Branch: `ccr-e79019fb-5xqj6o`
- Range: `81daa5bb..ccr-e79019fb-5xqj6o`. `81daa5bb` is the handover point on `feature/veloura-v1`
  (see `VELOURA_HANDOVER.md`). About 1,500 lines changed across 13 files plus tests.
- Command: `git diff 81daa5bb..origin/ccr-e79019fb-5xqj6o`

Veloura is an Android music app built on Prism (YouTube Music) as a separate build (`-PautoMixDj`, application ID
`com.jmibyte.veloura`). Its core feature is **Intelligent AutoMix**: it leaves each song partway through and brings
the next one in at a good moment, with DJ-style blends. The code still lives in Prism's `com.prism.music` packages;
Veloura-only behaviour is gated by `BuildConfig.AUTOMIX_DJ`.

## What changed (in commit order)

1. **DJ screen matches real play order** (`AutoMixDjUi.kt`). The queue/plan view used the row after the current
   song as "next"; it now uses the player's `upNext` play order (shuffle-aware). Labels no longer claim
   "Beat matched" for every plan.
2. **AutoMix always mixes mid-song** (`IntelligentAutoMixPlanner.kt`, `Mix.kt`, `PlaybackService.kt`). A new
   best-guess tier (`AutoMixBoundary.ESTIMATED`, `AutoMixPlan.entryEstimated`) runs when there is no phrase
   boundary or confident beat. It exits after the highlight (or around 90 s), just before a lyric line start or
   on a loose beat, and enters the next song at its highlight or around 30 s. Best guesses get a longer blend
   (about 6 s). The strict planner is unchanged when `estimate = false`.
3. **Library: account playlists and saving mixes** (`VelouraPlaylists.kt`, `AutoMixDjUi.kt`). Lists the
   account's playlists and Home's mixes. Ordinary playlists are saved to the library (`like/like`); auto-generated
   mixes (`RD…`, not `RDCLAK…`) are saved as a new private playlist (`playlist/create`) named
   "<mix> · <d MMM yyyy>".
4. **Mix Settings** (`AutoMixDjUi.kt`, `PlaybackService.kt`, `Mix.kt`). AutoMix on/off and crossfade length
   (Automatic or a fixed 1–12 s), honoured by mid-song blends (`earlyBlendLengthMs(fixedMs)`) and end-of-song
   ones. With AutoMix on, pairs with no mid-song plan now get an ordinary end crossfade instead of a cut.
5. **DJ effects** (`DjEqProcessor.kt`, `DjBlends.kt`, `Mix.kt`, `Settings.kt`). A Media3 audio processor on each
   player (main and blend tail) with three gliding controls:
   - `BassKill`: a low shelf at 180 Hz, -26 dB, for the **bass swap**.
   - `SweepFilter`: a resonant low-pass for the **filter sweep**.
   - `EchoOut`: a feedback delay for the **echo out**.
   Styles: Smooth (bass swap), Club (swap and sweep), Echo out, and Auto (Club when beats are matched, Echo when
   the ending beat is known, Smooth otherwise). Settings live in `SoundPrefs` (`djEffects`, `djStyle`).

6. **Next is mixed too (0.4.1)** (`Mix.kt` `blendSoon`, `PlaybackService.kt`). With AutoMix on, Next starts the
   same blend as an automatic transition about 4 s later (the outgoing song's second player loads and syncs). A
   second Next, or any failure to make the blend, moves on at once, so Next is never ignored. The DJ tab counts
   down "Mixing into the next song in N s".

## Where the risk is (please focus here)

1. **Audio thread** (`DjEqProcessor.kt`):
   - allocation on the audio thread (should be none in steady state);
   - thread safety of the `@Volatile` targets and the echo memory, which `setDelay` replaces from the main
     thread while the audio thread runs;
   - clicks when a filter switches on or off;
   - filter stability at low cutoffs;
   - 16-bit clipping;
   - true pass-through when idle.
2. **Blend state machine** (`Mix.kt`, `MixEngine`):
   - do bass, filter and echo always return to neutral on every exit path (finish, abort, skip, seek, song
     change)?
   - is `restoreBass()` reached on all of them?
   - the tail player is released with its processor; the main processor must end at full bass and an open filter.
3. **Best-guess planning** (`IntelligentAutoMixPlanner.kt`). Window bounds (45 s at the earliest, 20 s kept at
   the end), short songs (90 s or more for the best-guess tier), very late highlights, and the incoming cue for
   short next songs.
4. **Next blend** (`blendSoon`, `forced`, `forcedFailed`). Every path where a requested blend falls through
   (no plan, too little time, tail not ready, out of step, a seek, a song change) must either move on to the next
   song or be a deliberate no-op, never leave Next ignored; nor may it skip twice.
5. **Exact-pair guards** (`IntelligentAutoMixActivation`, `PlaybackService.kt`). A plan must only act on the
   exact current/next pair; queue edits, shuffle, repeat and skips must not run a stale plan. Manual Next jumps
   only to a phrase- or beat-based cue (`nextCueAt`).
6. **Playlist saving** (`VelouraPlaylists.kt`). Correct handling of `VL` prefixes, Liked Music (`LM`), editorial
   `RDCLAK` playlists, duplicates, and the failure messages.
7. **Prism build unaffected.** With `AUTOMIX_DJ` false: no DJ processor in the chain, no Veloura settings-search
   entry, identical behaviour.

## Known limits (not bugs to report unless they are worse than described)

- Not yet tested on a physical phone. The bass swap fires 120 ms early to allow for output latency; that figure
  is an estimate.
- The DSP runs in Kotlin per sample (biquads in doubles). The cost on low-end phones hasn't been measured.
- Best-guess transitions deliberately override the handover's rule that weak evidence means normal playback; the
  owner asked for every pair to be mixed mid-song.
- "DJ set order" (reordering by key and energy) is not implemented.
- The handover's open items remain: Previous, history and lyric timing after a mid-song entry.

## Already reviewed

An internal review found 10 issues. Nine were fixed in the commit after `fe68070`:
- Next jumped to best-guess cues;
- `LookTest` failed on the new preference keys;
- the AutoMix settings-search entry appeared in the Prism build;
- the slider truncated instead of rounding;
- the DJ processor sat in the Prism build's chain;
- the Library tab re-fetched on every visit;
- two places matched a plan to its pair, with different rules;
- slow-tempo echoes weren't on the beat;
- best-guess exits faded as a sung line started.

Not changed: a suggestion to derive the effect flags in `MixEngine` from `plan.dj` (a refactor, with no current
failure).

## How to build and test

Requirements: JDK 17 or later, Android SDK platform 37, build-tools 37.

```bash
./gradlew -PautoMixDj :app:testDebugUnitTest \
  --tests 'com.prism.music.IntelligentAutoMixPlannerTest' --tests 'com.prism.music.DjEqTest' \
  --tests 'com.prism.music.VelouraPlaylistsTest' --tests 'com.prism.music.Mix*' --tests 'com.prism.music.Dj*' \
  --tests 'com.prism.music.Crossfade*' --tests 'com.prism.music.Settings*' --tests 'com.prism.music.LookTest' \
  --tests 'com.prism.music.AppSettings*'
./gradlew -PautoMixDj :app:assembleDebug   # Veloura APK
./gradlew :app:compileDebugKotlin          # the plain Prism build must still compile
```

Last run: 123 tests passed, 1 skipped by the project's own setup; both builds compile.
