# D-Player

Audio player Android ottimizzato per head unit (Neutron Player come riferimento
visivo/funzionale). Kotlin + Jetpack Compose + Media3/ExoPlayer, min API 23
(Android 6), solo armeabi-v7a/arm64-v8a.

## Stato scaffold (v0.1.0)

Funzionante end-to-end (compila, gira, riproduce):
- Shell UI: shortcut bar, area visual tap-to-cycle, transport bar
- PlaybackService con MediaSession (notifica persistente + tasti fisici/BT)
- SafFileBrowser: browsing reale di cartelle USB/SD via SAF
- FtpBrowser: browsing reale via FTP (commons-net), compatibile Primitive-FTPd
- AudioEffectsChain: EQ 4-band + preamp reali; compressor/AGP via
  DynamicsProcessing (richiede API 28+, no-op sotto)
- GitHub Actions: build automatica APK debug su ogni push/PR

Ancora da implementare (TODO nel codice, cercare `TODO:`):
- UpnpBrowser: Browse via jupnp ContentDirectory (stub, non ancora collegato)
- FtpDataSource custom per Media3 (oggi FtpBrowser resta a livello di listing;
  la riproduzione via ftp:// va agganciata a un DataSource.Factory dedicato)
- Wiring VisualizerEngine -> Compose (oggi le 3 viste mostrano un placeholder
  animato/statico, non ancora i dati reali da Visualizer)
- Schermate: configurazione shortcut (scelta cartella/FTP/UPnP per ognuna
  delle 3), equalizzatore (UI fader), impostazioni skin, file browser vero
- Crossfade (dual-player) tra tracce
- VU-meter L/R discreti (oggi placeholder — serve AudioProcessor custom pre-mix)

## Build locale

Serve Gradle 8.7 + JDK 17 (il repo non include il wrapper binario, vedi CI).

```
gradle assembleDebug
```

## CI

`.github/workflows/build-debug.yml` produce un artifact `D-Player-debug`
scaricabile dalla tab Actions ad ogni push su `main`.
