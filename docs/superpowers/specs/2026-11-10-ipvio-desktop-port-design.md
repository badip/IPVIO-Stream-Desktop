# IPVIO Stream for Windows — design

Date: 2026-11-10
Status: draft, awaiting approval
Source of truth for behaviour: https://github.com/Emrodev/IPVIO-Stream @ `b65e8384` (GPL-3.0), plus shipped APK `com.ipvio.stream` 1.3 (`sha256 89d1dfea…`).

## Intent

Give Windows users the same IPTV player the Android APK provides: same playlists, same catalogue screens, same player features, as a normal Windows application that runs from an `.exe`. The port is built from the upstream Kotlin source, not from decompiled bytecode.

## Goals

- One installable desktop application (`IPVIOStream.exe`, no JVM required on the user's machine) that plays Xtream Codes, Stalker and M3U sources.
- Same screens, same navigation, same feature set as the Android app wherever Windows permits, reusing upstream Compose UI and Kotlin logic.
- Upstream fixes stay portable: shared code lives in a module the Android app can also consume, so the port tracks upstream instead of forking into a divergent copy.
- Upstream test suite (31 JVM tests) runs in CI against the shared module.

## Non-goals

- Android TV / Fire TV packaging, D-pad remote UX, leanback launcher, Play Store distribution.
- Any emulator or Android-runtime shim. No Windows Subsystem for Android.
- Fetching or shipping content. The app stays a player; the user brings playlists.
- Pixel-for-pixel UI equality. Screen-for-screen equality is the target.
- Importing data from the Android app. Windows starts clean: no backup, profile or history migration from the APK. The Android backup/transfer format is not a compatibility contract.

## Anti-goals

- A "port" that breaks playback of the formats the APK handles: HLS, DASH, MPEG-TS, AC3/E-AC3/DTS audio.
- Silent feature loss. Anything dropped is listed below with a Windows replacement or an explicit cut.
- Closed-source redistribution. GPL-3.0 forbids it; the port ships with source.

## Constraints

- GPL-3.0 (upstream) — the port and its published binaries stay GPL-3.0; `THIRD_PARTY_NOTICES.md` extends with VLC/ffmpeg terms.
- Windows 10 22H2 / 11, x64 first, arm64 later.
- Codes must be built without Android SDK: the shared and desktop modules are plain JVM.
- No Play-store style distribution; updates come from GitHub Releases, like upstream.

## Approaches considered

| Approach | Verdict |
|---|---|
| **Compose Multiplatform Desktop** (Kotlin, one language with upstream, jpackage `.exe`, libVLC playback) | **Chosen.** Reuses 88 Compose UI files and all pure-Kotlin logic; keeps one build system; upstream can sync. Cost: every `android.*` call needs a Windows seam (59 files). |
| Electron + TypeScript rewrite | Rejected. Nothing of the 2.5 MB Kotlin source is reused, so it is a from-scratch app with permanent double maintenance and no upstream sync. |
| Kotlin Multiplatform shared logic + native shell (WinUI/.NET or Rust) | Rejected. Two UI stacks and an FFI boundary; slower to reach feature parity than Compose Desktop for the same result. |
| Ship the APK inside an Android runtime | Rejected by the user (route A) and technically: Windows Subsystem for Android is discontinued. |

## Architecture

Three Gradle modules, one repository (`ipvio-desktop`, forked from upstream):

- `:core` — JVM-only Kotlin. Models, playlist parsing, Xtream/Stalker clients (OkHttp), EPG, catalogue logic, dictionary/i18n, settings schema, and the upstream logic tests moved here unchanged where possible.
- `:android` — the upstream `:app` module, reduced to Android UI + Android implementations of the seams. Keeps building the APK.
- `:desktop` — Compose for Desktop UI, desktop seam implementations, `main()`.

Seams are narrow interfaces in `:core` with `expect`-style provider objects (chosen over Kotlin `expect/actual` so `:core` stays a plain JVM module and tests run without the Android toolchain).

| Upstream Android dependency | Desktop implementation |
|---|---|
| `media3-exoplayer` (`Playback.kt`, `PlayerNet.kt`, `PlayerTech.kt`) | `PlayerEngine` interface in `:core`; `VlcPlayerEngine` in `:desktop` using vlcj 4.12.1 + libVLC 3.0.x runtimes |
| `media3-ffmpeg-decoder` (AC3/E-AC3/DTS) | Native to libVLC; no extra decoder needed. Verified per format in acceptance tests |
| `SharedPreferences` (`Store.kt`, `*Prefs.kt`) | Same key/value API over a JSON file in `%APPDATA%\IPVIO Stream\settings.json`, with atomic write and `.bak` recovery |
| `WorkManager` (`UpdateWorker`) | Scheduled executor for refresh; Windows toast + tray icon for update prompts |
| `RecordService` + `libffmpegJNI.so` (`Recorder.kt`, `LiveTimeshift.kt`) | Bundled `ffmpeg.exe` (GPL build, `-f gdigrab` screen capture, direct stream copy for live) and VLC timeshift for rewind |
| `BroadcastReceiver` boot/reminders (`BootReceiver`, `ReminderReceiver`) | `ScheduledExecutorService` + `HKCU\…\Run` entry + Windows toast notifications |
| `MainActivity`/`RestartActivity`, overlay, immersive mode | Single Compose `Window`, tray icon, always-on-top option for the mini player |
| Coil 2 (`coil-compose`) images | Coil 3.6.3 (`coil3.compose`) — multiplatform, same call sites |
| `FileProvider` + APK self-update | Download MSI/portable zip from GitHub Releases, verify SHA-256, launch installer |
| Phone import over Wi-Fi (`PhoneImport.kt`, `QrCode.kt`) | Keep: JDK `HttpServer` on LAN, QR code rendered in-window, browser form on the phone |
| Google Drive backup (`DriveSync.kt`) | Desktop OAuth loopback flow, same Drive app folder. Last phase; hidden when no client ID |
| `FocusCompat`, `RemoteHub` D-pad focus | Mouse/keyboard focus, keyboard shortcuts, optional gamepad mapping via `XInput` |

UI: Compose Multiplatform 1.12.2, Kotlin 2.2.x, JDK 21 toolchain, Gradle 8.x wrapper. Theme (`ThemeMath.kt`, `Brand.kt`, `Look.kt`), typography and 12 colour presets port unchanged; the TV layout gets a desktop density pass (mouse targets, window resize, 16:9 video panel), not a redesign.

## Parity matrix

Legend: **P0** core loop, **P1** catalogue, **P2** player, **P3** extras.

| Feature (upstream README) | Phase | Status on Windows |
|---|---|---|
| Xtream login, M3U URL/file, Stalker MAC, multiple playlists, source filter/switch | P0 | Same |
| Large playlists: language filter while loading, resumable download, compact storage | P0 | Same |
| Home/Movies/Series with rotating header, rows (Continue, Live now, Top 10, Because you watched, My list, New, Top rated, genres) | P1 | Same |
| Browse grid/cinema, sort/rating/year/genre/country filters, IMDb→RT→TMDB→provider rating order | P1 | Same |
| Detail page, resume, trailer, my list, favourites, source/quality choice | P1 | Same |
| Series seasons/episodes, sections, go-to-episode | P1 | Same |
| Live TV categories/favourites/own lists, TV guide, catch-up, reminders, sports hub | P1 | Same; reminders as Windows toasts |
| Search with typo tolerance, provider categories, automatic genres | P1 | Same |
| Player: audio/subtitle select remembered, preferred languages, subtitle style | P2 | Same |
| AC3/E-AC3/DTS software decode | P2 | Via libVLC; verified per format |
| Skip intro/credits (TheIntroDB), auto next episode | P2 | Same |
| Quality selection with 4K check, source fallback on error/stall | P2 | Same logic, VLC error surface |
| Buffer levels, watchdog, endless live reconnect, reconnect after network/standby change | P2 | Watchdog/reconnect ported; buffer telemetry from VLC differs in shape |
| HLS, DASH, MPEG-TS | P2 | Via libVLC |
| Aspect/zoom, frame-rate matching, tech info, clock overlay, sleep timer | P2 | Aspect/zoom/sleep/clock/tech same; frame-rate matching depends on GPU + exclusive-fullscreen, best effort |
| Rewind live TV with on-device buffer | P2 | VLC timeshift; seek granularity differs |
| Record current show to storage | P2 | ffmpeg stream copy; same file naming |
| Profiles, parental PIN, history, my list, favourites, backup/restore | P1 | Same (local files, Windows-only format; no Android migration) |
| 12 themes, 5 backgrounds, custom colours, 10 languages | P1 | Same |
| Phone import over Wi-Fi | P3 | Same (QR + LAN server) |
| Transfer to another device over Wi-Fi | P3 | Same protocol between two Windows installs; not compatible with the APK |
| Optional Google Drive backup | P3 | Desktop OAuth; hidden without client ID |
| In-app update from GitHub Releases | P3 | Windows installer flow |
| Multi-view / dual view | P3 | Two independent players in split panes |
| Cast to TV | P3 | DLNA + Chromecast best effort; Chromecast SDK is Android-only, so this is a documented partial |
| Android TV remote UI, D-pad focus, voice search, leanback | Cut | Replaced by mouse/keyboard shortcuts; voice search via Windows speech is out of scope |
| Overlay permission, install-unknown-apps, Android keystore | Cut | Not applicable |

## Phases and acceptance

Each phase ends with a runnable build and a checklist the user can try.

- **P0 (skeleton, 1 build):** repo fork, `:core` extraction, desktop window, theme, playlist add (Xtream + M3U), channel/movie/series list, VLC playback of HLS/TS/DASH. Accept: a real playlist plays live TV and VOD; settings persist across restart.
- **P1 (catalogue):** home rows, browse filters, detail, series, search, EPG, catch-up, profiles, favourites/history, settings screens, i18n, themes. Accept: every screen in `docs/screenshots` exists and works with a real playlist.
- **P2 (player parity):** track selection, quality/source fallback, watchdog/reconnect, aspect/zoom/clock/sleep, tech info, skip intro/credits, recorder, timeshift, reminders. Accept: format matrix (HLS, DASH, TS, HEVC, AC3, E-AC3, DTS) documented pass/fail.
- **P3 (extras + ship):** phone import, transfer, Drive backup, sports hub, multi-view, updater, MSI + portable exe, signing, notices.

## Testing

- Port the 31 upstream JVM tests into `:core`; they must pass unchanged.
- **Real source:** the user supplies an Xtream Codes account (host, user, password). Credentials live in `%APPDATA%\IPVIO Stream\`, never in git, never in test fixtures, never in build files. Each phase's manual acceptance run uses it.
- **Synthetic fixtures:** `ffmpeg`-generated HLS, DASH and MPEG-TS streams with HEVC, AC3, E-AC3 and DTS tracks, plus a small M3U, so the format matrix and the playlist parser are testable without the account and reproducible in CI.
- **Not covered:** Stalker portal handshakes, catch-up/archive links, provider-side EPG quirks and very large playlists are verified only when such a source becomes available. Until then those paths carry a "compiled, untested against a real provider" note in the release checklist.
- Format matrix results recorded per release in `docs/testing/playback-matrix.md`.
- Screen-by-screen comparison against `docs/screenshots/*.jpg` (visual check, not pixel diff).
- No automated UI tests in P0–P1; Compose Desktop UI tests reconsidered in P2 if regression cost justifies them.

## Build and packaging

- `./gradlew :desktop:packageAppImage` → `IPVIOStream.exe` + bundled JRE (jpackage, no admin, portable folder).
- `./gradlew :desktop:packageMsi` → MSI (requires WiX 3 on the build machine only).
- Runtime payload: libVLC 3.0.x (LGPL, DLLs in `app/vlc`), `ffmpeg.exe` (GPL build, invoked as a separate process), no other native dependencies.
- CI: Windows runner, `:core` tests + `:android` assembleDebug + `:desktop` packageAppImage; artifacts attached to GitHub Releases, each with a `.sha256`. No signing step: builds ship unsigned.

## Risks

1. **Android coupling is wider than the import count suggests** — 59 files import `android.*`, but Compose UI files also use Android-only overloads (`Modifier.focusRequester` variants, `LocalContext`). Budget for a seam review of the 88 Compose files, not just 59.
2. **Playback parity is the top risk.** VLC's buffering, metrics and frame-rate handling differ from ExoPlayer. Frame-rate matching may degrade to best effort.
3. **Recording live TV on a desktop is a different security model** — screen capture needs either `gdigrab` (captures the window region) or stream copy; stream copy is chosen where the source allows it.
4. **Upstream drift.** If upstream keeps evolving the Android UI, merges cost time. Mitigation: `:core` extraction stays mechanical, no behavioural edits there.
5. **Licences.** VLC LGPL and ffmpeg GPL must be shipped as separate binaries with notices; the app itself stays GPL-3.0 and its source published.
6. **Unsigned builds.** No Windows code-signing certificate. Every MSI/portable build shows SmartScreen "unknown publisher" until a certificate exists; `ffmpeg.exe` and the VLC DLLs are likewise unsigned and execute as separate processes. Documented in the release notes; no mitigation beyond publishing SHA-256 digests next to each artifact.

## Settled builder calls

- **Compose UI is ported, not shared.** `:core` holds logic and tests; `:desktop` holds its own copy of the 88 Compose files. A shared UI module would require `expect/actual` seams through every screen and edits to the Android call sites. Cost accepted: upstream UI changes are re-applied by hand; only `:core` syncs automatically.
- **Recording and timeshift use an in-process HTTP relay** that tees the player's byte stream, matching upstream `LiveTap`/`TapAwareDataSource` semantics and keeping single-connection providers working. `ffmpeg` reads the relay or the original URL only when the provider allows a second connection.
- **Distribution repository:** the fork lives under the authenticated GitHub account, named `IPVIO-Stream-Desktop`, public (GPL requires source availability, and the in-app updater reads GitHub Releases). Upstream `Emrodev/IPVIO-Stream` remains the APK's home; this fork ships Windows builds only and does not publish APKs.

## Left to the builder

- Exact Kotlin/Compose/Gradle version pinning beyond the ones named here.
- How far `:core` extraction goes per file; the rule is "logic leaves the Android module, UI stays where it compiles first".
- VLC packaging layout and whether to ship the full libVLC runtime or require installed VLC.
- Window chrome, default window size, and mini-player shape.
- ffmpeg build source and which filters are needed for recording.

## Decisions made in conversation

- Route: Compose Multiplatform Desktop (user chose it over Electron and over an Android-runtime wrapper).
- Scope: same screens and same catalogue/playlist features; VLC-based playback; desktop input instead of D-pad (user accepted the parity boundary stated in the table above).
- Test source: the user provides an Xtream Codes account for manual acceptance runs.
- Data: Windows starts clean. No migration of Android backups, profiles or history.
- Signing: no code-signing certificate. Builds ship unsigned with published SHA-256 digests.
