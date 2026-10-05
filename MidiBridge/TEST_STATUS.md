# Verification status - 2026-10-05

| Check | Result |
|---|---|
| Pure Java core compilation, javac --release 17 | PASS |
| Pure Java tests | PASS - 14/14 |
| Java syntax parsing, all 10 app/test source files | PASS |
| XML syntax, 8 manifest/resource files | PASS |
| Referenced resource IDs and strings | PASS |
| Intended manifest permissions | PASS |
| POSIX shell syntax | PASS |
| Windows BAT/PowerShell execution | NOT RUN |
| Downloaded Gradle wrapper execution | NOT RUN - network unavailable |
| Android API type checking | NOT RUN - Android SDK absent |
| Android APK assembly | NOT RUN |
| Android Lint | NOT RUN |
| JUnit through Android Gradle Plugin | NOT RUN (the same 14 core checks ran directly on JVM) |
| Emulator / Android UI test | NOT RUN |
| Physical Chocolate / TONEX / Android test | NOT RUN |
| Foreground service and screen-off runtime test | NOT RUN |
| Optional GitHub Actions build | NOT RUN |

Do not interpret Java parsing as an Android compilation. Do not interpret the core
tests as proof of USB/BLE interoperability or concert-ready reliability.

Captured outputs: CORE_TEST_RESULTS.txt, SYNTAX_TEST_RESULTS.txt,
STATIC_CHECK_RESULTS.txt. For the first real build, run unit tests, Android Lint,
and APK assembly with the commands in README.hu.md. Then complete the hardware
checklist. No APK is present in this source package.
