# MusicSpot

Android app, amely a [Winlator](https://github.com/brunodev85/winlator) emulátorra épül, és egy külön **„Spotify + Spicetify”** menüponttal lépésenként beállítja a Windowsos Spotify-t [Spicetify](https://spicetify.app/)-jal.

> **Figyelem:** a Spotify emulálva fut (Wine + Box64), ezért lassú lehet, sokat fogyaszt, és nem minden telefonon működik. Ezt a beállítást még senki nem próbálta ki valódi telefonon.

## Telepítés

1. Ha a hivatalos Winlator telepítve van, előbb **töröld** – ugyanazt a csomagnevet (`com.winlator`) használják, a natív kód miatt ez nem változtatható.
2. Töltsd le az APK-t: GitHub → **Actions** fül → a legutóbbi sikeres „Build APK” futás → **Artifacts** → `MusicSpot-apk` (zip, benne az `.apk`).
3. Telepítsd, indítsd el, és várd meg, amíg a rendszerfájlok kicsomagolódnak.

Csak 64 bites (arm64) Android telefonokon fut, Android 8.0 vagy újabb kell.

## Használat

A menüben válaszd a **Spotify + Spicetify** pontot, és nyomd meg sorban a gombokat:

1. **Előkészítés** – létrehoz egy „Spotify (MusicSpot)” konténert, és letölti a hivatalos forrásokból:
   - Spotify telepítő (`download.scdn.co`)
   - Spicetify CLI (GitHub, `spicetify/cli` legújabb kiadás)
   - Spicetify Marketplace (GitHub, `spicetify/marketplace`)
2. **Spotify telepítése** – lefut a Spotify telepítője. Jelentkezz be, aztán zárd be a Spotify-t.
3. **Spicetify alkalmazása** – beállítja a Marketplace-t, és alkalmazza a Spicetify-t.
4. **Spotify indítása** – elindul a Spotify a Spicetify-jal.

Ha a Spotify frissít, és eltűnnek a módosítások: **„Spicetify újra”** gomb.

Témákat és bővítményeket a Spotify-on belül, a **Marketplace** menüpontban lehet telepíteni.

### Ha valami nem megy

- **Fekete ablak / összeomlás:** Parancsikonok → „MusicSpot - Spotify” → beállítások → indítási paraméterek: `--disable-gpu`.
- **Lassú:** a konténer beállításainál próbáld a Box64 „Performance” presetet.
- **Nincs hang:** a konténer beállításainál válts ALSA ↔ PulseAudio között.

## Fordítás

Az APK-t a GitHub Actions fordítja (`.github/workflows/build.yml`) minden push után. Helyben: Android SDK, NDK `24.0.8215888`, CMake `3.22.1`, JDK 17, majd `./gradlew assembleDebug`.

Az `app/musicspot-debug.keystore` egy szándékosan nyilvános, csak erre az appra készült debug kulcs (jelszó: `android`), hogy az új verziók frissítésként települjenek, és ne vesszen el a Spotify konténer.

## Licenc és köszönet

- A Winlator forráskódja: [brunodev85/winlator-app](https://github.com/brunodev85/winlator-app) (commit `f8a211c`), **LGPL-2.1** – lásd `LICENSE`. Az eredeti leírás: `README.winlator.md`.
- A Spotify és a Spicetify **nincs** az APK-ban: mindkettőt az app a hivatalos forrásból tölti le, a felhasználó telefonján.
- A Spotify a Spotify AB védjegye. Ez a projekt nem áll kapcsolatban a Spotify-jal, a Spicetify-jal vagy a Winlator fejlesztőjével.
