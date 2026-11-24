#!/usr/bin/env bash
set -euo pipefail
if ! command -v java >/dev/null 2>&1; then echo 'JDK 17 is required.' >&2; exit 1; fi
if ! command -v gradle >/dev/null 2>&1; then echo 'Gradle 8.11.1 is required (this source ZIP does not include Gradle Wrapper).' >&2; exit 1; fi
JAVA_MAJOR=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [[ "$JAVA_MAJOR" != "17" ]]; then echo "Use JDK 17 (found Java ${JAVA_MAJOR:-unknown})." >&2; exit 1; fi
gradle --no-daemon :app:lintDebug :app:assembleDebug :app:assembleRelease
