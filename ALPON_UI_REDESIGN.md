# ALPON UI Redesign

- Replaced the former News bottom tab with the ALPON command center.
- Added a custom robotic falcon avatar used in the app header and local profile.
- Updated the visual palette to deep navy, electric blue, cyan, and restrained gold accents.
- Added the ALPON command center with current AI decision, confidence, five intelligence systems, scanner, Paper Trading access, backtesting, and walk-forward access.
- Added Auto Pilot for Paper Trading only. It checks the AI quality gate before virtual BUY entries and uses AI SELL decisions to close virtual positions.
- Auto Pilot checks the selected market every 15 seconds while the command center is active. It uses 10% of available virtual balance per entry, capped at 500 USDT, and only executes if the existing Paper Trading guards allow it.
- The Auto Pilot preference is saved locally. Monitoring runs while the ALPON tab is open. It never submits real Binance orders and does not request or store API credentials.

## Verification status

- ZIP integrity and source-level syntax diagnostics were checked.
- A full Android Gradle compile could not be run because the Gradle 8.9 wrapper distribution could not be downloaded in this environment (network DNS unavailable). Build and runtime behavior must still be verified in Termux or GitHub Actions.
