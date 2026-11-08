# Veloura handover — 0.9.2 (versionCode 78)

Written at the end of the cloud session, for whoever picks this up next (you, a local Claude Code, Codex or Grok).
It follows the original `VELOURA_HANDOVER.md` (handover point `81daa5bb`, branch `feature/veloura-v1`).

## Where the code is

- Repo: `jmi-byte/Prism`, branch **`ccr-e79019fb-5xqj6o`** (23 commits on top of `81daa5bb`, all pushed).
- Build Veloura: `./gradlew -PautoMixDj :app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
  (applicationId `com.jmibyte.veloura`). Without `-PautoMixDj` it builds plain Prism, which must keep compiling.
- Tests: `./gradlew :app:testDebugUnitTest` (plain build). `*LiveTest`/`LiveApiTest` need YouTube; in the cloud
  sandbox they, PodcastTest, GoogleCastTest, BlendExchangeTest and IssueFixes178Test failed for network reasons only.
  I18nGuardTest already failed at 0.7.0 (Veloura's hard-coded English strings); LookTest can flake when run with
  the whole suite (passes alone).

## Guardrails (unchanged)

- Keep the upstream association; never push to Yigder/Prism.
- Keep GPL attribution: licence strings and "Veloura is built on Prism by Yigder…".
- No real account cookies, tokens, keys or personal data in commits or notes.
- Don't call the app complete until transitions have been heard on a physical phone.

## What's in it (by version)

| Version | What |
|---|---|
| 0.5.x | Owner's ribbon-V icon and designed screens; audible DJ effects (bass swap, filter sweep, echo out); Next lands on a cue; one Transition style choice; karaoke lyrics in Now Playing |
| 0.6.x | More like this / Keep the mix going by language (Punjabi+Hindi together, English apart) and sound; Android Auto buttons, Veloura Mixes, Play with AutoMix |
| 0.7.0 | Why this song, Hook mode, Language mix, energy sets, lyrics romanisation/translation, story cards, wedding mode |
| 0.8.0 | Save the mix, Tempo lock, Context mood, Mashup transition style, Party queue (LAN web page + QR) |
| 0.8.1 | Notification/widget icon is Veloura's V |
| 0.9.0 | Mashups with real separated vocals (Sing's engine, Sing off): outgoing acapella over incoming beat; saved mixes keep each transition's exact points and replay them |
| 0.9.1 | Android Auto shows Veloura's icon (the media service's `android:icon`) |
| 0.9.2 | Dark theme (Mix Settings → Theme: Auto/Light/Dark); full-screen lyrics with A−/A+; visible feedback on Set the mood and Sync library now |

## Main files

- `app/src/main/java/com/prism/music/AutoMixDjUi.kt` — Veloura's app shell and screens' wiring.
- `VelouraScreens.kt` — stateless UI + palette (`VelouraTheme.dark`, theme-aware colours), render-tested in
  `VelouraScreensRenderTest` (PNGs in `app/build/veloura-previews/`).
- `VelouraExtras.kt` (Tempo lock, Context mood, Saved mixes), `PartyQueue.kt`, `VelouraFeatures.kt`, `SimilarSongs.kt`.
- `playback/Mix.kt` (MixEngine: plans, blends, mashup stages, `MixPoint` history, saved-point replay),
  `playback/DjEqProcessor.kt`, `playback/DjBlends.kt`, `playback/sing/VocalStemProcessor.kt` (mash modes PRIME/CUT/SOLO),
  `playback/sing/Sing.kt` (`prepareForMix`, `hasStems`), `playback/PlaybackService.kt`.
- Settings live in `SoundPrefs` (new keys must be added to the `notLook` list checked by LookTest).

## Still to check on a phone

1. Mashup with separated vocals (DJ · Mashup, on Wi-Fi, let a song play a minute or two first).
2. Saved mix replay keeps the same transition points.
3. Tempo lock sounds natural; Party queue reachable from a guest phone on the same Wi-Fi.
4. Dark theme on every screen (Prism's own settings pages inside Veloura use the Material colours).
5. Android Auto icon after reconnecting (Android Auto caches icons).
