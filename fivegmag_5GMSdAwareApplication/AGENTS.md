# AGENTS.md

Coding agent instructions for the **5GMSd-Aware Application** -- a reference Android app
for 5G Downlink Media Streaming by the 5G-MAG consortium. Single-module Kotlin Android
project using Gradle (Groovy DSL).

## Project Overview

- **Package:** `com.fivegmag.a5gmsdawareapplication`
- **Language:** Kotlin (JVM target 1.8)
- **Min SDK:** 29, **Target/Compile SDK:** 34
- **Architecture:** Activity-centric (no ViewModel/MVVM). All logic lives in Activities.
- **Key libraries:** Media3/ExoPlayer 1.0.2, Retrofit 2.9.0, greenrobot EventBus 3.3.1,
  kotlinx-serialization-json 1.4.1
- **5G-MAG dependencies** (`com.fivegmag:a5gmscommonlibrary:1.2.1`,
  `com.fivegmag:a5gmsmediastreamhandler:1.2.1`) are resolved from **Maven Local**.
  They must be published locally before building -- see `Readme.md`.
- **License:** 5G-MAG Public License (v1.0)
- **Git workflow:** Gitflow -- branch from `development`, not `main`.

## Build Commands

All commands run from project root (`fivegmag_5GMSdAwareApplication/`).

```bash
# Full build (debug + release APKs)
./gradlew assemble

# Debug build only
./gradlew assembleDebug

# Clean build
./gradlew clean assembleDebug

# Install debug APK on connected device/emulator
./gradlew installDebug

# Check for compilation errors without producing APK
./gradlew compileDebugKotlin
```

## Test Commands

Testing uses **JUnit 4** for unit tests and **Espresso** for instrumented tests.
Currently only placeholder tests exist.

```bash
# Run all unit tests
./gradlew test

# Run all unit tests (debug variant)
./gradlew testDebugUnitTest

# Run a single unit test class
./gradlew testDebugUnitTest --tests "com.fivegmag.a5gmsdawareapplication.ExampleUnitTest"

# Run a single test method
./gradlew testDebugUnitTest --tests "com.fivegmag.a5gmsdawareapplication.ExampleUnitTest.addition_isCorrect"

# Run instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest

# Run a single instrumented test class
./gradlew connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.fivegmag.a5gmsdawareapplication.ExampleInstrumentedTest
```

**Test locations:**
- Unit tests: `app/src/test/java/com/fivegmag/a5gmsdawareapplication/`
- Instrumented tests: `app/src/androidTest/java/com/fivegmag/a5gmsdawareapplication/`

## Lint

No dedicated linting tools (ktlint, detekt, spotless) are configured. Android Lint
is available via Gradle:

```bash
# Run Android Lint
./gradlew lint
./gradlew lintDebug
```

`gradle.properties` sets `kotlin.code.style=official` which affects IDE formatting.

## Code Style Guidelines

### File Structure

1. License header block (required for all source files):
   ```kotlin
   /*
   License: 5G-MAG Public License (v1.0)
   Author: <author name>
   Copyright: (C) <year> <organization>
   For full license terms please see the LICENSE file distributed with this
   program. If this file is missing then the license can be retrieved from
   https://drive.google.com/file/d/1cinCiA778IErENZ3JN52VFW-1ffHpx7Z/view
   */
   ```
2. `package` declaration
3. Imports
4. Top-level constants
5. Class declaration

### Imports

- Prefer explicit single-class imports over wildcards.
- Wildcards (`*`) are acceptable for `kotlinx.serialization.json.*` and `java.util.*`.
- No enforced grouping or blank-line separation between import groups.
- Do not leave unused imports.

### Naming Conventions

| Element          | Convention         | Example                                |
|------------------|--------------------|----------------------------------------|
| Packages         | lowercase          | `com.fivegmag.a5gmsdawareapplication`  |
| Classes          | PascalCase         | `MediaStreamHandlerEventHandler`       |
| Activities       | `*Activity` suffix | `LicenseActivity`, `AboutActivity`     |
| Interfaces       | `I` prefix         | `IM8InterfaceApi`, `IConfigApi`        |
| Functions        | camelCase          | `populateStreamSelectionSpinner()`     |
| Callbacks        | `on` prefix        | `onConnectionToMediaSessionHandlerEstablished()` |
| Variables        | camelCase          | `currentSelectedStreamIndex`           |
| Constants        | SCREAMING_SNAKE    | `TAG_AWARE_APPLICATION`                |
| XML resource IDs | camelCase or `id*` | `idStreamSpinner`, `loadButton`        |

### Types and Null Safety

- Use `lateinit var` for properties initialized after construction (common pattern here).
- Prefer safe-call `?.` and explicit null checks (`if (x != null)`) over `!!`.
- Avoid non-null assertions (`!!`) -- they risk `NullPointerException`.
- Use `== true` for nullable Boolean expressions:
  `event.trackFormat?.containerMimeType?.contains("video") == true`
- Retrofit interface return types are nullable: `Call<ResponseBody>?`.
- Use concrete collection types: `ArrayList<T>()` is the existing convention.

### Error Handling

- Wrap risky operations in `try/catch`.
- Catch specific exceptions where possible (`PackageManager.NameNotFoundException`).
- For intentionally suppressed exceptions, use `catch (_: Exception)` with the
  underscore convention.
- Use `e.printStackTrace()` for debugging (existing pattern), though `Log.e(TAG, msg, e)`
  is preferred for new code.
- Network callback failures: cancel the call in `onFailure`.

### Logging

- Use `android.util.Log` with a file-level `const val` tag.
- Tag constant: `const val TAG_AWARE_APPLICATION = "5GMS Aware Application"`
- Use Kotlin string templates for log messages:
  `Log.d(TAG, "Version: ${BuildConfig.LIB_VERSION_...}")`

### Async / Networking

- Retrofit with callback-based `enqueue()` (no coroutines).
- Retrofit instances are created manually (no DI framework).
- API interfaces use `@GET` annotations; dynamic URLs use `@Url` parameter.

### Event Handling

- Cross-component events use greenrobot **EventBus**.
- Subscribe with `@Subscribe(threadMode = ThreadMode.MAIN)`.
- Register in `onStart()`, unregister in `onStop()`.

### Dependencies

- No dependency injection framework -- dependencies are instantiated directly or via
  `lateinit var` with manual initialization.
- Function references (`::methodName`) are used as callbacks.

### Documentation

- KDoc (`/** ... */`) for public classes and non-obvious methods.
- Keep inline comments minimal and meaningful.
- Use `TODO("Not yet implemented")` for unfinished stubs.

## Project Structure

```
fivegmag_5GMSdAwareApplication/
  app/
    build.gradle                  # Module build config (Groovy DSL)
    src/
      main/
        AndroidManifest.xml
        assets/                   # M8 config files (JSON, XML)
        java/com/fivegmag/a5gmsdawareapplication/
          MainActivity.kt         # Core app logic (519 lines)
          AboutActivity.kt        # About screen
          LicenseActivity.kt      # License screen
          MediaStreamHandlerEventHandler.kt  # EventBus subscriber
          network/
            IM8InterfaceApi.kt    # Retrofit API for M8 endpoint
            IConfigApi.kt         # Retrofit API for config fetching
        res/                      # Layouts, strings, themes, icons
      test/                       # JUnit 4 unit tests
      androidTest/                # Espresso instrumented tests
  build.gradle                    # Root build file (plugin versions)
  settings.gradle                 # Module includes, repositories
  gradle.properties               # JVM args, Kotlin style, AndroidX
```

## Common Pitfalls

- **5G-MAG libraries must be in Maven Local** before building. If the build fails with
  unresolved `com.fivegmag` dependencies, publish the companion libraries first.
- The file `app/MainActivity.java` is a stale legacy file (not under `src/`). It is not
  part of the build and should be ignored.
- `@UnstableApi` annotation is required on classes using Media3 unstable APIs.
- XML resource IDs have inconsistent naming (mixed camelCase, `id*` prefix, snake_case).
  Follow the existing pattern for the file you are editing.
