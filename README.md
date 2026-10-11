# MG4 Launcher

Launcher in stile Tesla per l'infotainment della **MG4** (EH32, Android Automotive 9).
Sostituisce la schermata home originale mantenendone le funzioni, con una grafica nuova e pulita.

> Progetto amatoriale e **non ufficiale**: non è affiliato né approvato da MG Motor o SAIC Motor.
> I nomi MG / MG4 servono solo a indicare l'auto compatibile. Installalo e usalo a tuo rischio.

**Ultima versione: [0.3.0](releases/MG4Launcher-0.3.0.apk)** · [novità](#versioni)

## Cosa fa

- **Auto e batteria** (a sinistra): autonomia, percentuale, barra batteria con tacca del limite di ricarica,
  stato di ricarica e V2L, temperatura esterna. La tua MG4 ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
  Tocca l'auto → Gestione energia. Tienila premuta → impostazioni del launcher.
- **Mappa / Media / CarPlay** (a destra):
  - **Mappa**: indicazioni del navigatore MG o di CarPlay/Android Auto (prossima manovra, via, arrivo),
    oppure Navigatore / Casa / Lavoro quando non stai guidando.
  - **Media**: parte sempre dalla radio; le altre sorgenti si scelgono con le frecce ‹ ›
    (USB, Bluetooth, Online, CarPlay/Android Auto). Per la radio: logo della stazione e preferiti FM/DAB
    con i loghi, da toccare per cambiare stazione.
  - **CarPlay / Android Auto**: la scheda compare da sola quando colleghi il telefono.
- **Barra in basso**: temperatura ‹ › (17 = LO … 33 = HI), ventola ‹ › (OFF / 1…11 / A = automatico;
  tocca l'icona = app Clima, tieni premuto = accendi/spegni), mini-player, Telefono, Radio,
  Telecamera 360°, Impostazioni, App.
- **App**: scorri la home verso sinistra (o tocca il pulsante App) per le app dell'auto e quelle che installi tu,
  con le icone tutte nello stesso stile; scorri verso destra per tornare. Con **Modifica** scegli quali app installate mostrare.
- **Tema**: automatico (segue giorno/notte dell'auto), notte o giorno.
- **Schermo intero**: barra di stato e dock MG nascosti (trascina dal bordo per richiamarli).
- **Aggiornamenti**: le nuove versioni si scaricano e si installano dalle impostazioni, senza computer.

## Installazione

Serve un computer con [adb](https://developer.android.com/tools/releases/platform-tools) collegato all'auto.
Il firmware MG blocca l'installazione normale delle app: l'opzione `-f` la permette.

1. Scarica l'APK dalla cartella [`releases/`](releases/).
2. Installa:
   ```bash
   adb install -r -f MG4Launcher-0.3.0.apk
   ```
3. Rendilo la home predefinita:
   ```bash
   adb shell cmd package set-home-activity com.mg4.launcher/.MainActivity
   ```
   In alternativa premi il tasto Home: Android chiede quale home usare.

Il launcher parte già all'accensione dell'auto, come la home originale.

## Aggiornamenti

Impostazioni del launcher (tieni premuta l'auto) → **Aggiornamenti** → **Controlla aggiornamenti**.
Se c'è una versione più nuova compare **Scarica e installa**: il launcher la installa da solo e si riavvia.

Gli aggiornamenti si possono installare anche da computer, con lo stesso comando dell'installazione.

## Tornare alla home MG originale

Dal launcher: impostazioni → **Apri la home originale MG**. Per renderla di nuovo quella predefinita:

```bash
adb shell cmd package set-home-activity com.saicmotor.hmi.launcher/.ui.MainActivity
```

Per rimuovere del tutto il launcher:

```bash
adb uninstall com.mg4.launcher
```

L'app originale non viene mai modificata.

## Comandi utili

```bash
# riavviare il launcher senza spegnere l'auto
adb shell am force-stop com.mg4.launcher
adb shell am start -n com.mg4.launcher/.MainActivity

# vedere i valori letti dall'auto (Ctrl+C per fermare)
adb logcat -s MG4L MG4L.Link

# salvare il registro in un file
adb logcat -d -s MG4L MG4L.Link > log-launcher.txt

# scaricare la diagnostica salvata dal launcher
adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt
```

## Segnalare un problema

Nelle impostazioni del launcher la colonna **Diagnostica servizi auto** mostra i valori grezzi letti
da ogni servizio (veicolo, clima, media, radio, CarPlay, navigatore) e gli errori recenti.
Se qualcosa non torna (un valore strano, una funzione che non risponde), uno screenshot di quella colonna
o il file `diagnostica.txt` (vedi Comandi utili) bastano per capire cosa succede.

## Versioni

### 0.3.0 — 11 ottobre 2026

**Home**
- Nuova icona Telecamera 360° (auto vista dall'alto con freccia circolare), nella barra in basso e tra le app.
- Temperatura esterna accanto allo stato di ricarica.
- Home e App sono due pagine affiancate: scorri verso sinistra per le app, verso destra per tornare alla home.
- Tolto il colore d'accento: pulsanti e icone hanno tutti lo stesso colore.
- Tolte le scritte superflue nella schermata App.

**Media**
- Il riquadro media mostra sempre la radio di default (le altre sorgenti con le frecce).
- Riquadro ridisegnato: sorgente in alto, logo/frequenza centrati, titolo e testo RDS/DAB, comandi e bande sulla stessa riga.
- Preferiti FM e DAB con i loghi delle stazioni: tocca per sintonizzare.
  Loghi DAB trasmessi dalla stazione; loghi FM cercati online (radio-browser.info) e salvati; altrimenti le iniziali.

**Navigazione**
- Il launcher riceve le notifiche del servizio di navigazione come la home originale (inizio/fine guida,
  prossima manovra, via, distanza e tempo rimanenti), oltre alle letture periodiche.

**Diagnostica**
- I valori vanno su logcat a livello info e in `Android/data/com.mg4.launcher/files/diagnostica.txt`
  (aggiornato ogni 15 s).

**Aggiornamenti**
- Tolto il campo del token: il repository è pubblico.

### 0.2.0 — 11 ottobre 2026

**Schermata**
- Schermo intero: barra di stato e dock MG nascosti, il launcher usa tutti i 1920×720 px
  (trascinando dal bordo le barre compaiono sopra, senza spostare il contenuto).
- Parte già all'accensione, prima dello sblocco dell'utente Android, come la home originale.
  Colore auto, accento e tema vanno reimpostati una volta dopo l'aggiornamento dalla 0.1.0.

**Media**
- Radio riconosciuta correttamente quando è in riproduzione.
- Una sorgente non gestita (es. video USB) non viene più confusa con un'altra.

**Navigazione**
- Il pannello Mappa si aggiorna all'avvio della guida del navigatore.
- La card mostra la distanza dalla prossima manovra e la via, oltre a minuti rimanenti e orario di arrivo.

**Clima**
- Corretto il rimbalzo della temperatura al primo tocco (23 → 24 → 23).
- Nuovo controllo della ventola nella barra in basso: ‹ OFF / 1…11 / A ›.

**App**
- Pulsante **Modifica** nella schermata App per scegliere quali app installate mostrare.

**Aggiornamenti**
- Impostazioni → **Aggiornamenti**: controlla, scarica e installa le nuove versioni da GitHub.

### 0.1.0 — prima versione

- Home in stile Tesla: autonomia, percentuale, barra batteria con tacca del limite di ricarica, stato di ricarica e V2L.
- MG4 ricolorabile con gli 8 colori della gamma, tetto nero opzionale.
- Schede Mappa, Media (con frecce per la sorgente) e CarPlay/Android Auto automatica.
- Barra in basso: temperatura clima, mini-player e scorciatoie.
- Griglia App con le app installate aggiunte in automatico e icone ridisegnate.
- Tema automatico, notte o giorno; 4 colori d'accento.
- Colonna Diagnostica nelle impostazioni.

### In lavorazione

- "Ricarica fino a" mostra un valore sbagliato.
- Il consumo non viene ancora mostrato.

## Compilare

Nessun Gradle: `./build.sh` usa aapt2, javac (bytecode 8), D8 e firma v1+v2 con la chiave platform AOSP
(la stessa del sistema dell'auto, build test-keys). L'APK esce in `build/`. La versione è in `version.properties`.

Prima di compilare metti in `tools/` i due file non inclusi nel repository:
- `android.jar` (API 28) da [Sable/android-platforms](https://github.com/Sable/android-platforms);
- `d8.jar`: è `lib/jadx-1.5.1-all.jar` di [jadx 1.5.1](https://github.com/skylot/jadx/releases/tag/v1.5.1), rinominato.

`keys/platform.p12` è la chiave platform **pubblica** di test AOSP (password `android`).

### Pubblicare una nuova versione

1. Aumenta `VERSION_CODE` e `VERSION_NAME` in `version.properties` e compila con `./build.sh`.
2. Copia l'APK in `releases/` e aggiorna `releases/latest.json` (versione, nome del file e dimensione).
3. Aggiungi le novità nella sezione [Versioni](#versioni), poi commit e push.

Il launcher controlla `releases/latest.json` e anche le Release di GitHub (tag `vX.Y.Z` con l'APK allegato),
e propone la più recente tra le due.

## Come comunica con l'auto

Le informazioni arrivano dai servizi di sistema dell'auto, individuati analizzando il firmware:

| Funzione | Servizio | Note |
|---|---|---|
| Energia, ricarica, V2L | `com.saicmotor.service.vehicle` → hub → `vehiclecharging` | |
| Clima e ventola | stesso hub → `aircondition` | temperatura 17–33, ventola 1–11 / auto |
| USB / Bluetooth / Online | `com.saicmotor.service.media` | |
| Radio | `com.saicmotor.service.radio` | FM / AM / DAB |
| CarPlay / Android Auto | `com.allgo.rui` + MediaBrowser `com.allgo.remoteui.mediabrowserservice` | metadati e indicazioni |
| Navigatore MG | `com.saicmotor.adapterservice` (GeneralService) | |

La mappa del navigatore non può essere mostrata dentro il launcher (è un'altra app):
il pannello Mappa mostra le indicazioni e apre il navigatore a schermo intero.

## Note

- L'immagine dell'auto deriva da una foto ufficiale MG; marchi e immagini appartengono ai rispettivi proprietari.
