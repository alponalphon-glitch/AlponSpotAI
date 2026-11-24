# AlponSpotAI Kotlin repair notes

- Audited the 56 Kotlin source files included in this archive.
- Rewrote two `continue` statements inside `run {}` lambdas in `DualPaperAutonomousCoordinator.kt` as ordinary loop control flow. The decision thresholds, signal rules, sizing, stops, targets, and portfolio logic were not changed.
- Broke several long nullable-metric interpolations in `MainActivity.kt` into local formatting values to reduce Kotlin FIR/data-flow compiler pressure. Displayed values and decision logic are preserved.
- Updated Gradle memory/workers for a single-process Kotlin compile and pinned Gradle 8.11.1 to match Android Gradle Plugin 8.9.4.
- CI now builds the Debug APK and uploads that artifact.

Important: This environment does not contain the Android SDK / complete Gradle wrapper setup, so a full Android Gradle build could not be run here. The archive is statically reviewed and prepared for the next CI build; it is not honestly certified as a successful APK build until GitHub Actions (or another Android build runner) completes.
