package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.Map;

/**
 * Servizio veicolo SAIC (com.saicmotor.service.vehicle): un "hub" che restituisce i binder di
 * ricarica/energia e climatizzatore. Codici di transazione presi da vehiclesettingservice_eh32_eu_P.
 */
public final class VehicleClient {
    private static final String PKG = "com.saicmotor.service.vehicle";
    private static final String ACTION = "com.saicmotor.service.vehicle.VehicleService";

    private static final String HUB = "com.saicmotor.sdk.vehiclesettings.IHubService";
    private static final int HUB_GET_SERVICE = 1;

    private static final String CHG = "com.saicmotor.sdk.vehiclesettings.IVehicleChargingService";
    private static final int CHG_SOC = 3;              // float getCurrentElectricQuantity
    private static final int CHG_RANGE = 4;            // int   getCurrentEnduranceMileage
    private static final int CHG_PREDICT_TIME = 5;     // int   getPredictChargingTime
    private static final int CHG_CURRENT = 7;          // float getActualChargingCurrent
    private static final int CHG_STATUS = 9;           // int   getChargingStatus
    private static final int CHG_LIMIT_SOC = 13;       // int   getChargingCloseSoc
    private static final int CHG_SOC_VALID = 29;       // bool  getCurrentElectricQuantityV
    private static final int CHG_RANGE_VALID = 30;     // bool  getCurrentEnduranceMileageV
    private static final int CHG_DISCHARGE_STATUS = 48;// int   getDischrgControlStatus
    private static final int CHG_BATTERY_VOLT = 64;    // float getPowerBatteryVol
    private static final int CHG_CONSUMPTION = 66;     // float getElecCsumpPerKm

    private static final String AC = "com.saicmotor.sdk.vehiclesettings.IAirConditionService";
    private static final int AC_OPEN_POWER = 5;
    private static final int AC_CLOSE_POWER = 6;
    private static final int AC_SET_AIR_VOLUME = 15;
    private static final int AC_SET_DRV_TEMP = 41;
    private static final int AC_GET_POWER = 53;
    private static final int AC_GET_AIR_VOLUME = 59;
    private static final int AC_GET_MAX_AIR_VOLUME = 60;
    private static final int AC_GET_MIN_AIR_VOLUME = 61;
    private static final int AC_GET_DRV_TEMP = 81;

    /** Livelli ventola come nell'originale (AcCardView): 1..11, 0 = spenta, 15 = automatico. */
    public static final int FAN_OFF = 0, FAN_AUTO = 15;
    /** Per quanto tempo il valore impostato dall'utente prevale sulle letture (che arrivano in ritardo). */
    private static final long HOLD_MS = 4000;

    public static final int TEMP_MIN = 17;   // "LO"
    public static final int TEMP_MAX = 33;   // "HI"

    /** Istantanea dello stato del veicolo letta dai servizi. */
    public static final class State {
        public boolean connected;
        public float soc = -1;            // %
        public int rangeKm = -1;
        public int chargingStatus = -1;   // codici SAIC, vedi ChargeText
        public int predictMinutes = -1;
        public float chargeKw = 0;
        public int chargeLimit = -1;      // %
        public float consumption = -1;    // kWh/100km (da verificare sull'auto)
        public int dischargeStatus = -1;
        public int acTemp = -1;           // °C, 17 = LO, 33 = HI
        public int hvacPower = -1;
        public int fanLevel = -1;         // 0 spenta, 1..max, 15 auto
        public int fanMin = 1, fanMax = 11;
    }

    public interface Listener {
        void onVehicleState(State s);
    }

    private final ServiceLink hub;
    private final Map<String, IBinder> subs = new HashMap<>();
    private final HandlerThread thread = new HandlerThread("mg4-vehicle");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile State last = new State();
    private volatile int pendingTemp = -1, pendingFan = -1;
    private volatile int heldTemp = -1, heldFan = -1;
    private volatile long tempHoldUntil, fanHoldUntil;
    private int fanMin = 1, fanMax = 11;
    private boolean fanRangeRead;
    private boolean running;

    public VehicleClient(Context ctx) {
        hub = new ServiceLink(ctx, "veicolo", ServiceLink.byAction(PKG, ACTION));
        hub.setOnConnected(() -> {
            synchronized (subs) {
                subs.clear();
            }
            if (bg != null) bg.post(this::poll);
        });
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public State last() {
        return last;
    }

    public void start() {
        if (running) return;
        running = true;
        if (bg == null) {
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        hub.start();
        bg.post(loop);
    }

    public void stop() {
        pause();
        hub.stop();
    }

    /** Sospende le letture (launcher non visibile) senza scollegarsi dai servizi. */
    public void pause() {
        running = false;
        if (bg != null) bg.removeCallbacks(loop);
    }

    public void resume() {
        if (bg == null || running) return;
        running = true;
        bg.post(loop);
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            poll();
            bg.postDelayed(this, 2000);
        }
    };

    private IBinder sub(String name) throws RemoteException {
        synchronized (subs) {
            IBinder b = subs.get(name);
            if (b != null && b.isBinderAlive()) return b;
        }
        IBinder h = hub.binder();
        if (h == null) return null;
        IBinder b = Rpc.callBinder(h, HUB, HUB_GET_SERVICE, p -> p.writeString(name));
        synchronized (subs) {
            subs.put(name, b);
        }
        return b;
    }

    private void poll() {
        State s = new State();
        s.connected = hub.isConnected();
        if (s.connected) {
            try {
                IBinder c = sub("vehiclecharging");
                if (c != null) {
                    s.soc = Rpc.callFloat(c, CHG, CHG_SOC);
                    s.rangeKm = Rpc.callInt(c, CHG, CHG_RANGE);
                    s.chargingStatus = Rpc.callInt(c, CHG, CHG_STATUS);
                    s.predictMinutes = Rpc.callInt(c, CHG, CHG_PREDICT_TIME);
                    s.chargeLimit = Rpc.callInt(c, CHG, CHG_LIMIT_SOC);
                    s.dischargeStatus = Rpc.callInt(c, CHG, CHG_DISCHARGE_STATUS);
                    float amps = Rpc.callFloat(c, CHG, CHG_CURRENT);
                    float volts = Rpc.callFloat(c, CHG, CHG_BATTERY_VOLT);
                    s.chargeKw = Math.abs(amps * volts) / 1000f;
                    s.consumption = Rpc.callFloat(c, CHG, CHG_CONSUMPTION);
                    Diag.put("veicolo.soc", s.soc + " (valido " + Rpc.callBool(c, CHG, CHG_SOC_VALID) + ")");
                    Diag.put("veicolo.autonomia", s.rangeKm + " (valido " + Rpc.callBool(c, CHG, CHG_RANGE_VALID) + ")");
                    Diag.put("veicolo.statoRicarica", s.chargingStatus);
                    Diag.put("veicolo.minutiRicarica", s.predictMinutes);
                    Diag.put("veicolo.correnteA/tensioneV", amps + " / " + volts);
                    Diag.put("veicolo.limiteRicarica", s.chargeLimit);
                    Diag.put("veicolo.consumo", s.consumption);
                    Diag.put("veicolo.statoScarica(V2L)", s.dischargeStatus);
                }
            } catch (Exception e) {
                Diag.error("ricarica", e.toString());
                synchronized (subs) {
                    subs.remove("vehiclecharging");
                }
            }
            try {
                IBinder a = sub("aircondition");
                if (a != null) {
                    int pending = pendingTemp;
                    if (pending > 0) {
                        pendingTemp = -1;
                        Rpc.call(a, AC, AC_SET_DRV_TEMP, p -> p.writeInt(pending));
                    }
                    int fan = pendingFan;
                    if (fan >= 0) {
                        pendingFan = -1;
                        Rpc.call(a, AC, AC_SET_AIR_VOLUME, p -> p.writeInt(fan));
                    }
                    if (!fanRangeRead) {
                        int mn = Rpc.callInt(a, AC, AC_GET_MIN_AIR_VOLUME);
                        int mx = Rpc.callInt(a, AC, AC_GET_MAX_AIR_VOLUME);
                        if (mn >= 1 && mx > mn && mx < FAN_AUTO) {
                            fanMin = mn;
                            fanMax = mx;
                        }
                        fanRangeRead = true;
                        Diag.put("clima.ventolaMinMax", mn + " / " + mx);
                    }
                    long now = android.os.SystemClock.uptimeMillis();
                    int readTemp = Rpc.callInt(a, AC, AC_GET_DRV_TEMP);
                    // subito dopo un comando l'auto restituisce ancora il valore vecchio: tieni quello richiesto
                    s.acTemp = (now < tempHoldUntil && heldTemp > 0) ? heldTemp : readTemp;
                    int readFan = Rpc.callInt(a, AC, AC_GET_AIR_VOLUME);
                    s.fanLevel = (now < fanHoldUntil && heldFan >= 0) ? heldFan : readFan;
                    s.fanMin = fanMin;
                    s.fanMax = fanMax;
                    s.hvacPower = Rpc.callInt(a, AC, AC_GET_POWER);
                    Diag.put("clima.tempGuidatore", readTemp + (s.acTemp != readTemp ? " (richiesta " + s.acTemp + ")" : ""));
                    Diag.put("clima.ventola", readFan + (s.fanLevel != readFan ? " (richiesta " + s.fanLevel + ")" : ""));
                    Diag.put("clima.accensione", s.hvacPower);
                }
            } catch (Exception e) {
                Diag.error("clima", e.toString());
                synchronized (subs) {
                    subs.remove("aircondition");
                }
            }
        }
        last = s;
        main.post(() -> {
            if (listener != null) listener.onVehicleState(s);
        });
    }

    /** Imposta la temperatura lato guida (17..33). Il valore richiesto resta visibile per qualche secondo. */
    public void setDriverTemp(int celsius) {
        int t = Math.max(TEMP_MIN, Math.min(TEMP_MAX, celsius));
        heldTemp = t;
        tempHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        pendingTemp = t;
        sendSoon();
    }

    /** Livello ventola: 0 = spenta, fanMin..fanMax, 15 = automatico. */
    public void setFanLevel(int level) {
        int l = level == FAN_AUTO || level == FAN_OFF ? level : Math.max(fanMin, Math.min(fanMax, level));
        heldFan = l;
        fanHoldUntil = android.os.SystemClock.uptimeMillis() + HOLD_MS;
        pendingFan = l;
        sendSoon();
    }

    /** Invia il comando subito, senza riscrivere la UI con una lettura immediata. */
    private void sendSoon() {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub("aircondition");
                if (a == null) return;
                int t = pendingTemp;
                if (t > 0) {
                    pendingTemp = -1;
                    Rpc.call(a, AC, AC_SET_DRV_TEMP, p -> p.writeInt(t));
                }
                int f = pendingFan;
                if (f >= 0) {
                    pendingFan = -1;
                    Rpc.call(a, AC, AC_SET_AIR_VOLUME, p -> p.writeInt(f));
                }
            } catch (Exception e) {
                Diag.error("clima.comando", e.toString());
                synchronized (subs) {
                    subs.remove("aircondition");
                }
            }
        });
    }

    public void setHvacPower(boolean on) {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub("aircondition");
                if (a != null) Rpc.call(a, AC, on ? AC_OPEN_POWER : AC_CLOSE_POWER, Rpc.NONE);
            } catch (Exception e) {
                Diag.error("clima.accensione", e.toString());
            }
            poll();
        });
    }
}
