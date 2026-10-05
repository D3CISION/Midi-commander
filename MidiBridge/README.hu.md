# MIDI Bridge 0.1.0

Androidos MIDI host és egyirányú továbbító a **sima M-VAVE CHOCOLATE** és egy USB MIDI-ként elérhető **TONEX Pedal** közé.

> **Ez forrásprojekt, nem telepíthető APK.** Az Android SDK és a Gradle itt nem volt elérhető, a letöltésük sem sikerült. Teljes Android-fordítás, emulátoros futtatás és valódi pedálos próba nem történt. A tiszta Java MIDI-mag 14 tesztje sikeres. A Java-források szintaktikai és az XML-erőforrások statikus ellenőrzése is lefutott. Az első hardveres próbához készült, nem igazolt koncertkész kiadás.

## Jelút

```text
M-VAVE CHOCOLATE (sima)
          |
          | Bluetooth Low Energy MIDI
          v
Android telefon / MIDI Bridge
          |
          | USB host / OTG / MIDI
          v
TONEX Pedal (saját tápegységgel)
```

A program MIDI-vezérlőjeleket továbbít, **nem hangot**. Nem igényli a Chocolate Plus USB-host funkcióját: a host szerepét a telefon tölti be.

## Megvalósított funkciók a forrásban

| Funkció | Megoldás |
|---|---|
| Bluetooth scan | Kézzel indítható, 12 másodperces BLE-keresés; leállítási gomb |
| Chocolate kiválasztása | MIDI UUID, illetve Chocolate / FootCtrl / M-VAVE név szerinti jelölés; kapcsoló minden BLE eszközhöz |
| Engedélyek | Android 12+: Közeli eszközök; Android 8-11: helyengedély a kereséshez; Android 13+: értesítési kérés |
| USB MIDI | Android MIDI 1.0 portok listázása, csatlakoztatás/leválasztás kezelése, USB-diagnosztika |
| Routing | Választható bemenet és kimenet, minden elérhető MIDI-port külön sorban |
| Továbbítás | Változatlan bájtok és Android-időbélyeg; PC, CC, bank select, Note, SysEx, realtime |
| Háttérműködés | connectedDevice foreground service, leállítási értesítés, aktív routinghoz kötött wake lock |
| Hibakeresés | Be-/kimenő bájtszámláló, üzenetnapló, másolás vágólapra |
| TONEX-teszt | Kimeneti PC-küldés Chocolate nélkül, csak leállított routing mellett |

## APK készítése számítógépen

**Előfeltétel:** Android Studio, JDK 17, internet az első fordításhoz, Android SDK Platform 36 és SDK Build-Tools 35.0.0. A projekt rögzített verziói: Android Gradle Plugin 8.11.1 / Gradle 8.13 / compileSdk 36 / targetSdk 36 / minSdk 26. Hivatalos kompatibilitási forrás: `SOURCES.md`.

1. Csomagold ki a ZIP-et. A projekt gyökere a `MidiBridge` mappa, benne a `settings.gradle.kts` fájllal.
2. Legyen a JDK 17 `java` parancsa elérhető, vagy állítsd be a `JAVA_HOME` változót a JDK mappájára. A projekt mappájában futtasd:

Windows PowerShell:
```powershell
.\gradlew.bat --version
```
macOS / Linux:
```sh
chmod +x gradlew
./gradlew --version
```

A forráscsomag nem tartalmaz bináris Gradle wrapper JAR-t. Az indítószkript az első futtatáskor a Gradle hivatalos GitHub-tárolójából letölti, majd a hivatalosan közzétett, rögzített SHA-256 ellenőrzőösszeggel ellenőrzi. Csak ezután futtatja. A Gradle-disztribúció ellenőrzőösszege is rögzített.

3. Android Studio: **Open**, majd válaszd ki a `MidiBridge` mappát. Az SDK Managerben telepítsd a fenti SDK-t és build tools verziót, fogadd el a szükséges licenceket, majd fejeződjön be a Gradle Sync. A Studio az SDK helyét a helyi `local.properties` fájlban tudja beállítani.
4. Fordítás az Android Studio termináljából:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

macOS / Linux alatt ugyanez `./gradlew` indítóval fut. A Studio menüjéből a debug buildhez a **Build > Generate Bundle(s) / APK(s) > Generate APK(s)** lehetőség használható.

5. Sikeres fordítás után a telepíthető, debug-kulccsal aláírt fájl:

```text
app/build/outputs/apk/debug/app-debug.apk
```

6. Másold a telefonra és nyisd meg. Az Android kérésére engedélyezd az adott fájlkezelőből való alkalmazástelepítést. Az APK itt még nem készült el; ez a sikeres fordítás utáni lépés.

### GitHub Actions-alternatíva

A `.github/workflows/build-apk.yml` kézzel is indítható APK-fordítási munkafolyamat. A kibontott projekt tartalmát egy saját tároló gyökerébe feltöltve az **Actions > Build MIDI Bridge APK > Run workflow** paranccsal futtatható. A rejtett `.github` mappa is kerüljön fel. Siker esetén a `MIDI-Bridge-debug-APK` artifact tartalmazza az APK-t; a `build-reports` a teszt- és lintjelentéseket.

Ezt a munkafolyamatot itt nem futtattam, és nem hoztam létre tárolót a fiókodban. Eltérő gépen készülő debug APK-k aláírása eltérhet; ilyenkor a régi tesztverzió eltávolítása szükséges lehet az új telepítéséhez.

## Használat a pedálokkal

1. A TONEX-et saját tápjáról működtesd. Adatkábellel és a telefonhoz megfelelő USB host/OTG csatlakozással kösd a telefonra. A kimeneti listában tényleges MIDI-portnak kell megjelennie; az USB-töltés vagy USB-hangkártya-felismerés önmagában nem elég.
2. A sima Chocolate gombjait CubeSuite-ban állítsd MIDI PC/CC üzenetekre, ne billentyűparancsokra vagy lapozó HID módra. Ez az app nem programozza át a Chocolate-ot. Zárd be a CubeSuite-ot és minden más, a kapcsolatot használó appot.
3. MIDI Bridge > **Bluetooth scan** > engedélyezés > koppints a Chocolate / FootCtrl találatra. Ha nincs, kapcsold be a **Minden BLE eszköz mutatása** lehetőséget. A név alapján felismert jelölt nem garantáltan MIDI-kompatibilis: a tényleges megnyitást az Android végzi.
4. **INPUT = Chocolate**, **OUTPUT = TONEX**. A név melletti portszámokat is ellenőrizd. A program megjegyzi a legutóbb indított útvonal eszközneveit; nem indít routingot automatikusan.
5. **Továbbítás indítása**. A két pedálon a küldött és fogadott MIDI-csatorna egyezzen. Az app nem írja át a csatornát, a PC-számot vagy a CC-t.

A kimeneti tesztet a routing indítása előtt is használhatod. A **MIDI-csatorna** és **Program Change** mező kizárólag a tesztgombra vonatkozik; nem szűri és nem alakítja át a Chocolate jelét. A teszt 0-127 közötti MIDI PC-számot küld. Nem állít bankot, és a TONEX kijelzőjének bankszámozását nem képezi le. A tényleges presetváltást a pedálon ellenőrizd; az elküldés nem jelent hardveres visszaigazolást.

## Hibakeresés

| Jelenség | Ellenőrizendő |
|---|---|
| Nincs Chocolate a listában | Táp, MIDI mód, másik telefon/app kapcsolata, engedélyek, minden BLE eszköz mutatása |
| Android 8-11 nem talál BLE eszközt | Helyengedély és a telefon Hely funkciója; az app nem kér GPS-pozíciót |
| USB eszköz van, MIDI kimenet nincs | A telefon/firmware nem tett elérhetővé MIDI-portot; kábel, host/OTG, TONEX táp, Android támogatás |
| A PC teszt vált, a Chocolate nem | Chocolate MIDI üzenettípus, gombbeállítás, csatorna vagy rossz bemeneti port |
| Bejövő bájt nulla | Nem érkezik adat a kiválasztott bemenetre; a hardver némasága önmagában nem bizonyítja a kapcsolat bontását |
| Be és ki nő, de nincs váltás | A TONEX csatornája, a küldött PC/CC és bankbeállítás; az app csak továbbít |
| Lezárt kijelzőnél leáll | Az app akkumulátor-korlátozásának feloldása a telefonon; tényleges telefonos próba szükséges |
| Kimeneti port foglalt | Más MIDI-app vagy a még futó teszt használja |
| Kábel vagy Bluetooth megszakadt | Leállítás, újracsatlakoztatás, portok kiválasztása, kézi újraindítás |

Az aktív routing a felület bezárásától elkülönített szolgáltatásban fut. **Routing nélkül** a felület elhagyása után a keresés megáll, és az ideiglenes Bluetooth-kapcsolat felszabadulhat. A gyártói energiatakarékosság és a rendszerszintű leállítás ellen nincs abszolút garancia.

## Korlátok és adatkezelés

Egy bemenetet köt össze egy kimenettel. Nincs automatikus újracsatlakozás, CC-to-PC konverzió, csatornaátírás, bankszámozás-átalakítás, Bluetooth Classic SPP, HID-billentyűzet-kezelés, gyártóspecifikus USB-driver vagy MIDI 2.0 UMP-fordítás. A sima Chocolate szabványos BLE MIDI szolgáltatását használja, amennyiben az adott firmware ezt elérhetővé teszi. A saját hardver/firmware kombinációval még nem tesztelt.

Nincs INTERNET, mikrofon-, média- vagy tárhelyengedély. Nincs analitika és felhő. A helyi beállítások az app saját tárhelyén maradnak. A 160 soros napló memóriában él, és csak a felhasználó gombnyomására kerül vágólapra. Az export telefonmodellt, MIDI-eszközneveket és MIDI-üzeneteket tartalmazhat; megosztás előtt nézd át.

A napló gyors adatforgalomnál sorokat hagyhat ki, **nem a MIDI-adatút**. A clock és active sensing üzenetek nem töltik meg a szöveges naplót, de a továbbításból nincsenek kiszűrve.

## Fejlesztői felépítés

`MainActivity.java`: kezelőfelület, engedélyek, portválasztás. `BridgeService.java`: foreground szolgáltatás. `BleScanner.java`: időkorlátos keresés. `MidiEngine.java`: MIDI életciklus, BLE anchor, aszinkron portnyitás, USB-felismerés, tesztküldés. `MidiPipe.java`: szálbiztos bájttovábbítás. `MidiMonitor.java`: diagnosztikai parser; nem ebből épül újra a továbbított adat.

**Android-nézőpont:** az app INPUT-ja az eszköz `TYPE_OUTPUT` portját olvassa (`openOutputPort`); az app OUTPUT-ja az eszköz `TYPE_INPUT` portjára ír (`openInputPort`). A BLE kapcsolatot megnyitó `MidiDevice` objektum a kapcsolat alatt nyitva marad. Későn visszatérő portnyitási callbackek nem aktiválhatnak egy már leállított routingot.

## Ellenőrzések

A tiszta Java-mag Android SDK nélkül tesztelhető:

```sh
sh tools/test-core.sh
```

A mellékelt `CORE_TEST_RESULTS.txt`, `SYNTAX_TEST_RESULTS.txt` és `STATIC_CHECK_RESULTS.txt` kizárólag az itt ténylegesen elvégzett ellenőrzéseket dokumentálja. A hardveres próbalista a `HARDWARE_TEST_CHECKLIST.md` fájlban található.
