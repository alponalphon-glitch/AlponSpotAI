# NEXORA UI update — 2026-11-23

- Removed the persistent top NEXORA logo/title toolbar from the five-tab workspace.
- Kept a minimal back arrow only on secondary pages.
- Changed the shared CardBox composable to transparent, borderless content, removing the dark rounded rectangle panels throughout the five main menus without deleting their content.
- Kept the bottom five-tab navigation black with white labels and white icons; selected tabs no longer show a colored pill background.
- Fullscreen chart now requests sensor-landscape orientation, expands the candle chart to the available screen area, and keeps compact timeframe/indicator controls.
- Closing the fullscreen chart restores the app's unspecified orientation.
- Added Android configuration-change handling for orientation/screen size so the activity is not recreated while entering landscape chart mode.
- No Spot, Futures, Scanner, Paper Trading, or analysis source files were removed.

Build note: Android Gradle build could not be completed in this environment because Gradle 8.9 wrapper download failed (DNS: services.gradle.org). ZIP integrity is checked separately.
