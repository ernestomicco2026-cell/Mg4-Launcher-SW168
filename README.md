# MG4 Launcher

Launcher in stile Tesla per l'infotainment della MG4 (EH32, Android Automotive 9).

> Progetto amatoriale e **non ufficiale**: non è affiliato né approvato da MG Motor o SAIC Motor.
> I nomi MG / MG4 servono solo a indicare l'auto compatibile. Installalo e usalo a tuo rischio.

- **Sinistra**: autonomia, percentuale, barra batteria (con tacca del limite di ricarica), stato di ricarica/V2L
  e la tua MG4 ricolorabile (8 colori MG4 + tetto nero). Tocca → Gestione energia. Tieni premuto → impostazioni del launcher.
- **Destra**: schede **Mappa** (guida in corso dal navigatore MG o da CarPlay/AA, oppure Navigatore/Casa/Lavoro),
  **Media** (sorgenti Radio, USB, Bluetooth, Online, CarPlay/AA con le frecce ‹ ›, indietro/play/avanti)
  e **CarPlay/Android Auto** (compare da sola quando colleghi il telefono).
- **In basso**: temperatura clima (‹ ›, 17 = LO … 33 = HI), velocità ventola (‹ ›, OFF / 1…11 / A = automatico;
  tocca l'icona = app Clima, tieni premuto = accendi/spegni),
  mini-player, Telefono, Radio, Telecamera 360°, Impostazioni, App.
- **App**: app dell'auto con icone disegnate + app installate da te, aggiunte in automatico con l'icona ridisegnata.
  Con **Modifica** scegli quali app installate mostrare (tocca per nascondere/rimettere, poi **Fine**).
- **Tema**: automatico (segue giorno/notte dell'auto), notte o giorno; 4 colori d'accento.

## Versioni

### 0.2.0 — 11 ottobre 2026

**Schermata**
- Schermo intero: barra di stato e dock MG nascosti, il launcher usa tutti i 1920×720 px
  (trascinando dal bordo le barre compaiono sopra, senza spostare il contenuto).
- Parte già prima dello sblocco dell'utente Android, come la home originale (avvio più rapido all'accensione).
  Le impostazioni ora stanno nella memoria disponibile all'avvio: colore auto, accento e tema vanno reimpostati una volta.

**Media**
- Radio riconosciuta correttamente quando è in riproduzione (sorgente audio 3 = radio).
- Se l'auto indica una sorgente non gestita (es. video USB) il launcher non la confonde più con un'altra.

**Navigazione**
- Il pannello Mappa si aggiorna all'avvio della guida: oltre a `isMapNavigating` usa lo stato guida,
  la distanza rimanente che cambia e gli avvisi diretti del navigatore (TomTom / navimaps).
- La card mostra la distanza dalla prossima manovra e la via, oltre a minuti rimanenti e orario di arrivo.

**Clima**
- Corretto il rimbalzo della temperatura al primo tocco (23 → 24 → 23): il valore scelto resta fermo
  per qualche secondo mentre l'auto lo applica.
- Nuovo controllo della ventola nella barra in basso: ‹ OFF / 1…11 / A (automatico) ›.
  Tocca l'icona = app Clima, tieni premuto = accendi/spegni il clima.

**App**
- Pulsante **Modifica** nella schermata App: scegli quali app installate mostrare.

**Aggiornamenti**
- Impostazioni → **Aggiornamenti**: controlla, scarica e installa le nuove versioni da GitHub
  (cartella `releases/` del repository o Release GitHub), senza computer.
- Token GitHub opzionale per il repository privato, inseribile dalle impostazioni o via adb.

**Note**
- Avviso nel README: progetto non ufficiale.

### 0.1.0 — prima versione

- Home in stile Tesla: autonomia, percentuale, barra batteria con tacca del limite di ricarica, stato di ricarica e V2L.
- MG4 disegnata dalla foto ufficiale e ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
- Schede **Mappa** (navigatore MG, Casa/Lavoro, indicazioni di CarPlay/Android Auto), **Media**
  (Radio, USB, Bluetooth, Online, CarPlay/Android Auto con frecce ‹ › e comandi indietro/play/avanti)
  e **CarPlay/Android Auto**, che compare da sola quando colleghi il telefono.
- Barra in basso: temperatura clima, mini-player, Telefono, Radio, Telecamera 360°, Impostazioni, App.
- Griglia App: app dell'auto con icone disegnate + app installate aggiunte in automatico con icona ridisegnata.
- Tema automatico (giorno/notte dell'auto), notte o giorno; 4 colori d'accento.
- Colonna **Diagnostica** nelle impostazioni con i valori grezzi dei servizi dell'auto.
- Compilazione senza Gradle e firma con la chiave platform AOSP.

### Da sistemare
- "Ricarica fino a" mostra un valore sbagliato (5%): serve il valore grezzo dalla Diagnostica.
- Il consumo non viene ancora mostrato.

## Installazione

```bash
adb install -r -f MG4Launcher-0.2.0.apk
# rendilo la home predefinita
adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
```

In alternativa premi il tasto Home: Android chiede quale home usare.

Il launcher è a schermo intero (nasconde barra di stato e dock MG; trascina dal bordo per farli ricomparire)
e parte già prima dello sblocco dell'utente, come la home originale.

## Aggiornamenti da GitHub

Impostazioni → **Aggiornamenti** → *Controlla aggiornamenti*: legge l'ultima release di questo repository e,
se c'è una versione più nuova, *Scarica e installa* la installa da sola (il launcher si riavvia).

Se il repository è pubblico non serve altro. Se è privato serve un token GitHub di sola lettura:
GitHub → Settings → Developer settings → Fine-grained tokens → accesso solo a `mg4-launcher`, permesso *Contents: Read-only*.
Si può scrivere nel campo delle impostazioni oppure inviarlo dal computer:

```bash
adb shell am broadcast -n com.mg4.launcher/.svc.TokenReceiver --es token github_pat_XXXX
```

Per pubblicare una versione: aumenta `version.properties`, `./build.sh`, copia l'APK in `releases/` e aggiorna
`releases/latest.json` (versione e nome file), poi fai commit e push. In alternativa funziona anche una Release GitHub
con tag `vX.Y.Z` e l'APK allegato: il launcher prende la più recente tra le due.

## Tornare alla home MG originale

```bash
adb shell cmd package set-home-activity com.saicmotor.hmi.launcher/.ui.MainActivity
# oppure rimuovi del tutto il launcher
adb uninstall com.mg4.launcher
```

Dal launcher: impostazioni (tieni premuta l'auto) → **Apri la home originale MG**.
L'app originale non viene mai modificata.

## Diagnostica (prima prova sull'auto)

Impostazioni → colonna **Diagnostica servizi auto**: mostra i valori grezzi letti da ogni servizio
(veicolo, clima, media, radio, CarPlay, navigatore) e gli errori recenti. Gli stessi dati vanno su logcat:

```bash
adb logcat -s MG4L MG4L.Link
```

Se un valore è strano (unità, scala, stato), uno screenshot di quella colonna basta per correggerlo.

## Compilare

Nessun Gradle: `./build.sh` usa aapt2, javac (bytecode 8), D8 e firma v1+v2 con la chiave platform AOSP
(la stessa del sistema dell'auto, build test-keys). Strumenti in `tools/`, chiavi in `keys/`.
La versione è in `version.properties`.

Per non appesantire l'archivio, `tools/android.jar` (API 28, da github.com/Sable/android-platforms) e `tools/d8.jar`
(D8 + apksig, preso da jadx 1.5.1 `lib/jadx-1.5.1-all.jar`) non sono inclusi: vanno rimessi in `tools/` prima di compilare.
`keys/platform.p12` è la chiave platform **pubblica** di test AOSP (password `android`).

## Servizi usati (dall'analisi del firmware)

| Funzione | Servizio | Note |
|---|---|---|
| Energia, ricarica, V2L | `com.saicmotor.service.vehicle` → hub → `vehiclecharging` | codici da vehiclesettingservice_eh32_eu_P |
| Clima | stesso hub → `aircondition` | temperatura intera 17–33 |
| USB / BT / Online | `com.saicmotor.service.media` (azioni MUSIC_PLAYER, BT_MUSIC, ONLINE_MUSIC, PLAY_STATUS) | |
| Radio | `com.saicmotor.service.radio` | FM/AM/DAB |
| CarPlay / Android Auto | `com.allgo.rui` + MediaBrowser `com.allgo.remoteui.mediabrowserservice` | metadati e indicazioni |
| Navigatore MG | `com.saicmotor.adapterservice` GeneralService | |

La mappa del navigatore non può essere mostrata dentro il launcher (è un'altra app):
il pannello Mappa mostra le indicazioni e apre il navigatore a schermo intero.

## Note

- L'immagine dell'auto deriva da una foto ufficiale MG: va bene per uso personale; per distribuire il launcher
  conviene sostituirla con una foto propria (`art/` + `app/res/drawable-nodpi/car_*.png`).
