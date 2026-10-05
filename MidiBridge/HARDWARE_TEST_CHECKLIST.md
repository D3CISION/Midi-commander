# Hardveres próbalista - még nem végrehajtott tesztek

Telefon / Android-verzió: ____________________
TONEX modell / firmware: ____________________
Chocolate firmware / CubeSuite MIDI-mód: ____________________
USB-kábel / OTG adapter: ____________________

## Telepítés és engedélyek

- [ ] A teljes Android-fordítás, JUnit-teszt és lint sikeres.
- [ ] A debug APK telepíthető és elindul; nincs induláskori hiba.
- [ ] Első scannél megjelenik a Közeli eszközök kérése (Android 12+).
- [ ] Az engedély megtagadása nem okoz összeomlást; a beállítási út elérhető.
- [ ] Kikapcsolt Bluetooth esetén megjelenik a rendszer bekapcsolási kérése.
- [ ] Android 8-11 alatt a helyengedély és a Hely funkció ellenőrzése működik.
- [ ] Megtagadott értesítési engedély mellett sincs indítási hiba.

## Kapcsolatok

- [ ] A sima Chocolate / FootCtrl látszik a scan után.
- [ ] A keresés leállítható, és időkorlát után magától is leáll.
- [ ] A kiválasztott Chocolate kapcsolódása sikeres, bemeneti portja megjelenik.
- [ ] Az USB TONEX megjelenik kimeneti MIDI-portként.
- [ ] A kiválasztott TONEX-porton a kimeneti PC teszt ténylegesen vált.
- [ ] Nem MIDI BLE eszköz és foglalt MIDI-port esetén érthető hibajelzés jelenik meg.

## Továbbítás

- [ ] INPUT = Chocolate, OUTPUT = TONEX; a routing indítható és leállítható.
- [ ] A, B, C, D gombnyomásoknál nő a be-/kimenő bájtszámláló.
- [ ] A TONEX a várt PC és CC üzenetekre reagál.
- [ ] Eltérő MIDI-csatornák próbája igazolja, hogy nincs rejtett csatornaátírás.
- [ ] A tesztgomb aktív routing alatt le van tiltva.
- [ ] Többszöri indítás/leállítás után nincs duplázott parancs vagy fogva maradt port.
- [ ] Gyors egymás utáni gombnyomásoknál nincs elveszett vagy megkettőzött váltás.

## Életciklus és hibák

- [ ] Aktív routing mellett a felület elhagyása nem szakítja meg a továbbítást.
- [ ] Aktív routing mellett képernyőforgatás után megmarad a kapcsolat.
- [ ] Lezárt kijelzővel is működik; rögzítsd az eltelt időt és az akkumulátor-beállítást.
- [ ] Az értesítés Leállítás gombja ténylegesen lezárja a routingot.
- [ ] USB-kábel kihúzására leáll a routing, visszadugás után kézzel újraindítható.
- [ ] Bluetooth kikapcsolásakor és a Chocolate hatótávolságon kívül kerülésekor megfelelő az állapotjelzés.
- [ ] Nem indul újra magától a routing az app kényszerített leállítása után.
- [ ] A napló másolható, olvasható és nem nő korlátlanul.

A kezdeti próbát ne koncerten végezd. Egy sikeres gombnyomás nem helyettesíti a hosszabb, lezárt kijelzős és kapcsolatmegszakításos ellenőrzést.
