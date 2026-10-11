# MG4 Launcher

🇮🇹 [Italiano](#italiano) · 🇬🇧 [English](#english)

**Ultima versione / Latest version: [0.3.1](releases/MG4Launcher-0.3.1.apk)**

> Progetto amatoriale e **non ufficiale**: non è affiliato né approvato da MG Motor o SAIC Motor.
> I nomi MG / MG4 servono solo a indicare l'auto compatibile. Installalo e usalo a tuo rischio.
>
> Hobby project, **not official**: not affiliated with or endorsed by MG Motor or SAIC Motor.
> The MG / MG4 names only identify the compatible car. Install and use it at your own risk.

---

## Italiano

Launcher in stile Tesla per l'infotainment della **MG4** (EH32, Android Automotive 9).
Sostituisce la schermata home originale mantenendone le funzioni, con una grafica nuova e pulita.

### Cosa fa

- **Auto e batteria** (a sinistra): autonomia, percentuale, barra batteria con tacca del limite di ricarica,
  stato di ricarica e V2L, temperatura esterna. La tua MG4 ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
  Tocca l'auto → Gestione energia. Tienila premuta → impostazioni del launcher.
- **Mappa / Media / CarPlay** (a destra):
  - **Mappa**: indicazioni del navigatore (prossima manovra, via, arrivo). Senza guida: Navigatore, Casa, Lavoro
    e la riga **Apri con** (MG, CarPlay/Android Auto se il telefono è collegato, Waze/Google Maps se installati sull'auto).
    Tieni premuto un pulsante della riga per renderlo il navigatore predefinito.
  - **Media**: parte sempre dalla radio; le altre sorgenti si scelgono con le frecce ‹ ›
    (USB, Bluetooth, Online, CarPlay/Android Auto). Per la radio: logo della stazione e preferiti FM/DAB
    con i loghi, da toccare per cambiare stazione.
  - **CarPlay / Android Auto**: la scheda compare da sola quando colleghi il telefono.
- **Barra in basso**: temperatura ‹ › (17 = LO … 33 = HI), ventola ‹ › (OFF / 1…11 / A = automatico;
  tocca l'icona = app Clima, tieni premuto = accendi/spegni), mini-player, Telefono, Radio,
  Telecamera 360°, Impostazioni, App.
- **App**: scorri la home verso sinistra (o tocca il pulsante App) per le app dell'auto e quelle che installi tu,
  con le icone tutte nello stesso stile; scorri verso destra per tornare. Con **Modifica** scegli quali app installate mostrare.
- **Impostazioni** (tieni premuta l'auto): colore dell'auto, tetto nero, tema (automatico, notte, giorno),
  navigatore predefinito (Automatico, MG, Telefono), aggiornamenti, diagnostica.
- **Schermo intero**: barra di stato e dock MG nascosti (trascina dal bordo per richiamarli).
- **Aggiornamenti**: le nuove versioni si scaricano e si installano dalle impostazioni, senza computer.

### Installazione

Serve un computer con [adb](https://developer.android.com/tools/releases/platform-tools) collegato all'auto.
Il firmware MG blocca l'installazione normale delle app: l'opzione `-f` la permette.

1. Scarica l'APK dalla cartella [`releases/`](releases/).
2. Installa:
   ```bash
   adb install -r -f MG4Launcher-0.3.1.apk
   ```
3. Rendilo la home predefinita:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   In alternativa premi il tasto Home: Android chiede quale home usare.

Il launcher parte già all'accensione dell'auto, come la home originale.

### Aggiornamenti

Impostazioni del launcher → **Aggiornamenti** → **Controlla aggiornamenti**.
Se c'è una versione più nuova compare **Scarica e installa**: il launcher la installa da solo e si riavvia.
Gli aggiornamenti si possono installare anche da computer, con lo stesso comando dell'installazione.

### Tornare alla home MG originale

Dal launcher: impostazioni → **Apri la home originale MG**. Per renderla di nuovo quella predefinita:

```bash
adb shell cmd package set-home-activity com.saicmotor.hmi.launcher/.ui.MainActivity
```

Per rimuovere del tutto il launcher: `adb uninstall com.mg4.launcher`. L'app originale non viene mai modificata.

### Comandi utili

```bash
# riavviare il launcher senza spegnere l'auto
adb shell am force-stop com.mg4.launcher
adb shell am start -n com.mg4.launcher/.MainActivity

# vedere i valori letti dall'auto (Ctrl+C per fermare)
adb logcat -s MG4L MG4L.Link

# scaricare la diagnostica salvata dal launcher (aggiornata ogni 15 s)
adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt
```

### Segnalare un problema

Nelle impostazioni la colonna **Diagnostica servizi auto** mostra i valori grezzi letti da ogni servizio
(veicolo, clima, media, radio, CarPlay, navigatore) e gli errori recenti. Uno screenshot di quella colonna
o il file `diagnostica.txt` bastano per capire cosa succede.

### Versioni

#### 0.3.1 — 11 ottobre 2026
- Radio: il pulsante play/pausa ora mostra lo stato reale e la radio riparte dopo la pausa
  (pausa e ripresa come nella home originale).
- Navigatore: riga **Apri con** nel pannello Mappa (MG, CarPlay/Android Auto, Waze/Google Maps installati)
  e scelta del **navigatore predefinito** nelle impostazioni (Automatico = telefono se collegato, altrimenti MG).
- README in italiano e inglese.

#### 0.3.0 — 11 ottobre 2026
- Nuova icona Telecamera 360°, nella barra in basso e tra le app.
- Temperatura esterna accanto allo stato di ricarica.
- Home e App a scorrimento orizzontale.
- Tolto il colore d'accento: pulsanti e icone hanno tutti lo stesso colore. Tolte le scritte superflue.
- Media: radio di default, riquadro ridisegnato, preferiti FM/DAB con i loghi delle stazioni
  (DAB dalla stazione, FM da radio-browser.info, altrimenti iniziali).
- Navigatore: il launcher riceve le notifiche del servizio di navigazione come la home originale.
- Diagnostica su logcat e su file. Tolto il campo del token (repository pubblico).

#### 0.2.0 — 11 ottobre 2026
- Schermo intero; avvio all'accensione come la home originale.
- Radio riconosciuta correttamente; pannello Mappa aggiornato all'avvio della guida, con prossima manovra.
- Temperatura senza rimbalzo al primo tocco; controllo della ventola nella barra in basso.
- Scelta delle app installate da mostrare (**Modifica**); aggiornamenti da GitHub dalle impostazioni.

#### 0.1.0 — prima versione
- Home in stile Tesla: autonomia, percentuale, barra batteria, stato di ricarica e V2L; MG4 ricolorabile.
- Schede Mappa, Media e CarPlay/Android Auto; barra in basso con clima, mini-player e scorciatoie.
- Griglia App con le app installate aggiunte in automatico; tema automatico, notte o giorno; Diagnostica.

#### In lavorazione
- "Ricarica fino a" mostra un valore sbagliato.
- Il consumo non viene ancora mostrato.
- Verifica sull'auto delle indicazioni del navigatore MG.

### Compilare

Nessun Gradle: `./build.sh` usa aapt2, javac (bytecode 8), D8 e firma v1+v2 con la chiave platform AOSP
(la stessa del sistema dell'auto, build test-keys). L'APK esce in `build/`. La versione è in `version.properties`.

Prima di compilare metti in `tools/` i due file non inclusi nel repository:
- `android.jar` (API 28) da [Sable/android-platforms](https://github.com/Sable/android-platforms);
- `d8.jar`: è `lib/jadx-1.5.1-all.jar` di [jadx 1.5.1](https://github.com/skylot/jadx/releases/tag/v1.5.1), rinominato.

`keys/platform.p12` è la chiave platform **pubblica** di test AOSP (password `android`).

**Pubblicare una nuova versione:** aumenta `VERSION_CODE` e `VERSION_NAME` in `version.properties`, compila,
copia l'APK in `releases/`, aggiorna `releases/latest.json` (versione, nome del file e dimensione) e le Versioni
qui sopra, poi commit e push. Il launcher controlla `releases/latest.json` e anche le Release di GitHub
(tag `vX.Y.Z` con l'APK allegato) e propone la più recente.

### Come comunica con l'auto

| Funzione | Servizio | Note |
|---|---|---|
| Energia, ricarica, V2L | `com.saicmotor.service.vehicle` → hub → `vehiclecharging` | |
| Clima, ventola, temperatura esterna | stesso hub → `aircondition` | temperatura 17–33, ventola 1–11 / auto |
| USB / Bluetooth / Online | `com.saicmotor.service.media` | |
| Radio | `com.saicmotor.service.radio` | FM / AM / DAB, preferiti, logo DAB |
| CarPlay / Android Auto | `com.allgo.rui` + MediaBrowser `com.allgo.remoteui.mediabrowserservice` | metadati e indicazioni |
| Navigatore MG | `com.saicmotor.adapterservice` (GeneralService + notifiche) | |

La mappa del navigatore non può essere mostrata dentro il launcher (è un'altra app):
il pannello Mappa mostra le indicazioni e apre il navigatore a schermo intero.

### Note

- L'immagine dell'auto deriva da una foto ufficiale MG; marchi e immagini appartengono ai rispettivi proprietari.
- I loghi delle stazioni FM vengono da [radio-browser.info](https://www.radio-browser.info), database pubblico e gratuito.

---

## English

Tesla-style launcher for the **MG4** infotainment system (EH32, Android Automotive 9).
It replaces the original home screen, keeping its features, with a new, clean design.

### Features

- **Car and battery** (left): range, percentage, battery bar with a charge-limit mark, charging and V2L status,
  outside temperature. Your MG4, recolourable with the 8 factory colours, optional black roof.
  Tap the car → Energy management. Long-press it → launcher settings.
- **Map / Media / CarPlay** (right):
  - **Map**: navigation guidance (next manoeuvre, street, arrival). When not navigating: Navigator, Home, Work
    and the **Open with** row (MG, CarPlay/Android Auto when a phone is connected, Waze/Google Maps if installed on the car).
    Long-press a button in that row to make it the default navigator.
  - **Media**: always starts on the radio; pick other sources with the ‹ › arrows
    (USB, Bluetooth, Online, CarPlay/Android Auto). For the radio: station logo and FM/DAB favourites with logos,
    tap one to tune in.
  - **CarPlay / Android Auto**: the tab appears by itself when you connect a phone.
- **Bottom bar**: temperature ‹ › (17 = LO … 33 = HI), fan ‹ › (OFF / 1…11 / A = auto;
  tap the icon = Climate app, long-press = climate on/off), mini player, Phone, Radio,
  360° camera, Settings, Apps.
- **Apps**: swipe the home screen left (or tap the Apps button) for the car's apps and the ones you install,
  all with matching icons; swipe right to go back. Use **Modifica** (Edit) to choose which installed apps to show.
- **Settings** (long-press the car): car colour, black roof, theme (auto, night, day),
  default navigator (Auto, MG, Phone), updates, diagnostics.
- **Full screen**: status bar and MG dock hidden (swipe from the edge to bring them back).
- **Updates**: new versions download and install from the settings, no computer needed.

The app's interface is in Italian.

### Installation

You need a computer with [adb](https://developer.android.com/tools/releases/platform-tools) connected to the car.
The MG firmware blocks normal app installs; the `-f` option allows it.

1. Download the APK from the [`releases/`](releases/) folder.
2. Install it:
   ```bash
   adb install -r -f MG4Launcher-0.3.1.apk
   ```
3. Make it the default home:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   Or press the Home button: Android asks which home app to use.

The launcher starts as soon as the car switches on, like the original home.

### Updates

Launcher settings → **Aggiornamenti** (Updates) → **Controlla aggiornamenti** (Check for updates).
If a newer version exists, **Scarica e installa** (Download and install) appears: the launcher installs it and restarts.
Updates can also be installed from a computer with the installation command.

### Going back to the original MG home

From the launcher: settings → **Apri la home originale MG** (Open the original MG home). To make it the default again:

```bash
adb shell cmd package set-home-activity com.saicmotor.hmi.launcher/.ui.MainActivity
```

To remove the launcher completely: `adb uninstall com.mg4.launcher`. The original app is never modified.

### Useful commands

```bash
# restart the launcher without switching the car off
adb shell am force-stop com.mg4.launcher
adb shell am start -n com.mg4.launcher/.MainActivity

# watch the values read from the car (Ctrl+C to stop)
adb logcat -s MG4L MG4L.Link

# download the diagnostics saved by the launcher (refreshed every 15 s)
adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt
```

### Reporting a problem

In the settings, the **Diagnostica servizi auto** (car services diagnostics) column shows the raw values read from
each service (vehicle, climate, media, radio, CarPlay, navigator) and recent errors. A screenshot of that column
or the `diagnostica.txt` file is enough to understand what is going on.

### Changelog

#### 0.3.1 — 11 October 2026
- Radio: the play/pause button now shows the real state and the radio resumes after a pause
  (pause and resume work like the original home).
- Navigator: **Open with** row in the Map panel (MG, CarPlay/Android Auto, installed Waze/Google Maps)
  and a **default navigator** choice in the settings (Auto = phone when connected, otherwise MG).
- README in Italian and English.

#### 0.3.0 — 11 October 2026
- New 360° camera icon, in the bottom bar and in the app grid.
- Outside temperature next to the charging status.
- Home and Apps as horizontally swipeable pages.
- Accent colour removed: all buttons and icons share the same colour. Unnecessary captions removed.
- Media: radio by default, redesigned panel, FM/DAB favourites with station logos
  (DAB from the station, FM from radio-browser.info, otherwise initials).
- Navigator: the launcher receives the navigation service notifications, like the original home.
- Diagnostics on logcat and to a file. GitHub token field removed (public repository).

#### 0.2.0 — 11 October 2026
- Full screen; starts at power-on like the original home.
- Radio correctly detected; Map panel updates when guidance starts, with next manoeuvre.
- No temperature bounce on the first tap; fan control in the bottom bar.
- Choose which installed apps to show (**Modifica**); updates from GitHub in the settings.

#### 0.1.0 — first version
- Tesla-style home: range, percentage, battery bar, charging and V2L status; recolourable MG4.
- Map, Media and CarPlay/Android Auto tabs; bottom bar with climate, mini player and shortcuts.
- App grid with installed apps added automatically; auto, night or day theme; Diagnostics.

#### Work in progress
- "Ricarica fino a" (charge limit) shows a wrong value.
- Energy consumption is not shown yet.
- On-car check of MG navigator guidance.

### Building

No Gradle: `./build.sh` uses aapt2, javac (bytecode 8), D8 and v1+v2 signing with the AOSP platform key
(the same as the car's system, test-keys build). The APK ends up in `build/`. The version is in `version.properties`.

Before building, put the two files not included in the repository into `tools/`:
- `android.jar` (API 28) from [Sable/android-platforms](https://github.com/Sable/android-platforms);
- `d8.jar`: it is `lib/jadx-1.5.1-all.jar` from [jadx 1.5.1](https://github.com/skylot/jadx/releases/tag/v1.5.1), renamed.

`keys/platform.p12` is the **public** AOSP test platform key (password `android`).

**Publishing a new version:** bump `VERSION_CODE` and `VERSION_NAME` in `version.properties`, build,
copy the APK to `releases/`, update `releases/latest.json` (version, file name and size) and the changelog above,
then commit and push. The launcher checks `releases/latest.json` and GitHub Releases
(tag `vX.Y.Z` with the APK attached) and offers the newest one.

### How it talks to the car

| Feature | Service | Notes |
|---|---|---|
| Energy, charging, V2L | `com.saicmotor.service.vehicle` → hub → `vehiclecharging` | |
| Climate, fan, outside temperature | same hub → `aircondition` | temperature 17–33, fan 1–11 / auto |
| USB / Bluetooth / Online | `com.saicmotor.service.media` | |
| Radio | `com.saicmotor.service.radio` | FM / AM / DAB, favourites, DAB logo |
| CarPlay / Android Auto | `com.allgo.rui` + MediaBrowser `com.allgo.remoteui.mediabrowserservice` | metadata and guidance |
| MG navigator | `com.saicmotor.adapterservice` (GeneralService + notifications) | |

The navigator's map cannot be shown inside the launcher (it is a separate app):
the Map panel shows the guidance and opens the navigator full screen.

### Notes

- The car image is derived from an official MG photo; trademarks and images belong to their respective owners.
- FM station logos come from [radio-browser.info](https://www.radio-browser.info), a free public database.
