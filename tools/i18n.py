#!/usr/bin/env python3
"""Genera app/res/values*/strings.xml con i testi del launcher in 7 lingue.

Ogni voce: chiave -> (it, en, es, de, pt, fr, tr). L'inglese è la lingua di riserva (cartella values/).
Uso: python3 tools/i18n.py   (poi ./build.sh)
"""
import os
import re
import sys

LANGS = ["it", "en", "es", "de", "pt", "fr", "tr"]

S = {
    # --- home: auto e batteria ---
    "connecting_vehicle": ("Collegamento al veicolo…", "Connecting to the car…", "Conectando con el vehículo…", "Verbindung zum Fahrzeug…", "A ligar ao veículo…", "Connexion au véhicule…", "Araca bağlanılıyor…"),
    "vehicle_unavailable": ("Servizio veicolo non disponibile", "Vehicle service unavailable", "Servicio del vehículo no disponible", "Fahrzeugdienst nicht verfügbar", "Serviço do veículo indisponível", "Service du véhicule indisponible", "Araç hizmeti kullanılamıyor"),
    "chip_consumption": ("Consumo medio", "Avg. consumption", "Consumo medio", "Ø Verbrauch", "Consumo médio", "Conso. moyenne", "Ort. tüketim"),
    "chip_limit": ("Ricarica fino a", "Charge limit", "Cargar hasta", "Laden bis", "Carregar até", "Charger jusqu'à", "Şarj sınırı"),
    "v2l_desc": ("V2L, apri la gestione della scarica", "V2L, open discharge management", "V2L, abrir la gestión de descarga", "V2L, Entladeverwaltung öffnen", "V2L, abrir a gestão de descarga", "V2L, ouvrir la gestion de la décharge", "V2L, deşarj yönetimini aç"),
    "on": ("Attivo", "On", "Activo", "Aktiv", "Ativo", "Activé", "Açık"),
    "off": ("Spento", "Off", "Apagado", "Aus", "Desligado", "Désactivé", "Kapalı"),
    "full_at": ("piena alle %s", "full at %s", "llena a las %s", "voll um %s", "cheia às %s", "pleine à %s", "%s itibarıyla dolu"),
    "charge_charging": ("In carica", "Charging", "Cargando", "Lädt", "A carregar", "En charge", "Şarj oluyor"),
    "charge_fast": ("Ricarica rapida", "Fast charging", "Carga rápida", "Schnellladen", "Carregamento rápido", "Charge rapide", "Hızlı şarj"),
    "charge_wireless": ("Ricarica wireless", "Wireless charging", "Carga inalámbrica", "Kabelloses Laden", "Carregamento sem fios", "Charge sans fil", "Kablosuz şarj"),
    "charge_done": ("Ricarica completata", "Charging complete", "Carga completada", "Laden abgeschlossen", "Carregamento concluído", "Charge terminée", "Şarj tamamlandı"),
    "charge_balancing": ("Bilanciamento batteria", "Battery balancing", "Equilibrado de batería", "Batterieausgleich", "Equilíbrio da bateria", "Équilibrage de la batterie", "Batarya dengeleme"),
    "charge_error": ("Errore di ricarica", "Charging error", "Error de carga", "Ladefehler", "Erro de carregamento", "Erreur de charge", "Şarj hatası"),
    "charge_connecting": ("Collegamento in corso…", "Connecting…", "Conectando…", "Verbinde…", "A ligar…", "Connexion…", "Bağlanıyor…"),
    "charge_not_recognized": ("Collegato · non riconosciuto", "Plugged in · not recognised", "Conectado · no reconocido", "Angeschlossen · nicht erkannt", "Ligado · não reconhecido", "Branché · non reconnu", "Takılı · tanınmadı"),
    "charge_not_charging": ("Collegato · non in carica", "Plugged in · not charging", "Conectado · sin cargar", "Angeschlossen · lädt nicht", "Ligado · sem carregar", "Branché · pas en charge", "Takılı · şarj olmuyor"),
    "charge_stopped": ("Ricarica interrotta", "Charging stopped", "Carga interrumpida", "Laden unterbrochen", "Carregamento interrompido", "Charge interrompue", "Şarj durduruldu"),
    "charge_scheduled": ("Ricarica programmata", "Scheduled charging", "Carga programada", "Geplantes Laden", "Carregamento agendado", "Charge programmée", "Planlı şarj"),
    "charge_multiple": ("Ricarica multipla", "Multiple charging", "Carga múltiple", "Mehrfachladen", "Carregamento múltiplo", "Charge multiple", "Çoklu şarj"),
    "v2l_active": ("V2L attivo · alimentazione esterna", "V2L on · powering external devices", "V2L activo · alimentación externa", "V2L aktiv · externe Versorgung", "V2L ativo · alimentação externa", "V2L actif · alimentation externe", "V2L açık · harici güç"),
    "v2l_stopped": ("V2L interrotto", "V2L stopped", "V2L interrumpido", "V2L unterbrochen", "V2L interrompido", "V2L interrompu", "V2L durduruldu"),
    "v2l_done": ("V2L terminato", "V2L finished", "V2L finalizado", "V2L beendet", "V2L terminado", "V2L terminé", "V2L bitti"),
    "ready": ("Pronta", "Ready", "Lista", "Bereit", "Pronta", "Prête", "Hazır"),
    "outside_temp": ("%s° esterni", "%s° outside", "%s° exterior", "%s° außen", "%s° exterior", "%s° extérieur", "Dış %s°"),
    "ac_desc": ("Aria condizionata", "Air conditioning", "Aire acondicionado", "Klimaanlage", "Ar condicionado", "Climatisation", "Klima"),
    "auto_desc": ("Clima automatico", "Automatic climate", "Climatización automática", "Klimaautomatik", "Climatização automática", "Climatisation automatique", "Otomatik klima"),
    "defrost_desc": ("Sbrinamento parabrezza", "Windscreen defrost", "Desempañado del parabrisas", "Frontscheibe enteisen", "Desembaciar para-brisas", "Désembuage du pare-brise", "Ön cam buz çözme"),
    "fog_front": ("Fendinebbia anteriori", "Front fog lights", "Antiniebla delanteros", "Nebelscheinwerfer", "Faróis de nevoeiro", "Antibrouillards avant", "Ön sis farları"),
    "fog_rear": ("Retronebbia", "Rear fog light", "Antiniebla trasero", "Nebelschlussleuchte", "Farol de nevoeiro traseiro", "Antibrouillard arrière", "Arka sis lambası"),
    "climate": ("Clima", "Climate", "Climatización", "Klima", "Climatização", "Climatisation", "Klima"),

    # --- pannello destro ---
    "tab_map": ("Mappa", "Map", "Mapa", "Karte", "Mapa", "Carte", "Harita"),
    "tab_media": ("Media", "Media", "Multimedia", "Medien", "Multimédia", "Médias", "Medya"),
    "where_to": ("Dove andiamo?", "Where to?", "¿Adónde vamos?", "Wohin geht's?", "Para onde vamos?", "Où allons-nous ?", "Nereye gidiyoruz?"),
    "search_destination": ("Cerca una destinazione", "Search for a destination", "Buscar un destino", "Ziel suchen", "Procurar um destino", "Rechercher une destination", "Hedef ara"),
    "nav_default": ("Navigatore: %s", "Navigator: %s", "Navegador: %s", "Navigation: %s", "Navegador: %s", "Navigation : %s", "Navigasyon: %s"),
    "home_place": ("Casa", "Home", "Casa", "Zuhause", "Casa", "Domicile", "Ev"),
    "work_place": ("Lavoro", "Work", "Trabajo", "Arbeit", "Trabalho", "Travail", "İş"),
    "go_there": ("Avvia percorso", "Start route", "Iniciar ruta", "Route starten", "Iniciar rota", "Démarrer", "Rotayı başlat"),
    "open_with": ("Apri con", "Open with", "Abrir con", "Öffnen mit", "Abrir com", "Ouvrir avec", "Şununla aç"),
    "open_with_hint": ("tieni premuto per renderlo predefinito", "long-press to make it the default", "mantén pulsado para predeterminarlo", "lange drücken für Standard", "mantenha premido para predefinir", "appui long pour le définir par défaut", "varsayılan yapmak için basılı tutun"),
    "nav_phone": ("Navigazione dal telefono", "Navigation from the phone", "Navegación desde el teléfono", "Navigation vom Telefon", "Navegação do telemóvel", "Navigation depuis le téléphone", "Telefondan navigasyon"),
    "next_maneuver": ("Prossima manovra", "Next manoeuvre", "Próxima maniobra", "Nächstes Manöver", "Próxima manobra", "Prochaine manœuvre", "Sonraki manevra"),
    "navigating": ("Navigazione in corso", "Navigating", "Navegación en curso", "Navigation läuft", "Navegação em curso", "Navigation en cours", "Navigasyon sürüyor"),
    "remaining": ("Mancano %s", "%s to go", "Faltan %s", "Noch %s", "Faltam %s", "Encore %s", "%s kaldı"),
    "tap_open_nav": ("Tocca per aprire il navigatore", "Tap to open the navigator", "Toca para abrir el navegador", "Tippen, um die Navigation zu öffnen", "Toque para abrir o navegador", "Touchez pour ouvrir la navigation", "Navigasyonu açmak için dokunun"),
    "arrival": ("arrivo %s", "arrival %s", "llegada %s", "Ankunft %s", "chegada %s", "arrivée %s", "varış %s"),
    "open_source_app": ("Apri l'app della sorgente", "Open the source app", "Abrir la app de la fuente", "App der Quelle öffnen", "Abrir a app da fonte", "Ouvrir l'appli de la source", "Kaynak uygulamasını aç"),
    "src_radio": ("Radio", "Radio", "Radio", "Radio", "Rádio", "Radio", "Radyo"),
    "src_online": ("Online", "Online", "Online", "Online", "Online", "En ligne", "Çevrimiçi"),
    "media_generic": ("Media", "Media", "Multimedia", "Medien", "Multimédia", "Médias", "Medya"),
    "phone_connected": ("Telefono collegato", "Phone connected", "Teléfono conectado", "Telefon verbunden", "Telemóvel ligado", "Téléphone connecté", "Telefon bağlı"),
    "projection_ready": ("%s pronto. Tocca per aprire a schermo intero.", "%s ready. Tap to open full screen.", "%s listo. Toca para abrir a pantalla completa.", "%s bereit. Tippen für Vollbild.", "%s pronto. Toque para abrir em ecrã inteiro.", "%s prêt. Touchez pour l'ouvrir en plein écran.", "%s hazır. Tam ekran açmak için dokunun."),
    "open": ("Apri", "Open", "Abrir", "Öffnen", "Abrir", "Ouvrir", "Aç"),
    "stations_title": ("Stazioni %s", "%s stations", "Emisoras %s", "%s-Sender", "Estações %s", "Stations %s", "%s istasyonları"),
    "stations_empty": ("Nessuna stazione trovata. Avvia una ricerca dall'app Radio.", "No stations found. Start a scan from the Radio app.", "No se han encontrado emisoras. Inicia una búsqueda desde la app Radio.", "Keine Sender gefunden. Starte einen Suchlauf in der Radio-App.", "Nenhuma estação encontrada. Inicie uma pesquisa na app Rádio.", "Aucune station trouvée. Lancez une recherche depuis l'appli Radio.", "İstasyon bulunamadı. Radyo uygulamasından arama başlatın."),
    "open_radio_app": ("Apri Radio", "Open Radio", "Abrir Radio", "Radio öffnen", "Abrir Rádio", "Ouvrir Radio", "Radyoyu aç"),
    "previous": ("Precedente", "Previous", "Anterior", "Zurück", "Anterior", "Précédent", "Önceki"),
    "next": ("Successivo", "Next", "Siguiente", "Weiter", "Seguinte", "Suivant", "Sonraki"),
    "play_pause": ("Play o pausa", "Play or pause", "Reproducir o pausa", "Wiedergabe oder Pause", "Reproduzir ou pausa", "Lecture ou pause", "Oynat veya duraklat"),
    "temp_down": ("Diminuisci temperatura", "Lower temperature", "Bajar temperatura", "Temperatur senken", "Baixar temperatura", "Baisser la température", "Sıcaklığı düşür"),
    "temp_up": ("Aumenta temperatura", "Raise temperature", "Subir temperatura", "Temperatur erhöhen", "Subir temperatura", "Augmenter la température", "Sıcaklığı artır"),
    "fan_down": ("Ventola meno", "Fan down", "Ventilador menos", "Gebläse weniger", "Ventilador menos", "Ventilation moins", "Fan azalt"),
    "fan_up": ("Ventola più", "Fan up", "Ventilador más", "Gebläse mehr", "Ventilador mais", "Ventilation plus", "Fan artır"),

    # --- sorgenti media (servizio) ---
    "no_track": ("Nessun brano", "No track", "Ninguna canción", "Kein Titel", "Nenhuma faixa", "Aucun titre", "Parça yok"),
    "plug_usb": ("Collega una chiavetta USB", "Plug in a USB stick", "Conecta una memoria USB", "USB-Stick anschließen", "Ligue uma pen USB", "Branchez une clé USB", "USB bellek takın"),
    "bt_music": ("Musica Bluetooth", "Bluetooth music", "Música Bluetooth", "Bluetooth-Musik", "Música Bluetooth", "Musique Bluetooth", "Bluetooth müzik"),
    "bt_start": ("Avvia la riproduzione dal telefono", "Start playback on your phone", "Inicia la reproducción en el teléfono", "Wiedergabe am Telefon starten", "Inicie a reprodução no telemóvel", "Lancez la lecture sur le téléphone", "Telefondan çalmayı başlatın"),
    "online_music": ("Musica online", "Online music", "Música online", "Online-Musik", "Música online", "Musique en ligne", "Çevrimiçi müzik"),
    "from_phone": ("dal telefono", "from the phone", "desde el teléfono", "vom Telefon", "do telemóvel", "depuis le téléphone", "telefondan"),

    # --- app ---
    "apps_title": ("App", "Apps", "Apps", "Apps", "Apps", "Applis", "Uygulamalar"),
    "edit": ("Modifica", "Edit", "Editar", "Bearbeiten", "Editar", "Modifier", "Düzenle"),
    "done": ("Fine", "Done", "Listo", "Fertig", "Concluído", "Terminé", "Bitti"),
    "edit_desc": ("Scegli quali app installate mostrare", "Choose which installed apps to show", "Elige qué apps instaladas mostrar", "Wähle, welche installierten Apps angezeigt werden", "Escolha que apps instaladas mostrar", "Choisissez les applis installées à afficher", "Gösterilecek yüklü uygulamaları seçin"),
    "section_car": ("AUTO", "CAR", "COCHE", "FAHRZEUG", "CARRO", "VÉHICULE", "ARAÇ"),
    "section_mine": ("INSTALLATE DA TE", "INSTALLED BY YOU", "INSTALADAS POR TI", "VON DIR INSTALLIERT", "INSTALADAS POR SI", "INSTALLÉES PAR VOUS", "SİZİN YÜKLEDİKLERİNİZ"),
    "edit_note": ("tocca per mostrare o nascondere", "tap to show or hide", "toca para mostrar u ocultar", "tippen zum Ein- oder Ausblenden", "toque para mostrar ou ocultar", "touchez pour afficher ou masquer", "göstermek veya gizlemek için dokunun"),
    "app_vehicle": ("Veicolo", "Vehicle", "Vehículo", "Fahrzeug", "Veículo", "Véhicule", "Araç"),
    "app_energy": ("Energia", "Energy", "Energía", "Energie", "Energia", "Énergie", "Enerji"),
    "app_consumption": ("Consumi", "Consumption", "Consumo", "Verbrauch", "Consumo", "Consommation", "Tüketim"),
    "app_phone": ("Telefono", "Phone", "Teléfono", "Telefon", "Telefone", "Téléphone", "Telefon"),
    "app_music": ("Musica", "Music", "Música", "Musik", "Música", "Musique", "Müzik"),
    "app_usb_video": ("Video USB", "USB video", "Vídeo USB", "USB-Video", "Vídeo USB", "Vidéo USB", "USB video"),
    "app_camera": ("Camera 360°", "360° camera", "Cámara 360°", "360°-Kamera", "Câmara 360°", "Caméra 360°", "360° kamera"),
    "app_settings": ("Impostazioni", "Settings", "Ajustes", "Einstellungen", "Definições", "Réglages", "Ayarlar"),
    "app_navigator": ("Navigatore", "Navigation", "Navegador", "Navigation", "Navegação", "Navigation", "Navigasyon"),
    "app_weather": ("Meteo", "Weather", "Tiempo", "Wetter", "Meteorologia", "Météo", "Hava durumu"),
    "app_manual": ("Manuale", "Manual", "Manual", "Handbuch", "Manual", "Manuel", "Kılavuz"),
    "app_account": ("Account", "Account", "Cuenta", "Konto", "Conta", "Compte", "Hesap"),
    "app_sos": ("Chiamata SOS", "SOS call", "Llamada SOS", "SOS-Notruf", "Chamada SOS", "Appel SOS", "SOS çağrısı"),

    # --- impostazioni ---
    "settings_title": ("Impostazioni launcher", "Launcher settings", "Ajustes del launcher", "Launcher-Einstellungen", "Definições do launcher", "Réglages du launcher", "Başlatıcı ayarları"),
    "settings_desc": ("Impostazioni del launcher", "Launcher settings", "Ajustes del launcher", "Launcher-Einstellungen", "Definições do launcher", "Réglages du launcher", "Başlatıcı ayarları"),
    "car_color": ("COLORE AUTO", "CAR COLOUR", "COLOR DEL COCHE", "FAHRZEUGFARBE", "COR DO CARRO", "COULEUR DU VÉHICULE", "ARAÇ RENGİ"),
    "black_roof": ("Tetto nero", "Black roof", "Techo negro", "Schwarzes Dach", "Tejadilho preto", "Toit noir", "Siyah tavan"),
    "theme": ("TEMA", "THEME", "TEMA", "DESIGN", "TEMA", "THÈME", "TEMA"),
    "language": ("LINGUA", "LANGUAGE", "IDIOMA", "SPRACHE", "IDIOMA", "LANGUE", "DİL"),
    "lang_auto": ("Automatica (%s)", "Automatic (%s)", "Automático (%s)", "Automatisch (%s)", "Automático (%s)", "Automatique (%s)", "Otomatik (%s)"),
    "lang_auto_hint": ("Automatica segue la lingua del sistema dell'auto.", "Automatic follows the car's system language.", "Automático sigue el idioma del sistema del coche.", "Automatisch folgt der Systemsprache des Fahrzeugs.", "Automático segue o idioma do sistema do carro.", "Automatique suit la langue du système du véhicule.", "Otomatik, aracın sistem dilini izler."),
    "default_nav": ("NAVIGATORE PREDEFINITO", "DEFAULT NAVIGATOR", "NAVEGADOR PREDETERMINADO", "STANDARD-NAVIGATION", "NAVEGADOR PREDEFINIDO", "NAVIGATION PAR DÉFAUT", "VARSAYILAN NAVİGASYON"),
    "nav_auto_hint": ("Automatico: quello del telefono se è collegato CarPlay o Android Auto, altrimenti MG.", "Automatic: the phone's when CarPlay or Android Auto is connected, otherwise MG.", "Automático: el del teléfono si CarPlay o Android Auto está conectado; si no, MG.", "Automatisch: die des Telefons, wenn CarPlay oder Android Auto verbunden ist, sonst MG.", "Automático: o do telemóvel se o CarPlay ou o Android Auto estiver ligado; caso contrário, MG.", "Automatique : celle du téléphone si CarPlay ou Android Auto est connecté, sinon MG.", "Otomatik: CarPlay veya Android Auto bağlıysa telefonunki, değilse MG."),
    "auto": ("Automatico", "Automatic", "Automático", "Automatisch", "Automático", "Automatique", "Otomatik"),
    "night": ("Notte", "Night", "Noche", "Nacht", "Noite", "Nuit", "Gece"),
    "day": ("Giorno", "Day", "Día", "Tag", "Dia", "Jour", "Gündüz"),
    "open_original_home": ("Apri la home originale MG", "Open the original MG home", "Abrir el inicio original de MG", "Original-MG-Startseite öffnen", "Abrir o início original da MG", "Ouvrir l'accueil MG d'origine", "Orijinal MG ana ekranını aç"),
    "diag_title": ("DIAGNOSTICA SERVIZI AUTO", "CAR SERVICES DIAGNOSTICS", "DIAGNÓSTICO DE SERVICIOS", "DIAGNOSE FAHRZEUGDIENSTE", "DIAGNÓSTICO DOS SERVIÇOS", "DIAGNOSTIC DES SERVICES", "ARAÇ HİZMETLERİ TANILAMA"),
    "save_log": ("Salva log", "Save log", "Guardar registro", "Log speichern", "Guardar registo", "Enregistrer le journal", "Günlüğü kaydet"),
    "save_log_hint": ("Salva log: con una chiavetta USB inserita il file finisce anche lì.", "Save log: with a USB stick plugged in, the file is saved there too.", "Guardar registro: con una memoria USB conectada, el archivo también se guarda allí.", "Log speichern: Ist ein USB-Stick eingesteckt, wird die Datei auch dort gespeichert.", "Guardar registo: com uma pen USB ligada, o ficheiro também fica lá.", "Enregistrer le journal : avec une clé USB branchée, le fichier y est aussi enregistré.", "Günlüğü kaydet: USB bellek takılıysa dosya oraya da kaydedilir."),
    "close": ("Chiudi", "Close", "Cerrar", "Schließen", "Fechar", "Fermer", "Kapat"),
    "updates": ("AGGIORNAMENTI", "UPDATES", "ACTUALIZACIONES", "UPDATES", "ATUALIZAÇÕES", "MISES À JOUR", "GÜNCELLEMELER"),
    "check_updates": ("Controlla aggiornamenti", "Check for updates", "Buscar actualizaciones", "Nach Updates suchen", "Procurar atualizações", "Rechercher des mises à jour", "Güncellemeleri denetle"),
    "download_install": ("Scarica e installa", "Download and install", "Descargar e instalar", "Herunterladen und installieren", "Transferir e instalar", "Télécharger et installer", "İndir ve yükle"),
    "download_install_v": ("Scarica e installa %s", "Download and install %s", "Descargar e instalar %s", "%s herunterladen und installieren", "Transferir e instalar %s", "Télécharger et installer %s", "%s indir ve yükle"),
    "busy": ("Operazione in corso…", "Working…", "En curso…", "Wird ausgeführt…", "Em curso…", "En cours…", "İşlem sürüyor…"),
    "press_check": ("Premi Controlla per cercare una nuova versione", "Tap Check to look for a new version", "Pulsa Buscar para comprobar si hay una versión nueva", "Tippe auf „Suchen“, um nach einer neuen Version zu suchen", "Toque em Procurar para verificar se há uma nova versão", "Touchez Rechercher pour trouver une nouvelle version", "Yeni sürüm aramak için Denetle'ye dokunun"),
    "saving_log": ("Salvataggio del log…", "Saving the log…", "Guardando el registro…", "Log wird gespeichert…", "A guardar o registo…", "Enregistrement du journal…", "Günlük kaydediliyor…"),
    "log_failed": ("Non sono riuscito a salvare il log in nessuna cartella.", "Could not save the log to any folder.", "No se pudo guardar el registro en ninguna carpeta.", "Das Log konnte in keinem Ordner gespeichert werden.", "Não foi possível guardar o registo em nenhuma pasta.", "Impossible d'enregistrer le journal dans un dossier.", "Günlük hiçbir klasöre kaydedilemedi."),
    "log_saved_usb": ("Log salvato anche sulla chiavetta USB:", "Log saved, also to the USB stick:", "Registro guardado, también en la memoria USB:", "Log gespeichert, auch auf dem USB-Stick:", "Registo guardado, também na pen USB:", "Journal enregistré, aussi sur la clé USB :", "Günlük USB belleğe de kaydedildi:"),
    "log_saved": ("Log salvato:", "Log saved:", "Registro guardado:", "Log gespeichert:", "Registo guardado:", "Journal enregistré :", "Günlük kaydedildi:"),
    "log_usb_hint": ("Per averlo su chiavetta: inseriscila e premi di nuovo Salva log.", "To get it on a USB stick, plug one in and tap Save log again.", "Para tenerlo en una memoria USB, conéctala y pulsa de nuevo Guardar registro.", "Für den USB-Stick: einstecken und erneut auf „Log speichern“ tippen.", "Para o ter numa pen USB, ligue-a e toque novamente em Guardar registo.", "Pour l'avoir sur une clé USB, branchez-la et touchez à nouveau Enregistrer le journal.", "USB belleğe almak için belleği takıp Günlüğü kaydet'e yeniden dokunun."),
    "waiting_services": ("In attesa dei servizi dell'auto…", "Waiting for the car services…", "Esperando a los servicios del coche…", "Warte auf die Fahrzeugdienste…", "À espera dos serviços do carro…", "En attente des services du véhicule…", "Araç hizmetleri bekleniyor…"),

    # --- aggiornamenti ---
    "upd_checking": ("Controllo su GitHub…", "Checking GitHub…", "Comprobando en GitHub…", "Prüfe GitHub…", "A verificar no GitHub…", "Vérification sur GitHub…", "GitHub denetleniyor…"),
    "upd_no_apk": ("L'ultima release (%s) non contiene un APK", "The latest release (%s) has no APK", "La última versión (%s) no contiene un APK", "Die neueste Version (%s) enthält keine APK", "A versão mais recente (%s) não contém um APK", "La dernière version (%s) ne contient pas d'APK", "Son sürüm (%s) APK içermiyor"),
    "upd_available": ("Nuova versione disponibile: %s", "New version available: %s", "Nueva versión disponible: %s", "Neue Version verfügbar: %s", "Nova versão disponível: %s", "Nouvelle version disponible : %s", "Yeni sürüm mevcut: %s"),
    "upd_latest": ("Sei aggiornato (%1$s, ultima release %2$s)", "You're up to date (%1$s, latest release %2$s)", "Estás al día (%1$s, última versión %2$s)", "Du bist auf dem neuesten Stand (%1$s, neueste Version %2$s)", "Está atualizado (%1$s, versão mais recente %2$s)", "Vous êtes à jour (%1$s, dernière version %2$s)", "Güncelsiniz (%1$s, son sürüm %2$s)"),
    "upd_no_connection": ("Nessuna connessione a GitHub (%s)", "No connection to GitHub (%s)", "Sin conexión con GitHub (%s)", "Keine Verbindung zu GitHub (%s)", "Sem ligação ao GitHub (%s)", "Pas de connexion à GitHub (%s)", "GitHub bağlantısı yok (%s)"),
    "upd_downloading": ("Download di %s…", "Downloading %s…", "Descargando %s…", "Lade %s herunter…", "A transferir %s…", "Téléchargement de %s…", "%s indiriliyor…"),
    "upd_download_pct": ("Download %d%%…", "Downloading %d%%…", "Descargando %d%%…", "Download %d%%…", "A transferir %d%%…", "Téléchargement %d%%…", "İndiriliyor %%%d…"),
    "upd_download_failed": ("Download non riuscito: %s", "Download failed: %s", "Error en la descarga: %s", "Download fehlgeschlagen: %s", "Falha na transferência: %s", "Échec du téléchargement : %s", "İndirme başarısız: %s"),
    "upd_invalid_apk": ("Il file scaricato non è un APK valido (%d byte)", "The downloaded file is not a valid APK (%d bytes)", "El archivo descargado no es un APK válido (%d bytes)", "Die heruntergeladene Datei ist keine gültige APK (%d Byte)", "O ficheiro transferido não é um APK válido (%d bytes)", "Le fichier téléchargé n'est pas un APK valide (%d octets)", "İndirilen dosya geçerli bir APK değil (%d bayt)"),
    "upd_wrong_package": ("Il file scaricato non è un aggiornamento del launcher", "The downloaded file is not a launcher update", "El archivo descargado no es una actualización del launcher", "Die heruntergeladene Datei ist kein Launcher-Update", "O ficheiro transferido não é uma atualização do launcher", "Le fichier téléchargé n'est pas une mise à jour du launcher", "İndirilen dosya bir başlatıcı güncellemesi değil"),
    "upd_installing": ("Installazione di %s… il launcher si riavvierà da solo", "Installing %s… the launcher will restart by itself", "Instalando %s… el launcher se reiniciará solo", "Installiere %s… der Launcher startet von selbst neu", "A instalar %s… o launcher reinicia sozinho", "Installation de %s… le launcher redémarrera tout seul", "%s yükleniyor… başlatıcı kendiliğinden yeniden başlayacak"),
    "upd_install_failed": ("Installazione non riuscita: %s", "Installation failed: %s", "Error en la instalación: %s", "Installation fehlgeschlagen: %s", "Falha na instalação: %s", "Échec de l'installation : %s", "Yükleme başarısız: %s"),
    "upd_new_version": ("nuova versione", "new version", "nueva versión", "neue Version", "nova versão", "nouvelle version", "yeni sürüm"),
    "upd_not_found": ("Nessuna versione trovata su %s", "No version found on %s", "No se ha encontrado ninguna versión en %s", "Keine Version auf %s gefunden", "Nenhuma versão encontrada em %s", "Aucune version trouvée sur %s", "%s üzerinde sürüm bulunamadı"),
    "upd_token_invalid": ("Token GitHub non valido o scaduto", "GitHub token invalid or expired", "Token de GitHub no válido o caducado", "GitHub-Token ungültig oder abgelaufen", "Token do GitHub inválido ou expirado", "Jeton GitHub invalide ou expiré", "GitHub belirteci geçersiz veya süresi dolmuş"),
    "upd_refused": ("GitHub ha rifiutato la richiesta (limite richieste o permessi)", "GitHub refused the request (rate limit or permissions)", "GitHub rechazó la solicitud (límite de solicitudes o permisos)", "GitHub hat die Anfrage abgelehnt (Limit oder Berechtigungen)", "O GitHub recusou o pedido (limite de pedidos ou permissões)", "GitHub a refusé la requête (limite ou autorisations)", "GitHub isteği reddetti (istek sınırı veya izinler)"),
    "upd_unexpected": ("Risposta GitHub inattesa: %d", "Unexpected GitHub response: %d", "Respuesta inesperada de GitHub: %d", "Unerwartete GitHub-Antwort: %d", "Resposta inesperada do GitHub: %d", "Réponse GitHub inattendue : %d", "Beklenmeyen GitHub yanıtı: %d"),
    "upd_confirm": ("Conferma l'installazione sullo schermo", "Confirm the installation on screen", "Confirma la instalación en la pantalla", "Installation auf dem Bildschirm bestätigen", "Confirme a instalação no ecrã", "Confirmez l'installation à l'écran", "Yüklemeyi ekranda onaylayın"),
    "upd_installed": ("Aggiornamento installato", "Update installed", "Actualización instalada", "Update installiert", "Atualização instalada", "Mise à jour installée", "Güncelleme yüklendi"),
    "upd_error_code": ("errore %d", "error %d", "error %d", "Fehler %d", "erro %d", "erreur %d", "hata %d"),
}


def esc(t):
    t = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    t = t.replace("\\", "\\\\").replace("'", "\\'").replace('"', '\\"')
    if t.startswith("?") or t.startswith("@"):
        t = "\\" + t
    return t


def main():
    root = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "res")
    bad = [k for k, v in S.items() if len(v) != len(LANGS)]
    if bad:
        sys.exit("voci senza 7 traduzioni: %s" % bad)
    for i, lang in enumerate(LANGS):
        folder = "values" if lang == "en" else "values-" + lang
        os.makedirs(os.path.join(root, folder), exist_ok=True)
        lines = ['<?xml version="1.0" encoding="utf-8"?>',
                 '<!-- generato da tools/i18n.py: non modificare a mano -->', '<resources>']
        for k, v in S.items():
            t = v[i]
            fmt = re.search(r"%(\d+\$)?[sd]", t)
            attr = "" if fmt else ' formatted="false"'
            lines.append('    <string name="%s"%s>%s</string>' % (k, attr, esc(t)))
        lines.append("</resources>")
        with open(os.path.join(root, folder, "strings.xml"), "w", encoding="utf-8") as f:
            f.write("\n".join(lines) + "\n")
    print("%d testi × %d lingue" % (len(S), len(LANGS)))


if __name__ == "__main__":
    main()
