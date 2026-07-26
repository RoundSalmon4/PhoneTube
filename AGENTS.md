# PhoneTube Agent Guide

## Project Overview

YouTube phone app (Kotlin + Jetpack Compose) built on SmartTube's `MediaServiceCore` as its YouTube data engine. No TV UI, no Leanback, no MVP — modern Android architecture.

**Package:** `com.roundsalmon4.phonetube`  
**Min SDK:** 24 (Android 7.0) | **Target/Compile SDK:** 35

## Critical Constraints

1. **No Android SDK locally** — code-only commits. Do not run builds locally. Test via CI or on device.
2. **`GlobalPreferences.instance(context)` MUST be called first** before ANY MediaServiceCore API call. See `YouTubeInitializer.kt`.
3. **MediaServiceCore uses RxJava 2** — bridge to Coroutines/Flow at `YouTubeEngine` boundary. Never expose RxJava types to the rest of the app.
4. **KSP pinned to KSP1** — `ksp.useKSP2=false` in `gradle.properties`. KSP2 crashes on Room's suspend DAOs.
5. **OkHttp forced to 3.12.13** — root `build.gradle.kts` forces this version globally for MediaServiceCore compatibility.

## Build Commands

```bash
# Build release APK (CI)
./gradlew assembleRelease --no-daemon

# Run lint
./gradlew :app:lintDebug

# Run unit tests
./gradlew :app:testDebugUnitTest
```

**CI builds:** Push/PR to `new-ui` branch triggers GitHub Actions (JDK 17, `assembleRelease`).

## Project Structure

```
PhoneTube/
├── app/                          # Compose app module (our code)
│   └── src/main/java/com/roundsalmon4/phonetube/
│       ├── core/
│       │   ├── engine/           # MediaServiceCore wrapper (RxJava→Flow bridge)
│       │   ├── database/         # Room: history, playlists, subscriptions
│       │   ├── datastore/        # DataStore preferences
│       │   └── di/               # Hilt modules
│       ├── player/               # Media3 ExoPlayer + SponsorBlock
│       └── ui/                   # Compose screens + components
├── MediaServiceCore/             # Git submodule — YouTube API engine
│   ├── youtubeapi/               # :youtubeapi module
│   └── mediaserviceinterfaces/  # :mediaserviceinterfaces module
└── SharedModules/                # Git submodule — utilities, GlobalPreferences
    └── sharedutils/              # :sharedutils module
```

## Module Dependencies

`app` depends on:
- `:youtubeapi` (MediaServiceCore)
- `:mediaserviceinterfaces` (MediaServiceCore)
- `:sharedutils` (SharedModules)

The root `build.gradle.kts` auto-applies `enable-buildconfig.gradle` and forces Java 17 compile options on all submodules.

## Key Files

| File | Purpose |
|------|---------|
| `app/src/main/java/com/roundsalmon4/phonetube/core/engine/YouTubeEngine.kt` | All YouTube API calls — RxJava→Flow bridge |
| `app/src/main/java/com/roundsalmon4/phonetube/core/engine/YouTubeInitializer.kt` | MUST call `GlobalPreferences.instance(context)` first |
| `app/src/main/java/com/roundsalmon4/phonetube/core/database/AppDatabase.kt` | Room DB: history, playlists, subscriptions |
| `app/src/main/java/com/roundsalmon4/phonetube/player/PlayerEngineController.kt` | Media3 ExoPlayer abstraction |
| `app/src/main/java/com/roundsalmon4/phonetube/ui/navigation/Route.kt` | Type-safe navigation routes |
| `gradle/libs.versions.toml` | Version catalog — all dependency versions |
| `SharedModules/constants.gradle` | Version variables for submodules |

## Architecture

- **MVVM + UDF:** Single immutable `UiState` per screen, ViewModels + StateFlow
- **DI:** Hilt (`@HiltAndroidApp`, `@AndroidEntryPoint`, `@Inject`)
- **Navigation:** Navigation Compose with type-safe `@Serializable` routes
- **Async:** Kotlin Coroutines + Flow (RxJava only at MediaServiceCore boundary)
- **Images:** Coil 3 with custom `HttpNetworkClient`
- **Player:** Media3 ExoPlayer 1.8.0 with `media3-ui-compose`

## Submodule Updates

MediaServiceCore and SharedModules are git submodules. To update:
1. `cd MediaServiceCore && git fetch origin && git checkout origin/master`
2. `cd SharedModules && git fetch origin && git checkout origin/master`
3. Commit the submodule pointer changes

CI runs automated weekly updates via `.github/workflows/update-submodules.yml`.

## Gotchas

- **Version catalog mismatch:** `gradle/libs.versions.toml` has the real versions. `plan.md` contains aspirational versions for future bumps — do not use them.
- **Submodule build files** use Groovy (`build.gradle`) and reference `SharedModules/constants.gradle` for versions. Do not modify these unless updating the submodules themselves.
- **Room schema** exported to `app/schemas/` — check in schema changes.
- **ProGuard:** Currently disabled for release builds (`isMinifyEnabled = false`). Keep it disabled until release polish.
- **JVM target:** 17 everywhere. Root `build.gradle.kts` forces this on all submodules.
- **Coroutines forced to 1.9.0** via root `build.gradle.kts` resolution strategy.

## Agent Workflow Rules

1. **Read the repository** and become an expert on it and how it works before making changes.

2. **Never use the user's real name or personal info** in commits, code, comments, or any output.

3. **Commit identity:** All commits must use:
   - Author: `roundsalmon4`
   - Email: `209016228+RoundSalmon4@users.noreply.github.com`

3a. **Always provide a commit summary and get final go-ahead before committing.**

3b. **Solo-dev style:** All commits, code, changelog entries, comments, and notes should read like a solo developer wrote them — not AI.
   - Bad: "Refactored the VideoCardViewHolder to utilize a shared adapter pattern for improved code maintainability"
   - Good: "pulled out the video card view holder into a shared adapter. was tired of copy-pasting it between 5 fragments"

4. **Do not try to build locally** — no Android SDK. Code-only commits. Test via CI or on device.

5. **Always use PAT to commit and push** (configured in remote `fork`).

6. **Re-sync before starting:** `git pull --rebase` to ensure local copy is up to date with repo.

7. **Re-evaluate uncommitted changes** — check `git status` and `git diff` before proceeding.

8. **Read `plan.md`** — the full implementation plan. Always stay in scope with the current step. Do not drift into other parts of the plan.

9. **Kotlin only, Compose only** — no Java, no XML Views. Jetpack Compose UI throughout.
