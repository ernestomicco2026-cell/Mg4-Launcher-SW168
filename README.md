# MG4 Launcher

Launcher in stile Tesla per l'infotainment della MG4 (EH32, Android Automotive 9).

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

## Installazione (prova)

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

Il repository è privato, quindi serve un token GitHub di sola lettura:
GitHub → Settings → Developer settings → Fine-grained tokens → accesso solo a `mg4-launcher`, permesso *Contents: Read-only*.
Si può scrivere nel campo delle impostazioni oppure inviarlo dal computer:

```bash
adb shell am broadcast -n com.mg4.launcher/.svc.TokenReceiver --es token github_pat_XXXX
```

Per pubblicare una versione: aumenta `version.properties`, `./build.sh`, poi crea una release con tag `vX.Y.Z`
e allega `build/MG4Launcher-X.Y.Z.apk`.

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
