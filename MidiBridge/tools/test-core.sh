#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/core-checks"
mkdir -p "$OUT"
javac --release 17 -d "$OUT" \
  "$ROOT/app/src/main/java/hu/spektrumhiba/midibridge/core/MidiPipe.java" \
  "$ROOT/app/src/main/java/hu/spektrumhiba/midibridge/core/MidiMonitor.java" \
  "$ROOT/app/src/test/java/hu/spektrumhiba/midibridge/core/CoreChecks.java"
java -cp "$OUT" hu.spektrumhiba.midibridge.core.CoreChecks
