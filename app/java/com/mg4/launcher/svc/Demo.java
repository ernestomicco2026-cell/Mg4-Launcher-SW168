package com.mg4.launcher.svc;

import java.util.ArrayList;
import java.util.List;

/**
 * Dati simulati per la versione DEMO: la home si comporta come in auto (pulsanti, livelli, radio) senza
 * collegarsi ai servizi MG. Nella versione normale questa classe non viene usata.
 */
public final class Demo {
    private Demo() {}

    // ---- veicolo ------------------------------------------------------------------------------
    private static float soc = 76.6f;
    private static int temp = 22, fan = 3, ac = 1, auto = 0, seatL = 0, seatR = 0, wheel = 0, rearFog = 0, frontFog = 0;
    private static int hvac = 1;

    public static synchronized VehicleClient.State vehicle() {
        VehicleClient.State s = new VehicleClient.State();
        s.connected = true;
        soc = Math.max(5f, soc - 0.01f);                  // scende piano, per vedere i decimali muoversi
        s.soc = soc;
        s.rangeKm = Math.round(soc * 4.45f);
        s.chargingStatus = 0;
        s.chargeLimit = 80;
        s.consumption = 16.8f;
        s.dischargeStatus = 0;
        s.acTemp = temp;
        s.hvacPower = hvac;
        s.fanLevel = fan;
        s.fanMin = 1;
        s.fanMax = 7;
        s.outsideTemp = 12f;
        s.insideTemp = 21f;
        s.acOn = ac;
        s.autoOn = auto;
        s.defrostOn = 0;
        s.seatLeft = seatL;
        s.seatRight = seatR;
        s.wheelHeat = wheel;
        s.rearFog = rearFog;
        s.frontFog = frontFog;
        s.hasFrontFog = true;
        s.tireBar = new float[]{2.5f, 2.5f, 2.4f, 1.9f};
        return s;
    }

    public static synchronized void setTemp(int t) { temp = t; }
    public static synchronized void setFan(int f) { fan = f; }
    public static synchronized void toggleAc() { ac = 1 - ac; }
    public static synchronized void toggleAuto() { auto = 1 - auto; }
    public static synchronized void seatLeft() { seatL = (seatL + 1) % 4; }
    public static synchronized void seatRight() { seatR = (seatR + 1) % 4; }
    public static synchronized void wheel() { wheel = 1 - wheel; }
    public static synchronized void rearFog() { rearFog = 1 - rearFog; }
    public static synchronized void frontFog() { frontFog = 1 - frontFog; }
    public static synchronized void hvacPower(boolean on) { hvac = on ? 1 : 0; }

    // ---- media --------------------------------------------------------------------------------
    private static final String[][] FM = {
            {"ZETA", "87900"}, {"Radio1", "88300"}, {"R.MARIA", "88800"}, {"VIRGIN", "89100"},
            {"RTL 102.5", "90100"}, {"DEEJAY", "91800"}, {"RDS", "92400"}, {"RADIO 105", "94600"}};
    private static final String[] DAB = {"RAI Radio 1", "RAI Radio 2", "Radio Deejay", "RTL 102.5", "Virgin Radio"};
    private static int source = MediaClient.SRC_RADIO, band = MediaClient.BAND_FM, station;
    private static boolean playing = true;

    private static MediaClient.Station st(int b, int i) {
        MediaClient.Station s = new MediaClient.Station();
        s.band = b;
        if (b == MediaClient.BAND_DAB) {
            s.name = DAB[i];
            s.sid = 0x5000 + i;
        } else {
            s.name = FM[i][0];
            s.khz = Integer.parseInt(FM[i][1]);
        }
        s.key = StationLogos.key(b, s.sid, s.khz, s.name);
        return s;
    }

    public static synchronized MediaClient.State media() {
        MediaClient.State s = new MediaClient.State();
        s.sources.add(MediaClient.SRC_RADIO);
        s.sources.add(MediaClient.SRC_USB);
        s.sources.add(MediaClient.SRC_BT);
        s.sources.add(MediaClient.SRC_ONLINE);
        s.source = source;
        s.playing = playing;
        if (source == MediaClient.SRC_RADIO) {
            int n = band == MediaClient.BAND_DAB ? DAB.length : FM.length;
            List<MediaClient.Station> list = new ArrayList<>();
            for (int i = 0; i < n; i++) list.add(st(band, i));
            MediaClient.Station cur = list.get(station % n);
            s.radioBand = band;
            s.stations = list;
            s.stationKey = cur.key;
            s.title = cur.name;
            if (band == MediaClient.BAND_DAB) {
                s.radioFreq = "DAB";
                s.radioUnit = "RAI";
                s.subtitle = "DAB+";
            } else {
                s.radioFreq = String.format(com.mg4.launcher.L.locale(), "%.1f", cur.khz / 1000f);
                s.radioUnit = "MHz";
                s.subtitle = "Demo";
            }
            List<MediaClient.Station> fav = new ArrayList<>();
            fav.add(st(MediaClient.BAND_FM, 0));
            fav.add(st(MediaClient.BAND_FM, 3));
            fav.add(st(MediaClient.BAND_DAB, 2));
            s.favorites = fav;
        } else if (source == MediaClient.SRC_BT) {
            s.title = "Bohemian Rhapsody";
            s.subtitle = "Queen · A Night at the Opera";
            s.durationMs = 354_000;
            s.positionMs = 121_000;
        } else if (source == MediaClient.SRC_USB) {
            s.title = "Nessun dorma";
            s.subtitle = "Luciano Pavarotti";
            s.durationMs = 183_000;
            s.positionMs = 64_000;
        } else {
            s.title = "Musica online";
            s.subtitle = "Spotify";
        }
        return s;
    }

    public static synchronized void selectSource(int src) { source = src; playing = true; }
    public static synchronized void playPause() { playing = !playing; }

    public static synchronized void skip(boolean forward) {
        if (source != MediaClient.SRC_RADIO) return;
        int n = band == MediaClient.BAND_DAB ? DAB.length : FM.length;
        station = (station + (forward ? 1 : n - 1)) % n;
    }

    public static synchronized void switchBand(int b) {
        source = MediaClient.SRC_RADIO;
        band = b;
        station = 0;
    }

    public static synchronized void tune(MediaClient.Station target) {
        source = MediaClient.SRC_RADIO;
        band = target.band;
        int n = band == MediaClient.BAND_DAB ? DAB.length : FM.length;
        for (int i = 0; i < n; i++) if (st(band, i).key.equals(target.key)) station = i;
        playing = true;
    }

    // ---- connessioni --------------------------------------------------------------------------
    public static ConnClient.State conn() {
        ConnClient.State s = new ConnClient.State();
        s.btOn = true;
        s.btConnected = true;
        s.wifiConnected = true;
        s.wifiLevel = 2;
        s.mobileType = 4;
        s.mobileLevel = 3;
        return s;
    }
}
