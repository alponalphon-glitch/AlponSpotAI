# NEXORA Android compatibility

- Minimum SDK: 26 (Android 8.0 Oreo). Android 8.1 is API 27.
- Compile SDK: 37 (Android 17).
- Target SDK: 36 (Android 16), intentionally conservative until API 37 behavior and large-screen flows are tested.
- Android Gradle Plugin: 8.9.4.
- Gradle: 8.11.1.
- Java/Kotlin bytecode target remains 17.

This configuration makes the project build against Android 17 APIs while retaining Android 8.0 as the minimum runtime. It does not by itself prove runtime compatibility on every OEM/device. Validate on API 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36 and 37 emulators/devices before release.

Target SDK 36 is intentional: moving to target SDK 37 opts into Android 17 target-gated behavior changes and should follow dedicated compatibility testing, including large-screen resizability.
