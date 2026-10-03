# D-Player

## ⚠️ Azione richiesta ora, una tantum
Le build precedenti a questo fix avevano ciascuna una chiave di firma debug
diversa (runner CI effimero, nessuna keystore persistita). Qualsiasi app
D-PLAYER già installata su un telefono **non verrà mai aggiornata** da una
build con la keystore ora fissata in CI — Android rifiuta update con firma
diversa. **Disinstalla D-PLAYER da ogni dispositivo di test prima di
installare la prossima build**, poi va tutto a posto per sempre (la keystore
da qui in avanti è stabile, cache key `dplayer-debug-keystore-v1`).

Audio player Android ottimizzato per head unit (Neutron Player come riferimento
visivo/funzionale). Kotlin + Jetpack Compose + Media3/ExoPlayer, min API 23
(Android 6), solo armeabi-v7a/arm64-v8a.

## Stato scaffold (v0.1.0)

Funzionante end-to-end (compila, gira, riproduce):
- Shell UI con navigazione (Player / Settings / Config shortcut / EQ / Browser)
- PlaybackService con MediaSession (notifica persistente + tasti fisici/BT)
- SafFileBrowser (USB/SD), FtpBrowser + FtpDataSource per Media3 (compatibile
  Primitive-FTPd, playback reale via ftp://), UpnpBrowser via jupnp
  ContentDirectory Browse (**non ancora testato in CI, vedi nota sotto**)
- AudioEffectsChain: EQ 4-band + preamp reali, persistiti e ripristinati ad
  ogni riavvio del service; compressor/AGP via DynamicsProcessing (API 28+,
  no-op sotto)
- CrossfadeController: fade-out/fade-in ai bordi traccia (non vero overlap a
  due player — vedi commento nel file per il perché)
- VisualizerEngine agganciato via bridge in-process a Oscilloscope/FFT/VU
  reali (VU-meter semplificato a singolo canale, non stereo L/R separato)
- Settings: skin (DEFAULT PITCH BLACK / FULL GLASS con vera trasparenza di
  finestra / CUSTOM con immagine utente), overlay album art, Full Screen
  VU-Meters toggle
- File browser: navigazione cartella per cartella, tap per riprodurre,
  shuffle ricorsivo della cartella madre, selezione multi-cartella
- GitHub Actions: build automatica APK debug su ogni push/PR

Ancora da fare / a rischio (TODO nel codice, cercare `TODO:`):
- **UpnpBrowser**: scritto seguendo l'API standard jupnp/Cling ma MAI
  compilato in CI — è il pezzo con più probabilità di richiedere un fix
  al primo giro di build (nomi metodi/import leggermente diversi tra
  versioni jupnp). Se fallisce, guarda l'errore di compilazione e
  aggiustiamo import/firma dei metodi.
- Discovery UPnP: oggi lo UDN del server va inserito a mano nella config
  shortcut; manca una schermata che lista i server trovati dalla ricerca
  automatica (UpnpServiceHolder già fa `controlPoint.search()` all'avvio).
- VU-meter L/R discreti (oggi singolo meter — serve AudioProcessor custom
  pre-mix per uno split stereo reale)
- Vero crossfade a due player (oggi è fade-out/fade-in sul singolo player)
- Vertical fader look nell'EQ (oggi slider orizzontali, funzionalmente
  identici ma esteticamente diversi dallo screenshot di riferimento)

## Build locale

Serve Gradle 8.7 + JDK 17 (il repo non include il wrapper binario, vedi CI).

```
gradle assembleDebug
```

## CI

`.github/workflows/build-debug.yml` produce un artifact `D-Player-debug`
scaricabile dalla tab Actions ad ogni push su `main`.
