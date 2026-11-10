# IPVIO Stream Desktop — P0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a runnable Windows skeleton of IPVIO Stream that adds an Xtream or M3U playlist, lists live/movie/series items, plays them through libVLC, and remembers everything across restarts.

**Architecture:** Two Gradle modules in one repository. `:core` is plain JVM Kotlin holding the upstream domain logic copied from `Emrodev/IPVIO-Stream` `b65e8384` plus four new desktop-facing classes (`Settings`, `PlaylistRepo`, `PlaylistDownloader`, `PlaylistService`). `:desktop` holds Compose for Desktop UI copied/adapted from upstream plus vlcj playback and packaging. No Android module and no Android SDK in P0.

**Tech Stack:** Kotlin 2.2.10, JDK 21 toolchain, Gradle 8.14.3, Compose Multiplatform 1.12.2, OkHttp 4.12.0, Gson 2.11.0, org.json 20250107, kotlinx-coroutines 1.10.2, vlcj 4.12.1, libVLC 3.0.24 (win64), JUnit 4.13.2, jpackage (app-image).

**Spec:** `docs/superpowers/specs/2026-11-10-ipvio-desktop-port-design.md`

## Global Constraints

- Licence: GPL-3.0. `LICENSE` stays upstream's; every Windows binary is published with source and `THIRD_PARTY_NOTICES.md` listing VLC (LGPL-2.1+), ffmpeg (GPL, not shipped in P0), OkHttp/Gson/Kotlin/Compose (Apache-2.0), org.json (public domain).
- Platform: Windows 10 22H2 / 11 x64. No Linux/macOS work in P0.
- JDK 21 toolchain; Kotlin 2.2.10; Compose Multiplatform 1.12.2; Gradle 8.14.3. Do not float versions.
- `:core` must not import `android.*`, `androidx.*`, or any Compose type. It builds and tests without an Android SDK.
- Package name stays `com.ipvio.stream` for moved upstream files. New desktop-only classes live in `com.ipvio.stream.desktop`.
- Credentials (Xtream host/user/pass, M3U URLs with tokens) never appear in git, logs, error messages, test fixtures, or build files. Tests use `127.0.0.1` and fake users only.
- Data root: `%LOCALAPPDATA%\IPVIO Stream` (DPAPI-sealed secrets are machine-bound; do not use roaming `%APPDATA%`).
- Builds are unsigned. Every artifact gets a `.sha256` file and a release note mentioning the SmartScreen warning.
- Upstream files are copied verbatim unless a task says otherwise. Record every intentional edit in `core/UPSTREAM.md`.

## Spec Corrections Accepted in This Plan

These are plan-level decisions that refine the approved spec. Reviewers should reject them here if they disagree.

1. **30 of 31 upstream test classes move; two of them lose a media3-only method.** `CoreLogicTest.kt` asserts `playbackErrorText` against `androidx.media3.common.PlaybackException` constants, so it stays with the Android module in P3. `PlayerLogicTest` moves after `PlayerLogic.kt` is trimmed of its three media3 blocks. `PlayerBufferTest` moves without its `IptvLoadErrorPolicy` method. VLC has no ExoPlayer error codes, so none of that behaviour survives a port anyway.
2. **Stalker moves from P0 to P1.** `StalkerApi.resolve` takes an Android `Context` and reads `PlaylistStore.lists`; it needs a seam and a real portal to verify. P0 lists Stalker as "not yet supported" in the add-playlist screen. The user has no portal to test with.
3. **P0 manages several playlists (add / remove / activate / rename via the repo) but does not merge them.** The "Also show" merged view is P1. The rail shows and switches playlists; rename/remove UI is P1.
4. **P0 screens are functional, not the final upstream layouts.** They use the upstream theme, text translation and item model, but a simple rail + list layout. The real Home/Browse/Detail screens are P1.
5. **No `:android` module in P0.** The upstream `:app` module and its CI job arrive in P3 together with Android SDK setup. P0 CI builds `:core:test` and `:desktop:packageAppImage` only.
6. **Three import-free upstream files are not moved.** `Trending.kt`, `EpgFull.kt` and `LiveTap.kt` reference Android-side objects (`Config`, `MetaRepo`, `Epg`, `Catchup`, `LiveSettings`). They belong to the P1 home/tap/EPG work. `PlayerLogic.kt` moves trimmed of its media3 blocks, `Diag.kt` is rewritten without Compose state.

## Review Focus

The five conditions most likely to bite a real user, each pinned by a test in the owning task:

1. **A provider answers HTTP 200 with an HTML error page** instead of an M3U. The app must refuse it with a readable error and never parse the HTML as a playlist. (Task 6)
2. **A server ignores `Range`** while resuming an interrupted download. The file must restart cleanly instead of appending a second copy. (Task 6)
3. **An Xtream provider has `get.php` disabled.** The loader must fall back to `player_api.php` and still produce live, movie and series entries. (Task 6)
4. **An error message leaks the playlist URL or password.** Every failure path must show text without credentials. (Task 6)
5. **The Windows profile path contains non-ASCII characters** (`C:\Users\José\…`), which must not break settings file creation or backup recovery. (Task 3)

## File Structure

```
ipvio-desktop/
  build.gradle.kts, settings.gradle.kts, gradle.properties, gradlew.bat, gradle/
  LICENSE, THIRD_PARTY_NOTICES.md, README.md
  core/
    UPSTREAM.md                     moved-file manifest + upstream commit
    build.gradle.kts
    src/main/kotlin/com/ipvio/stream/
      (53 upstream pure-Kotlin files + trimmed PlayerLogic.kt, package unchanged)
      Diag.kt                       plain-JVM seam for upstream Diag (Compose-free)
      XtreamApi.kt                  upstream + Gson reader
      Settings.kt                   key/value + file persistence + Codec
      PlaylistRepo.kt               playlist list CRUD + hidden-language memory
      PlaylistDownloader.kt         resume, Xtream fallback, gzip
      PlaylistService.kt            scan, language filter, binary cache
    src/test/kotlin/com/ipvio/stream/
      (30 upstream test files)
      TestHttpServer.kt             loopback HTTP fixture
      SettingsTest.kt, PlaylistRepoTest.kt, XtreamApiTest.kt,
      PlaylistDownloaderTest.kt, PlaylistServiceTest.kt
  desktop/
    build.gradle.kts                Compose app, downloadVlc, jpackage
    src/main/kotlin/com/ipvio/stream/            copied UI files (package com.ipvio.stream)
      Look.kt, DesignK.kt, Ui.kt, Lang.kt, General.kt
    src/main/kotlin/com/ipvio/stream/desktop/    new desktop code
      Main.kt, AppDirs.kt, Dpapi.kt, AppState.kt, App.kt,
      AddPlaylistScreen.kt, LanguageDialog.kt, BrowseScreen.kt,
      player/PlayerEngine.kt, player/VlcRuntime.kt, player/VlcPlayerEngine.kt, player/PlayerWindow.kt
    src/test/kotlin/com/ipvio/stream/desktop/DpapiTest.kt
```

---

### Task 1: Repository and Gradle skeleton

**Files:**
- Create: `.gitignore`, `README.md`, `LICENSE`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`
- Create: `core/build.gradle.kts`, `desktop/build.gradle.kts`
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/Main.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: runnable `:desktop` app and testable `:core`; `main(args)` supports `--smoke` (prints version, exits 0) and `--check-vlc` (Task 11 adds the body).

- [ ] **Step 1: Clone upstream licence and create the repo**

```powershell
cd "C:\Users\Shad Accounts\Desktop\New folder (2)\ipvio-desktop"
git init
git branch -M main
Copy-Item "..\ipvio-src\LICENSE" .\LICENSE
gh repo create badip/IPVIO-Stream-Desktop --public --source . --remote origin --description "Windows desktop port of IPVIO Stream (GPL-3.0)"
```

If the repo name already exists, stop and ask. Do not push yet.

- [ ] **Step 2: Add the Gradle wrapper**

No Gradle is installed. Download 8.14.3 once, generate the wrapper, then use `.\gradlew.bat` only.

```powershell
$g = "$env:USERPROFILE\tools\gradle-8.14.3"
if (!(Test-Path $g)) {
  curl.exe -L -o "$env:TEMP\gradle-8.14.3-bin.zip" https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
  Expand-Archive "$env:TEMP\gradle-8.14.3-bin.zip" "$env:USERPROFILE\tools" -Force
}
$env:JAVA_HOME = "C:\Users\Shad Accounts\tools\rea\jdk21"
& "$g\bin\gradle.bat" wrapper --gradle-version 8.14.3 --distribution-type bin
```

- [ ] **Step 3: Write the build files**

`gradle/libs.versions.toml`:

```toml
[versions]
kotlin = "2.2.10"
compose = "1.12.2"
coroutines = "1.10.2"
okhttp = "4.12.0"
gson = "2.11.0"
orgjson = "20250107"
vlcj = "4.12.1"
jna = "5.17.0"
junit = "4.13.2"

[libraries]
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
gson = { module = "com.google.code.gson:gson", version.ref = "gson" }
orgjson = { module = "org.json:json", version.ref = "orgjson" }
coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
coroutines-swing = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-swing", version.ref = "coroutines" }
vlcj = { module = "uk.co.caprica:vlcj", version.ref = "vlcj" }
jna-platform = { module = "net.java.dev.jna:jna-platform", version.ref = "jna" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
```

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral(); google() }
}
plugins { id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0" }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { mavenCentral(); google() }
}
rootProject.name = "IPVIO-Stream-Desktop"
include(":core", ":desktop")
```

`build.gradle.kts`:

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.compose) apply false
}
```

`gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx3g -Dfile.encoding=UTF-8
kotlin.code.style=official
```

`core/build.gradle.kts`:

```kotlin
plugins { alias(libs.plugins.kotlin.jvm) }

kotlin { jvmToolchain(21) }

dependencies {
    api(libs.okhttp)
    api(libs.gson)
    api(libs.orgjson)
    api(libs.coroutines.core)
    testImplementation(libs.junit)
}

tasks.test { useJUnit() }
```

`desktop/build.gradle.kts`:

```kotlin
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

kotlin { jvmToolchain(21) }

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(libs.coroutines.swing)
    implementation(libs.vlcj)
    implementation(libs.jna.platform)
    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "com.ipvio.stream.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.AppImage)
            packageName = "IPVIO Stream"
            packageVersion = "0.1.0"
            description = "IPTV player for your own playlists"
            vendor = "IPVIO Stream Desktop contributors"
        }
    }
}
```

`.gitignore`: `.gradle/`, `build/`, `*.iml`, `.idea/`, `local.properties`, `secrets.properties`, `*.part`, `*.m3u`, `*.m3u.gz`, `*.bin`, `tools/core-files.txt`, `.superpowers/`, `tools/sdd/`.

`README.md`: one paragraph saying this is the Windows port of IPVIO Stream, GPL-3.0, no content included, builds unsigned.

- [ ] **Step 4: Write `Main.kt`**

```kotlin
package com.ipvio.stream.desktop

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

const val VERSION = "0.1.0"

fun main(args: Array<String>) {
    if ("--smoke" in args) {
        println("IPVIO Stream desktop $VERSION")
        return
    }
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "IPVIO Stream",
            state = rememberWindowState(size = DpSize(1200.dp, 760.dp))
        ) {
            App()
        }
    }
}
```

- [ ] **Step 5: Add a placeholder `App()` so it compiles**

`desktop/src/main/kotlin/com/ipvio/stream/desktop/App.kt` — replaced in Task 8:

```kotlin
package com.ipvio.stream.desktop

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable

@Composable
fun App() {
    BasicText("IPVIO Stream $VERSION")
}
```

- [ ] **Step 6: Verify**

```powershell
$env:JAVA_HOME = "C:\Users\Shad Accounts\tools\rea\jdk21"
.\gradlew.bat :core:test :desktop:run --args="--smoke"
```

Expected: `BUILD SUCCESSFUL` and `IPVIO Stream desktop 0.1.0`. Then `.\gradlew.bat :desktop:run` opens a 1200x760 window (manual check).

- [ ] **Step 7: Commit and push**

```powershell
git add .
git commit -m "build: Gradle skeleton for core and desktop modules"
git push -u origin main
```

---

### Task 2: Move upstream pure logic into `:core`

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/` (53 files verbatim + trimmed `PlayerLogic.kt` + the `Diag` seam)
- Create: `core/src/test/kotlin/com/ipvio/stream/` (30 test files)
- Create: `core/UPSTREAM.md`

**Interfaces:**
- Consumes: nothing.
- Produces: `com.ipvio.stream` package with `Item`, `Kind`, `Episode`, `Variant`, `ParsedPlaylist`, `M3uParser`, `PlaylistCacheIO`, `XtreamLogic`, `LoadLogic`, `PlaylistLogic`, `TitleText`, `LangTagLogic`, `CatchupLogic`, `LinePump`, `QualityLogic`, `MultiLogic`, `StalkerLogic`, `TlsFallback`, `Net`, `I18nCore`, `DictA`…`DictNl5`, `ThemeMath`, `fmtTime`, and the rest of the list below.

- [ ] **Step 1: Copy the pure main files**

Every file below has zero `android.*` / `androidx.*` imports at upstream `b65e8384`, except `PlayerLogic` (Step 1c):

```
AwakeLogic CatchupLogic ChannelSearch CreditsLogic DictA DictB DictC DictD DictE DictF DictG
DictH DictI DictJ DictK DictNl1 DictNl2 DictNl3 DictNl4 DictNl5 DriveSync EpisodeLogic
I18nCore LangTagLogic LinePump ListImport LiveBundle LiveGenre LiveLogic LoadLogic MatchLogic
MenuLogic Models MultiLogic Net NewEpisodeLogic ParentalLogic PhoneImport PlaybackLogic
PlaylistCacheIO PlaylistLogic ProviderLogic QrCode RemoteLogic Round17Logic SearchLogic SettingsLogic
SportLogic StalkerLogic TapLogic ThemeMath TlsFallback XtreamLogic
```

Three files look pure but call Android-side objects, and none of them is needed in P0:
- `Trending.kt` → `Config.TMDB_KEY`, `MetaRepo`;
- `EpgFull.kt` → `Epg`, `Catchup`, `LruCache`;
- `LiveTap.kt` → `LiveSettings`.

All three stay with the Android module, together with their P1 screens.

Then add the `Diag` seam, because `TlsFallback.kt` calls `Diag.report(...)` and upstream `Diag` uses Compose runtime state:

`core/src/main/kotlin/com/ipvio/stream/Diag.kt`:

```kotlin
package com.ipvio.stream

/** Plain-JVM stand-in for upstream Diag.kt (upstream uses Compose state; :core must not). */
object Diag {
    @Volatile var lastError: String? = null
    @Volatile var lastErrorAt: Long = 0L
    @Volatile var errorCount: Long = 0L

    fun report(msg: String) {
        lastError = msg.take(160)
        lastErrorAt = System.currentTimeMillis()
        errorCount++
    }
}
```

```powershell
$src = "..\ipvio-src\app\src\main\java\com\ipvio\stream"
$dst = "core\src\main\kotlin\com\ipvio\stream"
New-Item -ItemType Directory -Force $dst | Out-Null
New-Item -ItemType Directory -Force .\tools | Out-Null
Get-Content .\tools\core-files.txt | ForEach-Object { Copy-Item "$src\$_.kt" $dst }
```

Write `tools/core-files.txt` with one base name per line from the list above (53 lines; `XtreamApi` is added in Task 5, `PlayerLogic` in Step 1c, and `Diag.kt` is written by hand in Step 1b). The file is git-ignored and can be deleted after use.

- [ ] **Step 1b: Add the `Diag` seam**

`TlsFallback.kt` calls `Diag.report(...)`, and upstream `Diag.kt` uses Compose runtime state, which `:core` must not have. Write `core/src/main/kotlin/com/ipvio/stream/Diag.kt`:

```kotlin
package com.ipvio.stream

/** Plain-JVM stand-in for upstream Diag.kt (upstream uses Compose state; :core must not). */
object Diag {
    @Volatile var lastError: String? = null
    @Volatile var lastErrorAt: Long = 0L
    @Volatile var errorCount: Long = 0L

    fun report(msg: String) {
        lastError = msg.take(160)
        lastErrorAt = System.currentTimeMillis()
        errorCount++
    }
}
```

- [ ] **Step 1c: Copy `PlayerLogic.kt` minus its media3 blocks**

`PlayerLogicTest` (21 tests) covers `fmtTime`, `fmtShort`, `fmtDelta`, `endClockSec`, `turbo*`, `cleanEpg` and `xmltvTime`, all pure. Only three blocks need media3. Copy the file, then delete exactly these, keeping everything else byte-identical:

- the six `androidx.media3.*` imports;
- `fun trackLabel(...)` through `fun collectTracks(...)` (upstream lines 99–125, including `class TrackOpt`);
- `fun httpStatusOf(...)` (lines 189–199);
- `fun playbackErrorText(...)` (lines 204–222).

`isPermanentHttp`, `speedLabel`, `PlayerLogic`, `PlayerLocale` and all time/EPG helpers stay. Record the trim in `UPSTREAM.md`. VLC has no ExoPlayer error codes, so `playbackErrorText` has no desktop meaning.

- [ ] **Step 2: Copy the tests**

```powershell
$tsrc = "..\ipvio-src\app\src\test\java\com\ipvio\stream"
$tdst = "core\src\test\kotlin\com\ipvio\stream"
New-Item -ItemType Directory -Force $tdst | Out-Null
Get-ChildItem $tsrc\*.kt | Where-Object { $_.Name -ne "CoreLogicTest.kt" } | Copy-Item -Destination $tdst
```

Then delete the `isFinalStatus_*` test method from `core/src/test/kotlin/com/ipvio/stream/PlayerBufferTest.kt`: it tests `IptvLoadErrorPolicy` from `PlayerNet.kt`, which extends media3's `DefaultLoadErrorHandlingPolicy` and stays Android-side. The `byteLimit` test in the same file is pure and stays.

Upstream has no test resources directory, so nothing else to copy.

- [ ] **Step 3: Write `core/UPSTREAM.md`**

Record: upstream repository, commit `b65e83840e29b1bf8ee1b01d2703abb29245d26c`, the 54 moved main files, the 30 moved tests, and the rule "no behavioural edits; only seams that remove an Android/Compose type are allowed and must be listed here". Add these entries:
- `CoreLogicTest.kt` stays behind because it asserts `androidx.media3` error codes.
- `Trending.kt`, `EpgFull.kt`, `LiveTap.kt` stay behind: they call `Config`, `MetaRepo`, `Epg`, `Catchup`, `LruCache` and `LiveSettings`, all Android-side. P1 screens use them.
- `Diag.kt` is a new plain-JVM file replacing upstream's Compose-state version; same fields and `report`.
- `PlayerLogic.kt` loses `trackLabel`/`TrackOpt`/`collectTracks`, `httpStatusOf` and `playbackErrorText` (media3 types only); everything else unchanged.
- `PlayerBufferTest.kt` loses its `isFinalStatus` test method (`IptvLoadErrorPolicy` is media3).

- [ ] **Step 4: Run the tests**

```powershell
$env:JAVA_HOME = "C:\Users\Shad Accounts\tools\rea\jdk21"
.\gradlew.bat :core:test
```

Expected: `BUILD SUCCESSFUL`, 30 test classes executed, zero failures. If a test fails to compile because a copied file references a Compose/Android symbol, copy the missing file only if it is also import-free; otherwise leave the file and its test out and add a line to `UPSTREAM.md` saying why.

- [ ] **Step 5: Commit**

```powershell
git add core
git commit -m "feat(core): move upstream JVM logic and its tests"
```

---

### Task 3: Settings store

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/Settings.kt`
- Test: `core/src/test/kotlin/com/ipvio/stream/SettingsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `interface Settings { fun string(key: String, def: String? = null): String?; fun int(key: String, def: Int = 0): Int; fun bool(key: String, def: Boolean = false): Boolean; fun put(key: String, value: String?); fun put(key: String, value: Int); fun put(key: String, value: Boolean); fun remove(key: String); fun flush() }`
  - `class JsonFileSettings(file: File) : Settings`.
  - `interface Codec { fun seal(plain: String): String?; fun open(token: String): String? }` and `Codec.Plain`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class SettingsTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun roundtrip_persists_across_instances() {
        val f = File(tmp.root, "settings.json")
        JsonFileSettings(f).apply {
            put("active", 1); put("lang", "en"); put("flag", true); flush()
        }
        val again = JsonFileSettings(f)
        assertEquals(1, again.int("active"))
        assertEquals("en", again.string("lang"))
        assertTrue(again.bool("flag"))
        assertEquals(7, again.int("missing", 7))
    }

    @Test fun unicode_parent_directory() {
        val dir = File(tmp.root, "José_Пользователь")
        dir.mkdirs()
        val f = File(dir, "settings.json")
        JsonFileSettings(f).apply { put("name", "Grüße"); flush() }
        assertEquals("Grüße", JsonFileSettings(f).string("name"))
    }

    @Test fun corrupt_file_falls_back_to_backup() {
        val f = File(tmp.root, "settings.json")
        JsonFileSettings(f).apply { put("name", "first"); flush() }
        JsonFileSettings(f).apply { put("name", "second"); flush() }
        f.writeText("{not json")
        assertEquals("first", JsonFileSettings(f).string("name"))
    }

    @Test fun flush_leaves_no_tmp_and_creates_backup() {
        val f = File(tmp.root, "settings.json")
        JsonFileSettings(f).apply { put("a", 1); flush() }
        JsonFileSettings(f).apply { put("a", 2); flush() }
        assertFalse(File(f.path + ".tmp").exists())
        assertTrue(File(f.path + ".bak").exists())
        assertEquals(2, JsonFileSettings(f).int("a"))
    }
}
```

Add `@get:Rule val tmp = org.junit.rules.TemporaryFolder()` and the `org.junit.rules` import.

- [ ] **Step 2: Run to verify failure**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.SettingsTest"
```

Expected: FAIL, `Unresolved reference: JsonFileSettings`.

- [ ] **Step 3: Implement `Settings.kt`**

JSON object via org.json (`JSONObject`, `JSONObject.NULL`). Behaviour: load on construction from `file`, else from `file.bak`, else empty; `flush()` copies the current valid file to `file.bak`, writes `file.tmp`, `FileOutputStream.fd.sync()`, then `Files.move(tmp, file, REPLACE_EXISTING, ATOMIC_MOVE)` falling back to `REPLACE_EXISTING` on `AtomicMoveNotSupportedException`. Missing key returns the default. `put(key, null)` removes.

`Codec.Plain` returns the input unchanged for both methods.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.SettingsTest"
```

Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```powershell
git add core
git commit -m "feat(core): file-backed settings with atomic write and backup"
```

---

### Task 4: Playlist repository

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/PlaylistRepo.kt`
- Test: `core/src/test/kotlin/com/ipvio/stream/PlaylistRepoTest.kt`

**Interfaces:**
- Consumes: `Settings`, `Codec`, `PlaylistLogic.normalizeUrl`, `LangTagLogic.encode/decode`.
- Produces:
  - `data class Playlist(val name: String, val url: String)`
  - `class PlaylistRepo(settings: Settings, codec: Codec = Codec.Plain)` with `val lists: List<Playlist>`, `val active: Int`, `fun add(name: String, url: String): Int`, `fun rename(index: Int, name: String)`, `fun remove(index: Int)`, `fun activate(index: Int)`, `fun hidden(url: String): Set<String>`, `fun setHidden(url: String, hidden: Set<String>)`, `fun hostOf(url: String): String`.
  - Settings keys: `lists` (plain JSON array), `lists_sealed` (base64 token), `active` (int), `hidden_<url.hashCode()>` (string).

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlaylistRepoTest {
    private fun repo(codec: Codec = Codec.Plain): Pair<PlaylistRepo, Settings> {
        val s = JsonFileSettings(File.createTempFile("ipvio", ".json").apply { delete(); deleteOnExit() })
        return PlaylistRepo(s, codec) to s
    }

    @Test fun add_normalizes_and_persists() {
        val (r, s) = repo()
        val i = r.add("Home", "http://example.com/list.m3u")
        assertEquals(0, i)
        s.flush()
        val again = PlaylistRepo(s)
        assertEquals(listOf(Playlist("Home", "http://example.com/list.m3u")), again.lists)
    }

    @Test fun add_rejects_invalid_url() {
        val (r, _) = repo()
        assertThrows(IllegalArgumentException::class.java) { r.add("x", "not a url") }
    }

    @Test fun duplicate_url_is_not_added_twice() {
        val (r, _) = repo()
        r.add("A", "http://example.com/a.m3u")
        assertEquals(0, r.add("B", "http://example.com/a.m3u"))
        assertEquals(1, r.lists.size)
        assertEquals("B", r.lists[0].name)
    }

    @Test fun remove_active_keeps_index_in_range() {
        val (r, s) = repo()
        r.add("A", "http://example.com/a.m3u"); r.add("B", "http://example.com/b.m3u")
        r.activate(1); r.remove(1)
        assertEquals(0, r.active)
        assertEquals("A", PlaylistRepo(s).lists[r.active].name)
    }

    @Test fun sealed_lists_are_not_plain_text() {
        val codec = object : Codec {
            override fun seal(plain: String) = "SEALED:" + plain.reversed()
            override fun open(token: String) = token.removePrefix("SEALED:").reversed()
        }
        val (r, s) = repo(codec)
        r.add("A", "http://example.com/a.m3u")
        s.flush()
        assertNull(s.string("lists"))
        assertTrue(s.string("lists_sealed")!!.startsWith("SEALED:"))
        assertEquals("http://example.com/a.m3u", PlaylistRepo(s, codec).lists[0].url)
    }

    @Test fun unseal_failure_yields_empty_list() {
        val codec = object : Codec {
            override fun seal(plain: String) = "x"
            override fun open(token: String): String? = null
        }
        val s = JsonFileSettings(File.createTempFile("ipvio", ".json"))
        s.put("lists_sealed", "garbage")
        assertTrue(PlaylistRepo(s, codec).lists.isEmpty())
    }

    @Test fun hidden_languages_roundtrip() {
        val (r, s) = repo()
        r.setHidden("http://example.com/a.m3u", setOf("de", "en"))
        assertEquals(setOf("de", "en"), PlaylistRepo(s).hidden("http://example.com/a.m3u"))
    }
}
```

Use `org.junit.Assert.*` and `org.junit.Assert.assertThrows` (JUnit 4.13).

- [ ] **Step 2: Run to verify failure**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistRepoTest"
```

Expected: FAIL, unresolved `PlaylistRepo`.

- [ ] **Step 3: Implement `PlaylistRepo.kt`**

Read order: `lists_sealed` (decode via `codec.open`, then base64 + JSON array), then `lists`. On any parse failure return an empty list; never throw at construction. `add` normalizes with `PlaylistLogic.normalizeUrl` and throws `IllegalArgumentException("Ungültige Adresse.")` when null; an existing identical URL is renamed in place and its index returned. `remove` clamps `active` to `0..size-1` (`0` when empty). Every mutator flushes immediately.

`hidden(url)` uses `settings.string("hidden_" + url.hashCode())` decoded by `LangTagLogic.decode`; null → empty set.

`hostOf(url)` returns the host of an http(s) URL via `okhttp3.HttpUrl.Companion.toHttpUrlOrNull`, else `"Playlist"`.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistRepoTest"
```

Expected: PASS (7 tests).

- [ ] **Step 5: Commit**

```powershell
git add core
git commit -m "feat(core): playlist list persistence with pluggable secret codec"
```

---

### Task 5: Xtream API on plain JVM

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/XtreamApi.kt` (copy of upstream, imports swapped)
- Create: `core/src/test/kotlin/com/ipvio/stream/TestHttpServer.kt`
- Test: `core/src/test/kotlin/com/ipvio/stream/XtreamApiTest.kt`
- Modify: `core/UPSTREAM.md`

**Interfaces:**
- Consumes: `XtreamLogic`, `PlaylistLogic`, `Net`, `org.json`.
- Produces:
  - `class XtreamApi(client: OkHttpClient, ua: String)` with `var onPhase: (String) -> Unit`, `var check: () -> Unit`, `fun verify(a: XtreamLogic.Acct)`, `fun write(a: XtreamLogic.Acct, out: Writer): Int`, `fun episodeList(a: XtreamLogic.Acct, seriesId: String): List<Episode>?`.
  - Test helper `class TestHttpServer : AutoCloseable` with `val base: String`, `fun handle(key: String, handler: (com.sun.net.httpserver.HttpExchange) -> Unit)`, `fun serve(key: String, body: String, contentType: String = "text/plain; charset=utf-8")`. A `key` is a path plus optional query (`/player_api.php?…&action=get_live_streams`); routing matches the exchange's path + query, so the same path can serve different actions.

- [ ] **Step 1: Copy and swap imports**

```powershell
Copy-Item "..\ipvio-src\app\src\main\java\com\ipvio\stream\XtreamApi.kt" "core\src\main\kotlin\com\ipvio\stream\XtreamApi.kt"
```

Replace:

```kotlin
import android.util.JsonReader
import android.util.JsonToken
```

with

```kotlin
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
```

No other changes. Gson's `JsonReader` matches the used API (`isLenient`, `peek`, `beginArray/Object`, `nextName`, `nextString`, `nextBoolean`, `skipValue`, `hasNext`).

- [ ] **Step 2: Write `TestHttpServer.kt`**

Wrap `com.sun.net.httpserver.HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)`, start it in the constructor, `base = "http://127.0.0.1:${server.address.port}"`, `close()` stops it. `serve` registers a handler writing the body with the given content type.

- [ ] **Step 3: Write the failing test**

```kotlin
class XtreamApiTest {
    private fun acct(s: TestHttpServer) = XtreamLogic.Acct(s.base, "user", "pass")

    @Test fun write_builds_m3u_from_player_api() {
        TestHttpServer().use { s ->
            s.serve("/player_api.php?username=user&password=pass", """
              {"user_info":{"auth":1,"status":"Active"}}
            """.trimIndent())
            s.serve("/player_api.php?username=user&password=pass&action=get_live_categories", """[{"category_id":"1","category_name":"News"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_live_streams",
                """[{"stream_id":101,"name":"Channel One","stream_icon":"http://logo/1.png","epg_channel_id":"c1","category_id":"1"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_vod_categories", """[{"category_id":"2","category_name":"Movies"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_vod_streams",
                """[{"stream_id":201,"name":"Film","year":"2020","container_extension":"mkv","category_id":"2"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_series_categories", """[{"category_id":"3","category_name":"Series"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_series",
                """[{"series_id":301,"name":"Show","cover":"http://logo/3.png","category_id":"3"}]""")

            val out = java.io.StringWriter()
            val n = XtreamApi(Net.base, "test").write(acct(s), out)
            val text = out.toString()
            assertEquals(3, n)
            assertTrue(text.startsWith("#EXTM3U url-tvg="))
            assertTrue(text.contains("""tvg-id="c1""""))
            assertTrue(text.contains("""group-title="News""""))
            assertTrue(text.contains("/live/user/pass/101.ts"))
            assertTrue(text.contains("/movie/user/pass/201.mkv"))
            assertTrue(text.contains("/series/user/pass/301.xtser"))
        }
    }

    @Test fun verify_rejects_dead_account() {
        TestHttpServer().use { s ->
            s.serve("/player_api.php?username=user&password=pass", """{"user_info":{"auth":0,"status":"Disabled"}}""")
            val e = assertThrows(PlaylistError::class.java) { XtreamApi(Net.base, "test").verify(acct(s)) }
            assertTrue(e.message!!.contains("Zugang abgelehnt"))
        }
    }
}
```

- [ ] **Step 4: Run to verify failure, then pass**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.XtreamApiTest"
```

First run after copying may PASS immediately; if it fails, fix only the import seam, never the parsing. Expected final: PASS (2 tests).

- [ ] **Step 5: Record the seam and commit**

Add to `core/UPSTREAM.md`: "`XtreamApi.kt`: `android.util.JsonReader/JsonToken` → `com.google.gson.stream.*`; no logic change."

```powershell
git add core
git commit -m "feat(core): Xtream player_api client on plain JVM"
```

---

### Task 6: Playlist downloader with resume and Xtream fallback

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/PlaylistDownloader.kt`
- Test: `core/src/test/kotlin/com/ipvio/stream/PlaylistDownloaderTest.kt`

**Interfaces:**
- Consumes: `Net`, `LoadLogic`, `PlaylistLogic`, `XtreamLogic`, `XtreamApi`, `TestHttpServer`.
- Produces:
  - `class PlaylistDownloader(dir: File, client: OkHttpClient = Net.base, ua: String = UA)` with `companion object { const val UA = "IPVIO-Stream-Desktop/0.1" }`
  - `data class Progress(val read: Long, val total: Long)`
  - `fun fetch(url: String, onPhase: (String) -> Unit = {}, onProgress: (Progress) -> Unit = {}, check: () -> Unit = {}): File` returning the gzip file
  - `fun fileFor(url: String): File`, `fun delete(url: String)`
  - Files in `dir`: `pl_<url.hashCode()>.m3u.gz`, `.part`, `.info`.

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlaylistDownloaderTest {
    private val m3u = """
        #EXTM3U
        #EXTINF:-1 group-title="News",One
        http://example.com/1.ts
        #EXTINF:-1 group-title="Movies",Film (2020)
        http://example.com/film.mp4
    """.trimIndent()

    private fun gz(f: File): String = GZIPInputStream(f.inputStream()).bufferedReader().readText()

    @Test fun download_writes_gzip_m3u() {
        TestHttpServer().use { s ->
            s.serve("/list.m3u", m3u)
            val d = PlaylistDownloader(Files.createTempDirectory("pl").toFile())
            val f = d.fetch(s.base + "/list.m3u")
            assertTrue(f.name.endsWith(".m3u.gz"))
            assertEquals(m3u, gz(f).trim())
        }
    }

    @Test fun resume_appends_when_server_supports_range() {
        TestHttpServer().use { s ->
            val bytes = m3u.toByteArray()
            s.handle("/r.m3u") { ex ->
                val from = (ex.requestHeaders.getFirst("Range") ?: "").removePrefix("bytes=").substringBefore("-").toIntOrNull()
                ex.responseHeaders.add("ETag", "\"v1\"")
                if (from != null) {
                    ex.responseHeaders.add("Content-Range", "bytes $from-${bytes.size - 1}/${bytes.size}")
                    ex.sendResponseHeaders(206, (bytes.size - from).toLong())
                    ex.responseBody.write(bytes, from, bytes.size - from)
                } else {
                    ex.sendResponseHeaders(200, bytes.size.toLong())
                    ex.responseBody.write(bytes)
                }
                ex.close()
            }
            val dir = Files.createTempDirectory("pl").toFile()
            val d = PlaylistDownloader(dir)
            val part = File(dir, "pl_${(s.base + "/r.m3u").hashCode()}.part")
            val cut = 40
            part.writeBytes(bytes.copyOf(cut))
            File(part.path.replace(".part", ".info")).writeText(LoadLogic.PartInfo((s.base + "/r.m3u").hashCode(), "\"v1\"", null, "UTF-8").encode())
            assertEquals(m3u, gz(d.fetch(s.base + "/r.m3u")).trim())
        }
    }

    @Test fun restart_when_server_ignores_range() {
        TestHttpServer().use { s ->
            s.serve("/x.m3u", m3u)
            val dir = Files.createTempDirectory("pl").toFile()
            val d = PlaylistDownloader(dir)
            val url = s.base + "/x.m3u"
            File(dir, "pl_${url.hashCode()}.part").writeText("garbage-prefix")
            File(dir, "pl_${url.hashCode()}.info").writeText(LoadLogic.PartInfo(url.hashCode(), "\"v1\"", null, "UTF-8").encode())
            assertEquals(m3u, gz(d.fetch(url)).trim())
        }
    }

    @Test fun html_page_is_rejected() {
        TestHttpServer().use { s ->
            s.serve("/bad.m3u", "<html><body>error</body></html>", "text/html")
            val d = PlaylistDownloader(Files.createTempDirectory("pl").toFile())
            val e = assertThrows(PlaylistError::class.java) { d.fetch(s.base + "/bad.m3u") }
            assertTrue(e.message!!.contains("keine gültige Playlist"))
        }
    }

    @Test fun xtream_get_php_disabled_uses_api() {
        TestHttpServer().use { s ->
            s.serve("/get.php?username=user&password=pass&type=m3u_plus&output=ts", "<html>disabled</html>", "text/html")
            s.serve("/player_api.php?username=user&password=pass", """{"user_info":{"auth":1}}""")
            s.serve("/player_api.php?username=user&password=pass&action=get_live_categories", """[{"category_id":"1","category_name":"News"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_live_streams",
                """[{"stream_id":1,"name":"One","category_id":"1"}]""")
            s.serve("/player_api.php?username=user&password=pass&action=get_vod_categories", "[]")
            s.serve("/player_api.php?username=user&password=pass&action=get_vod_streams", "[]")
            s.serve("/player_api.php?username=user&password=pass&action=get_series_categories", "[]")
            s.serve("/player_api.php?username=user&password=pass&action=get_series", "[]")
            val url = s.base + "/get.php?username=user&password=pass&type=m3u_plus&output=ts"
            val text = gz(PlaylistDownloader(Files.createTempDirectory("pl").toFile()).fetch(url))
            assertTrue(text.contains("/live/user/pass/1.ts"))
        }
    }

    @Test fun error_never_leaks_credentials() {
        TestHttpServer().use { s ->
            s.serve("/get.php?username=SECRETUSER&password=SECRETPASS&type=m3u_plus&output=ts", "denied")
            val url = s.base + "/get.php?username=SECRETUSER&password=SECRETPASS&type=m3u_plus&output=ts"
            val e = assertThrows(PlaylistError::class.java) {
                PlaylistDownloader(Files.createTempDirectory("pl").toFile()).fetch(url)
            }
            assertFalse(e.message!!.contains("SECRET"))
        }
    }
}
```

`s.serve` matches the full request URI (path + query) against the registered key; a request with an unregistered query returns 404.

- [ ] **Step 2: Run to verify failure**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistDownloaderTest"
```

Expected: FAIL, unresolved `PlaylistDownloader`.

- [ ] **Step 3: Implement `PlaylistDownloader.kt`**

Rules:
- `fetch`: if `pl_<h>.m3u.gz` exists and length > 0 → return it.
- Read `.info` as `LoadLogic.PartInfo`; build resume headers with `LoadLogic.resumeHeaders(part.length(), etag, lastModified)`.
- Request with `User-Agent: ua`. On a response, `LoadLogic.resumeAction(code, Content-Range, part.length())`: `APPEND` → append, `RESTART` → truncate and write, `ERROR` → throw `PlaylistError(PlaylistLogic.httpError(code))`.
- Retry up to 3 attempts on `PlaylistLogic.retryable`, sleeping 3 s via `Thread.sleep` between attempts. Progress callback after each 64 KiB buffer.
- If the URL parses as Xtream (`XtreamLogic.fromUrl`), download `get.php` first, read the first 512 bytes, and if `!XtreamLogic.looksLikeM3u(head)` open a fresh `.part` and use `XtreamApi(client, ua).write(acct, writer)`. API count `0` → `PlaylistError("Keine Sender in der Playlist gefunden.")`.
- A `file:///` URL (imported M3U file) is copied unchanged into `.part`; no HTTP request, no resume. Its charset is UTF-8.
- Stalker URLs (`StalkerLogic.parse(url) != null`) throw `PlaylistError("Stalker-Portale werden noch nicht unterstützt.")`.
- After the `.part` is complete, read its first 512 bytes. `!XtreamLogic.looksLikeM3u(head)` → throw `PlaylistError("Die Adresse liefert keine gültige Playlist (keine #EXTM3U-Zeile).")` and delete the `.part`. This is what catches a provider that answers HTTP 200 with an HTML error page.
- Record the response charset in `.info` (`LoadLogic.PartInfo(h, etag, lastModified, charset)`) after every response, where charset comes from `body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8`. API and file-import paths write UTF-8.
- Gzip step transcodes: read `.part` with the recorded charset, write through `OutputStreamWriter(GZIPOutputStream(tmp), UTF_8)`. `.m3u.gz` is therefore always UTF-8 and the parser can read it with `InputStreamReader(..., UTF_8)` (same as upstream `PlaylistStore.readFile`). Then rename to `.m3u.gz`, delete `.part` and `.info`.
- Never put `url`, `user` or `pass` into an exception message; wrap all failures in `PlaylistError(PlaylistLogic.friendlyError(cause))`.
- `check()` is called every 200 buffers and every 1000 API entries.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistDownloaderTest"
```

Expected: PASS (6 tests).

- [ ] **Step 5: Commit**

```powershell
git add core
git commit -m "feat(core): resumable playlist download with Xtream API fallback"
```

---

### Task 7: Playlist service — scan, language filter, binary cache

**Files:**
- Create: `core/src/main/kotlin/com/ipvio/stream/PlaylistService.kt`
- Test: `core/src/test/kotlin/com/ipvio/stream/PlaylistServiceTest.kt`

**Interfaces:**
- Consumes: `PlaylistDownloader`, `M3uParser.scanStream/parseStream`, `PlaylistCacheIO`, `LoadLogic`, `LangTagLogic`.
- Produces:
  - `class PlaylistService(dir: File, downloader: PlaylistDownloader = PlaylistDownloader(dir))` with nested `data class Scan(val counts: Map<String, Int>, val bytes: Map<String, Long>, val entries: Int, val fromCache: Boolean)` and `data class Loaded(val items: List<Item>, val epgUrls: List<String>, val counts: Map<String, Int>)`
  - `fun prepare(url: String, onPhase: (String) -> Unit = {}, check: () -> Unit = {}): Scan` — binary-cache header when present, otherwise download + full scan.
  - `fun load(url: String, hidden: Set<String> = emptySet(), onPhase: (String) -> Unit = {}, check: () -> Unit = {}): Loaded` — binary cache when its stored `hidden` equals `hidden`, else parse the gzip text and write the cache.
  - `fun cachedHeader(url: String): PlaylistCacheIO.Cached?`, `fun delete(url: String)`, plus `internal fun fileForTest(url: String): File` and `internal fun cacheForTest(url: String): File` for the tests.

- [ ] **Step 1: Write the failing tests**

```kotlin
class PlaylistServiceTest {
    private val m3u = """
        #EXTM3U url-tvg="http://example.com/epg.xml"
        #EXTINF:-1 group-title="News | DE",DE Eins
        http://example.com/de1.ts
        #EXTINF:-1 group-title="News | EN",EN One
        http://example.com/en1.ts
        #EXTINF:-1 group-title="Movies",Film (2020)
        http://example.com/f.mp4
    """.trimIndent()

    private val servers = ArrayList<TestHttpServer>()

    @After fun stop() = servers.forEach { it.close() }

    private fun service(body: String = m3u): Pair<PlaylistService, String> {
        val s = TestHttpServer().also { servers.add(it) }
        s.serve("/list.m3u", body)
        return PlaylistService(Files.createTempDirectory("svc").toFile()) to s.base + "/list.m3u"
    }

    @Test fun prepare_counts_languages() {
        val (svc, url) = service().let { (a, b) -> a to b }
        val scan = svc.prepare(url)
        assertTrue(scan.counts.getValue("de") >= 1)
        assertTrue(scan.counts.getValue("en") >= 1)
        assertTrue(scan.counts.getValue(LangTagLogic.UNKNOWN) >= 1)
        assertEquals(3, scan.entries)
        assertFalse(scan.fromCache)
    }

    @Test fun load_filters_hidden_languages() {
        val (svc, url) = service()
        val all = svc.load(url)
        val onlyDe = svc.load(url, hidden = setOf("en", LangTagLogic.UNKNOWN))
        assertEquals(3, all.items.size)
        assertEquals(1, onlyDe.items.size)
        assertEquals("DE Eins", onlyDe.items[0].title)
    }

    @Test fun second_load_uses_binary_cache() {
        val (svc, url) = service()
        val first = svc.load(url)
        svc.fileForTest(url).delete()                     // remove the gzip text
        val second = svc.load(url)
        assertEquals(first.items.map { it.id }, second.items.map { it.id })
    }

    @Test fun corrupt_binary_cache_is_reparsed() {
        val (svc, url) = service()
        svc.load(url)
        svc.cacheForTest(url).writeText("broken")
        assertEquals(3, svc.load(url).items.size)
    }

    @Test fun header_is_reused_by_prepare() {
        val (svc, url) = service()
        svc.load(url)
        assertTrue(svc.prepare(url).fromCache)
    }
}
```

Expose `internal fun fileForTest(url: String): File` and `internal fun cacheForTest(url: String): File` in `PlaylistService` so the test can delete/truncate files. Add `import org.junit.After` for the server cleanup above.

- [ ] **Step 2: Run to verify failure**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistServiceTest"
```

Expected: FAIL, unresolved `PlaylistService`.

- [ ] **Step 3: Implement `PlaylistService.kt`**

- Gzip text: open with `GZIPInputStream`, wrap in `InputStreamReader(UTF_8)` and `M3uParser.parseStream(reader, hidden, check)`.
- Scan: `M3uParser.scanStream(reader, check)`.
- Binary cache path: `pl_<h>.bin`; write via a `.tmp` then rename (same pattern as upstream `writeCache`), calling `PlaylistCacheIO.write(out, url, items, epgUrls, emptyMap(), counts, bytes, hidden)`. The empty order map only loses category ordering (P1 feature); note this with a `shortcut:` comment.
- `prepare`: `PlaylistCacheIO.readHeader` on the bin; return counts/bytes/entries (`counts.values.sum()`) with `fromCache = true`. If the header has no counts (a version 3/4 cache) or `entries == 0`, ignore it and run the download + scan path instead, so language preselect always has data.
- `load`: read bin with `PlaylistCacheIO.read(input, url, hidden, check)`; accept only when `cached.hidden == hidden`; otherwise parse gzip and write the bin with the parse's `counts`, `bytes`, `hidden`.
- `fileForTest(url)` returns the `.m3u.gz`; `cacheForTest(url)` returns the `.bin`.
- `delete`: remove `.bin`, `.m3u.gz`, `.part`, `.info` and their `.tmp` siblings.
- A damaged `.bin` is a cache miss, not an error: catch the `IOException` from `PlaylistCacheIO.read`, delete the file, and continue with the gzip parse. A missing `.m3u.gz` after the download step is an error (`PlaylistError(PlaylistLogic.friendlyError(e))`), and a write failure surfaces as `PlaylistError` too.

- [ ] **Step 4: Run tests**

```powershell
.\gradlew.bat :core:test --tests "com.ipvio.stream.PlaylistServiceTest"
```

Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```powershell
git add core
git commit -m "feat(core): playlist scan, language filter and binary cache"
```

---

### Task 8: Desktop shell, theme, text and DPAPI

**Files:**
- Create: `desktop/src/main/kotlin/com/ipvio/stream/Look.kt`, `DesignK.kt`, `Ui.kt`, `Lang.kt` (copied), `General.kt` (new)
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/AppDirs.kt`, `Dpapi.kt`, `AppState.kt`
- Modify: `desktop/src/main/kotlin/com/ipvio/stream/desktop/App.kt`, `Main.kt`
- Test: `desktop/src/test/kotlin/com/ipvio/stream/desktop/DpapiTest.kt`

**Interfaces:**
- Consumes: `:core`, `Look`/`Ui`/`Design`/`Lang`, `Codec`, `Settings`, `PlaylistRepo`, `PlaylistService`.
- Produces:
  - `object AppDirs { val root: File; val settings: File; val playlists: File; val imports: File }`
  - `object Dpapi : Codec`
  - `class AppState(settings: Settings, repo: PlaylistRepo, service: PlaylistService)` with `var items: List<Item>`, `var kind: Kind`, `var category: String?`, `var phase: String`, `var busy: Boolean`, `var error: String?`, `var showAdd: Boolean`, `var playing: Pair<Item, Episode?>?`, `val scope: CoroutineScope`, `var cancelCheck: (() -> Unit)?`.
  - `@Composable fun App(state: AppState)`

- [ ] **Step 1: Copy the theme and text files**

```powershell
$src = "..\ipvio-src\app\src\main\java\com\ipvio\stream"
$dst = "desktop\src\main\kotlin\com\ipvio\stream"
Copy-Item "$src\Look.kt","$src\DesignK.kt","$src\Ui.kt","$src\Lang.kt" $dst
```

No edits are needed in the copied files. `Lang.kt` references `General.lang` and `General.fontFamily()`; `Ui.kt` references `SidebarPrefs.labels`. Both come from the new `General.kt`:

```kotlin
package com.ipvio.stream

import androidx.compose.ui.text.font.FontFamily

/** Desktop stand-ins for the Android settings objects used by Lang.kt and Ui.kt. */
object General {
    /** "" = system language; a code from I18nCore.CODES otherwise. */
    var lang: String = ""
    fun fontFamily(): FontFamily? = null
}

/** Only Ui.kt's SidebarW reads this; the P0 layout has no sidebar. */
object SidebarPrefs {
    var labels: Boolean = true
}
```

If `Ui.kt` fails to compile on desktop, replace only the failing import/overload (record it in the commit message). Do not rewrite the file.

- [ ] **Step 2: Write the DPAPI test**

```kotlin
class DpapiTest {
    @Test fun seal_open_roundtrip() {
        val token = Dpapi.seal("http://host/get.php?username=u&password=p")!!
        assertEquals("http://host/get.php?username=u&password=p", Dpapi.open(token))
        assertFalse(token.contains("username=u"))
    }

    @Test fun open_rejects_garbage() {
        assertNull(Dpapi.open("not-base64"))
    }
}
```

- [ ] **Step 3: Implement `Dpapi.kt` and `AppDirs.kt`**

`Dpapi`: `com.sun.jna.platform.win32.Crypt32Util.cryptProtectData/cryptUnprotectData` over UTF-8 bytes, Base64 for the token, every failure → `null`.

```kotlin
object AppDirs {
    val root = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "IPVIO Stream")
    val settings = File(root, "settings.json")
    val playlists = File(root, "playlists")
    val imports = File(root, "files/imports")
}
```

Create all four directories in `main()` before use.

- [ ] **Step 4: Implement `AppState.kt` and replace `App.kt`**

`AppState` holds the five `mutableStateOf` fields and `fun project(kind: Kind): List<Item>` returning items of that kind. `App` composes:

```
Row {
  Column(width 240.dp) { title "IPVIO Stream"; playlist list (radio-style Text rows); FocusButton("Playlist hinzufügen") }
  Box(weight 1f) { if (state.showAdd) AddPlaceholder else BrowsePlaceholder }
}
```

Use `Bg`, `Surf`, `Accent1`, `Muted`, `DS`, `Text(...)` from the copied theme. For this task the two placeholders are `Text("Noch keine Playlist")` and the three kind tabs; Task 9 and 10 replace them.

`AppState`:

```kotlin
class AppState(
    val settings: Settings,
    val repo: PlaylistRepo,
    val service: PlaylistService
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    var items by mutableStateOf<List<Item>>(emptyList())
    var kind by mutableStateOf(Kind.LIVE)
    var category by mutableStateOf<String?>(null)
    var phase by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var showAdd by mutableStateOf(repo.lists.isEmpty())
    var playing by mutableStateOf<Pair<Item, Episode?>?>(null)
    var cancelCheck: (() -> Unit)? = null
}
```

`Main.kt`:

```kotlin
fun main(args: Array<String>) {
    if ("--smoke" in args) { println("IPVIO Stream desktop $VERSION"); return }
    AppDirs.root.mkdirs(); AppDirs.playlists.mkdirs(); AppDirs.imports.mkdirs()
    val settings = JsonFileSettings(AppDirs.settings)
    val state = AppState(settings, PlaylistRepo(settings, Dpapi), PlaylistService(AppDirs.playlists))
    application { Window(onCloseRequest = ::exitApplication, title = "IPVIO Stream", state = rememberWindowState(size = DpSize(1280.dp, 800.dp))) { App(state) } }
}
```

- [ ] **Step 5: Run the check**

```powershell
.\gradlew.bat :desktop:test --tests "com.ipvio.stream.desktop.DpapiTest"
.\gradlew.bat :desktop:run --args="--smoke"
```

Expected: DPAPI tests PASS (2), smoke prints the version. Manual: `.\gradlew.bat :desktop:run` shows the rail and the empty state in the upstream colours.

- [ ] **Step 6: Commit**

```powershell
git add desktop
git commit -m "feat(desktop): theme, translated text, app shell and DPAPI codec"
```

---

### Task 9: Add-playlist screen with progress and language dialog

**Files:**
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/AddPlaylistScreen.kt`
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/LanguageDialog.kt`
- Modify: `desktop/src/main/kotlin/com/ipvio/stream/desktop/App.kt`, `AppState.kt`

**Interfaces:**
- Consumes: `PlaylistService.prepare/load`, `PlaylistRepo.add/setHidden/activate/hostOf`, `PlaylistLogic.xtreamUrl/normalizeUrl/friendlyError`, `LoadLogic.fits/preselect/budget`.
- Produces:
  - `@Composable fun AddPlaylistScreen(state: AppState)`
  - `@Composable fun LanguageDialog(counts: Map<String, Int>, bytes: Map<String, Long>, budget: Long, onConfirm: (Set<String>) -> Unit)`
  - `AppState` gains `var ask: Ask?`, `fun addAndOpen(name: String, url: String)`, `fun confirmLanguages(shown: Set<String>)`, `fun cancelLoad()`.

- [ ] **Step 1: Implement the screen**

Fields (all through the copied `Field`/`FocusButton`):
- Name (optional; default `PlaylistRepo` derives it from the host).
- Mode toggle: "Xtream Codes" vs "M3U-Adresse".
- Xtream: server, username, password (hidden by default).
- M3U: URL.
- A Stalker note: "Stalker-Portale folgen in Version 0.2." with the MAC fields disabled.
- Buttons: "Hinzufügen", "Abbrechen". While busy: phase text, bytes read/total in MB, a Cancel button.

`AppState.addAndOpen` (two-phase, because the language dialog must sit between scan and load):

```kotlin
class Ask(val url: String, val name: String, val scan: PlaylistService.Scan, val budget: Long)

var ask by mutableStateOf<Ask?>(null)

fun addAndOpen(name: String, url: String) {
    busy = true; error = null; cancelCheck = null
    scope.launch(Dispatchers.IO) {
        try {
            val normalized = PlaylistLogic.normalizeUrl(url) ?: throw PlaylistError("Ungültige Adresse.")
            val scan = service.prepare(normalized, { phase = it }, { cancelCheck?.invoke() })
            val budget = LoadLogic.budget(Runtime.getRuntime().maxMemory())
            withContext(Dispatchers.Main) {
                busy = false
                if (!LoadLogic.fits(scan.bytes, emptySet(), budget) && scan.counts.size > 1) {
                    ask = Ask(normalized, name, scan, budget)
                } else {
                    open(normalized, name, emptySet(), scan)
                }
            }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { error = PlaylistLogic.friendlyError(e); busy = false }
        }
    }
}

/** Called by the language dialog with the languages to show. */
fun confirmLanguages(shown: Set<String>) {
    val a = ask ?: return
    ask = null; busy = true
    val hidden = a.scan.counts.keys - shown
    scope.launch(Dispatchers.IO) {
        try {
            val loaded = service.load(a.url, hidden, { phase = it }, { cancelCheck?.invoke() })
            withContext(Dispatchers.Main) { openLoaded(a.url, a.name, hidden, loaded) }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { error = PlaylistLogic.friendlyError(e); busy = false }
        }
    }
}

private fun open(url: String, name: String, hidden: Set<String>, scan: PlaylistService.Scan) {
    busy = true
    scope.launch(Dispatchers.IO) {
        try {
            val loaded = service.load(url, hidden, { phase = it }, { cancelCheck?.invoke() })
            withContext(Dispatchers.Main) { openLoaded(url, name, hidden, loaded) }
        } catch (e: Throwable) {
            withContext(Dispatchers.Main) { error = PlaylistLogic.friendlyError(e); busy = false }
        }
    }
}

private fun openLoaded(url: String, name: String, hidden: Set<String>, loaded: PlaylistService.Loaded) {
    val i = repo.add(if (name.isBlank()) repo.hostOf(url) else name, url)
    repo.activate(i); repo.setHidden(url, hidden)
    items = loaded.items; showAdd = false; busy = false; cancelCheck = null
}

fun cancelLoad() { cancelCheck = { throw PlaylistError("Abgebrochen.") } }
```

`AddPlaylistScreen` calls `state.addAndOpen(name, url)`; the screen renders `state.ask?.let { a -> LanguageDialog(a.scan.counts, a.scan.bytes, a.budget) { shown -> state.confirmLanguages(shown) } }`. `AppState` already has `val scope` and `var cancelCheck` from Task 8. The Cancel button calls `state.cancelLoad()`, and each run clears `cancelCheck` at the start. `PlaylistRepo.hostOf` comes from Task 4: `fun hostOf(url: String): String`, parsing the host with `okhttp3.HttpUrl.Companion.toHttpUrlOrNull` and falling back to `"Playlist"` (a `file:///` URL yields `"Playlist"`).

Xtream input: the screen builds the URL with `PlaylistLogic.xtreamUrl(server, user, pass)` and shows `PlaylistError("Bitte Server, Benutzername und Passwort ausfüllen.")` when it returns null; M3U mode passes the pasted URL. `LanguageDialog` pre-selects `LoadLogic.preselect(counts, bytes, I18nCore.CODES[Lang.index], budget)`, shows per-language counts and a memory estimate, and confirms with the shown set. `confirmLanguages` with an empty set would hide everything, so the dialog does not let the user confirm zero languages.

M3U file import: a "Datei wählen" button opens `java.awt.FileDialog`, copies the chosen file to `AppDirs.imports` (created in Task 8) with the original name, and puts `"file:///" + target.absolutePath.replace('\\', '/')` into the URL field. `PlaylistLogic.normalizeUrl` accepts it because the path contains `/files/imports/` and ends in `.m3u`.

- [ ] **Step 2: Wire it into `App`**

`App` shows `AddPlaylistScreen(state)` when `state.showAdd` or `state.repo.lists.isEmpty()`, otherwise the browse placeholder with a "Playlist hinzufügen" button.

- [ ] **Step 3: Verify**

```powershell
.\gradlew.bat :desktop:compileKotlin
.\gradlew.bat :desktop:run
```

Manual: add an M3U URL; watch phase + byte progress; on a large list confirm the language dialog appears; confirm errors show without the URL. (Automated paths are covered by Tasks 6–7.)

- [ ] **Step 4: Commit**

```powershell
git add desktop
git commit -m "feat(desktop): add playlist flow with progress and language choice"
```

---

### Task 10: Browse list

**Files:**
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/BrowseScreen.kt`
- Modify: `desktop/src/main/kotlin/com/ipvio/stream/desktop/App.kt`, `AppState.kt`

**Interfaces:**
- Consumes: `PlaylistService`, `PlaylistRepo`, `Item`, `Kind`.
- Produces: `@Composable fun BrowseScreen(state: AppState, onPlay: (Item, Episode?) -> Unit)`; `AppState` gains `fun reload(index: Int)`, `fun categories(kind: Kind): List<String>`, `fun visible(kind: Kind, category: String?): List<Item>`.

- [ ] **Step 1: Implement `reload`/derived lists**

`reload(index)`: activate, `busy = true`, IO: `service.prepare(url)` then `service.load(url, repo.hidden(url))`, set `items`, `busy = false`, errors via `friendlyError`. Called on startup for the saved active playlist and on the Reload button.

`categories(kind)`: distinct `group` in first-appearance order for that kind.
`visible(kind, category)`: filter items by kind and (category == null || group == category), sorted by title.

- [ ] **Step 2: Implement the screen**

Layout:
- Top row: three tab buttons "Live", "Filme", "Serien"; a search-free filter note; a "Neu laden" button.
- Left column (200.dp): "Alle" + category names, scrollable.
- Right: `LazyColumn` of rows; each row shows `Text(item.title, raw = true)`, quality badge text (`item.quality`), year for movies, and for series the season count via `seasonCount(item)`.
- Click → `onPlay(item, null)` for live/movie; for series open an episode picker list inline (seasons → episodes) and call `onPlay(item, episode)` with `pickVariant(ep, null, null, null)` resolved in Task 12.
- Playlist rail: each stored playlist is a row; clicking it calls `repo.activate(i)` then `state.reload(i)`. A small "Entfernen" action removes a non-active playlist after a confirm dialog. The active row is highlighted with `Accent1`.

- [ ] **Step 3: Verify**

```powershell
.\gradlew.bat :desktop:run
```

Manual: switch tabs, pick a category, confirm items and series episode expansion. No playback yet.

- [ ] **Step 4: Commit**

```powershell
git add desktop
git commit -m "feat(desktop): live, movie and series list with categories"
```

---

### Task 11: libVLC runtime and player engine

**Files:**
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/player/PlayerEngine.kt`, `VlcRuntime.kt`, `VlcPlayerEngine.kt`
- Modify: `desktop/build.gradle.kts`, `desktop/src/main/kotlin/com/ipvio/stream/desktop/Main.kt`

**Interfaces:**
- Consumes: vlcj.
- Produces:
  - `interface PlayerEngine { fun play(url: String); fun pause(); fun resume(); fun stop(); fun seek(ms: Long); fun setVolume(percent: Int); val positionMs: Long; val durationMs: Long; val playing: Boolean; fun release() }` in package `com.ipvio.stream.desktop.player`
  - `object VlcRuntime { fun locate(): File; fun init(dir: File) }`
  - `class VlcPlayerEngine(vlcDir: File) : PlayerEngine` plus `fun attach(canvas: java.awt.Canvas)` and `fun setMute(muted: Boolean)`.

- [ ] **Step 1: Add the VLC download task**

Append to `desktop/build.gradle.kts` (top-level, after `compose.desktop`):

```kotlin
val vlcVersion = "3.0.24"
val vlcDir = layout.buildDirectory.dir("vlc")
val vlcZip = layout.buildDirectory.file("vlc.zip")

val downloadVlc by tasks.registering {
    outputs.dir(vlcDir)
    doLast {
        val out = vlcDir.get().asFile
        if (File(out, "libvlc.dll").exists()) return@doLast
        val zip = vlcZip.get().asFile
        if (!zip.exists()) {
            java.net.URI("https://get.videolan.org/vlc/$vlcVersion/win64/vlc-$vlcVersion-win64.zip")
                .toURL().openStream().use { input -> zip.outputStream().use { input.copyTo(it) } }
        }
        out.mkdirs()
        java.util.zip.ZipInputStream(zip.inputStream().buffered()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                val name = e.name.substringAfter('/')
                if (e.isDirectory || name.isEmpty()) continue
                if (!(name == "libvlc.dll" || name == "libvlccore.dll" || name.startsWith("plugins/"))) continue
                val f = File(out, name)
                f.parentFile.mkdirs()
                f.outputStream().use { z.copyTo(it) }
            }
        }
    }
}

tasks.named("run") { dependsOn(downloadVlc) }

compose.desktop.application {
    jvmArgs += "-Dipvio.vlc=" + vlcDir.get().asFile.absolutePath
}
```

- [ ] **Step 2: Implement `VlcRuntime`**

`locate()` order: system property `ipvio.vlc`, `VLC_HOME` env, `compose.application.resources.dir/vlc`, `C:\Program Files\VideoLAN\VLC`, `C:\Program Files (x86)\VideoLAN\VLC`. Missing → throw `IllegalStateException` with the download URL and the `--check-vlc` hint.

`init(dir)`: set `jna.library.path` to `dir.absolutePath` and `VLC_PLUGIN_PATH` to `File(dir, "plugins").absolutePath` **before** any vlcj class loads, then verify with `uk.co.caprica.vlcj.factory.discovery.NativeDiscovery().discover()`. Do not call `LibVlc()` if discovery fails; throw the actionable error instead.

- [ ] **Step 3: Implement `VlcPlayerEngine`**

Hold `MediaPlayerFactory`, `EmbeddedMediaPlayer`, `CanvasVideoSurface`. The constructor calls `VlcRuntime.init(vlcDir)`. `play(url)` sets `media().prepare(url)`, `controls().start()`. `pause/resume` via `controls().setPause`. `seek(ms)` via `controls().setTime(ms)`. `setVolume` 0..100. `attach(canvas)` creates the surface. `release()` stops and factory releases. Live streams: set `:network-caching=1500` via `media().play(url, ":network-caching=1500")`.

- [ ] **Step 4: Implement `--check-vlc`**

```kotlin
if ("--check-vlc" in args) {
    val dir = VlcRuntime.locate()
    VlcRuntime.init(dir)
    val factory = MediaPlayerFactory()
    try { println("libVLC " + factory.info().version()) } finally { factory.release() }
    return
}
```

- [ ] **Step 5: Verify**

```powershell
.\gradlew.bat :desktop:run --args="--check-vlc"
```

Expected: `libVLC 3.0.24`, exit 0. `desktop/build/vlc/libvlc.dll` exists.

- [ ] **Step 6: Commit**

```powershell
git add desktop
git commit -m "feat: VLC runtime provisioning and player engine"
```

---

### Task 12: Player window

**Files:**
- Create: `desktop/src/main/kotlin/com/ipvio/stream/desktop/player/PlayerWindow.kt`
- Modify: `desktop/src/main/kotlin/com/ipvio/stream/desktop/App.kt`, `AppState.kt`

**Interfaces:**
- Consumes: `VlcPlayerEngine`, `pickVariant`, `Item`, `Episode`.
- Produces: `@Composable fun PlayerWindow(state: AppState, item: Item, episode: Episode?, onClose: () -> Unit)`; `AppState` gains `var playing: Pair<Item, Episode?>?`.

- [ ] **Step 1: Implement the window**

- Resolve the URL: `episode?.let { pickVariant(it, null, null, null).url } ?: item.url`.
- `val engine = remember { VlcPlayerEngine(VlcRuntime.locate()) }`; the constructor runs `VlcRuntime.init(dir)` before any vlcj class loads.
- `Window(onCloseRequest = { engine.release(); state.playing = null }, title = item.title, state = rememberWindowState(size = DpSize(1100.dp, 640.dp)))`.
- Video: `SwingPanel(factory = { Canvas().also { c -> engine.attach(c); engine.play(url) } }, modifier = Modifier.weight(1f).fillMaxWidth())`.
- Controls row: play/pause toggle, seek slider bound to `positionMs/durationMs` refreshed by a 500 ms `LaunchedEffect` (hidden for live: `item.kind == Kind.LIVE`), volume slider, mute, fullscreen toggle (`window.placement = WindowPlacement.Fullscreen` / `WindowPlacement.Floating`), close.
- Error: vlcj `MediaPlayerEventListener.error` sets `state.error = PlaylistLogic.friendlyError(...)`, shown above the controls.
- Release on close and on `DisposableEffect` exit.

- [ ] **Step 2: Wire click to play**

`App` keeps `state.playing`; when non-null render `PlayerWindow`. `BrowseScreen` calls `onPlay(item, episode)`.

- [ ] **Step 3: Verify**

```powershell
.\gradlew.bat :desktop:run --args="--smoke"
.\gradlew.bat :desktop:run
```

Manual: add the user's Xtream account, play a live channel and a movie; pause, seek, change volume, fullscreen, close. Play a series episode through the picker.

- [ ] **Step 4: Commit**

```powershell
git add desktop
git commit -m "feat(desktop): VLC player window with controls"
```

---

### Task 13: Startup restore and acceptance run

**Files:**
- Modify: `desktop/src/main/kotlin/com/ipvio/stream/desktop/Main.kt`, `App.kt`, `AppState.kt`

**Interfaces:**
- Consumes: `PlaylistRepo.lists/active/hidden`, `PlaylistService.prepare/load`.
- Produces: startup path that restores the last playlist and its language filter without downloading again.

- [ ] **Step 1: Restore on start**

In `main`, after constructing the state: if `repo.lists.isNotEmpty()`, call `state.reload(repo.active)` on `Dispatchers.IO` and keep `showAdd = false`.

- [ ] **Step 2: Verify persistence**

```powershell
.\gradlew.bat :desktop:run
```

Manual: add the playlist, quit, start again. Expected: lists appear from `%LOCALAPPDATA%\IPVIO Stream\playlists\pl_*.bin` without the download phase. Delete the `.bin`, restart: list rebuilds from `.m3u.gz` without network. Delete both: full download.

- [ ] **Step 3: Run the whole suite**

```powershell
.\gradlew.bat :core:test :desktop:test
```

Expected: `BUILD SUCCESSFUL`; all tasks from Tasks 3–8 green.

- [ ] **Step 4: Commit**

```powershell
git add desktop
git commit -m "feat(desktop): restore last playlist and language filter at startup"
```

---

### Task 14: Packaging, notices and CI

**Files:**
- Create: `THIRD_PARTY_NOTICES.md`, `tools/hash.ps1`, `.github/workflows/windows.yml`
- Modify: `desktop/build.gradle.kts`, `README.md`

**Interfaces:**
- Consumes: `downloadVlc`, app image.
- Produces: `desktop\build\compose\binaries\main\app\IPVIO Stream\IPVIO Stream.exe` with libVLC beside it, plus a `.sha256`.

- [ ] **Step 1: Bundle VLC into the app image**

Add to `desktop/build.gradle.kts`:

```kotlin
val stageVlc by tasks.registering(Copy::class) {
    dependsOn(downloadVlc)
    from(vlcDir)
    into(layout.buildDirectory.dir("appResources/vlc"))
}

compose.desktop.application {
    appResourcesRootDir.set(layout.buildDirectory.dir("appResources"))
}

tasks.matching { it.name == "packageAppImage" }.configureEach { dependsOn(stageVlc) }
```

`VlcRuntime.locate()` already checks `compose.application.resources.dir/vlc`.

- [ ] **Step 2: Write notices**

`THIRD_PARTY_NOTICES.md`: IPVIO Stream (GPL-3.0, upstream commit), libVLC 3.0.24 (LGPL-2.1+, separate DLLs, no modifications), vlcj 4.12.1 (GPL-3.0 with classpath exception — verify the artefact's licence file at build time and paste it), JNA (Apache-2.0/LGPL), OkHttp (Apache-2.0), Gson (Apache-2.0), org.json (public domain), Kotlin/Compose/coroutines (Apache-2.0). Include the libVLC source offer: `https://www.videolan.org/vlc/download-sources.html`.

- [ ] **Step 3: Build and hash**

```powershell
.\gradlew.bat :desktop:packageAppImage
& ".\desktop\build\compose\binaries\main\app\IPVIO Stream\IPVIO Stream.exe" --smoke
```

Expected: app image runs, prints the version. Then `tools/hash.ps1`:

```powershell
param([string]$Path)
(Get-FileHash -Algorithm SHA256 $Path).Hash.ToLower() + "  " + (Split-Path $Path -Leaf)
```

```powershell
.\tools\hash.ps1 ".\desktop\build\compose\binaries\main\app\IPVIO Stream\IPVIO Stream.exe"
```

Expected: 64-hex digest + file name.

- [ ] **Step 4: Add CI**

`.github/workflows/windows.yml`: `on: workflow_dispatch` + tags `v*`; `runs-on: windows-latest`; `actions/checkout`; `actions/setup-java` Temurin 21; `./gradlew.bat :core:test :desktop:packageAppImage`; upload `desktop/build/compose/binaries/main/app/IPVIO Stream` and the hash file.

Pushing the workflow needs the `workflow` scope:

```powershell
gh auth refresh -s workflow
git add .github
git commit -m "ci: Windows build and tests"
git push
```

If `gh auth refresh` is not possible in this session, commit locally and stop; report the blocked push.

- [ ] **Step 5: Update README with the P0 limits**

State: Windows x64, VLC bundled, unsigned build → SmartScreen warning, Stalker not yet, no Android data import, source GPL-3.0.

- [ ] **Step 6: Final acceptance**

Run the app, add the user's Xtream account through the form, verify live TV, a movie, a series episode, restart persistence, and that `%LOCALAPPDATA%\IPVIO Stream\settings.json` contains no readable password (DPAPI token). Record the result in the commit message.

- [ ] **Step 7: Commit and push**

```powershell
git add .
git commit -m "build: Windows app image with bundled libVLC, notices and CI"
git push
```

---

## P0 Definition of Done

- `:core:test` and `:desktop:test` green: 30 upstream test classes plus the new settings, repo, Xtream, downloader, service and DPAPI tests.
- `IPVIO Stream.exe` starts from the app image and plays a live channel, a movie and a series episode from a real Xtream account.
- Playlists, active index and language filter survive a restart; deleting the binary cache still restores from the gzip text without network.
- No plaintext playlist URL or password in any file the app writes, and no URL or password in any error text.
- Stalker, merged sources, Home/Browse/Detail screens, posters, EPG, search, settings, profiles, recording and timeshift are explicitly absent and listed as P1/P2 scope.
