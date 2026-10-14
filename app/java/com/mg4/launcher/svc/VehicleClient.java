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
    private static final int CHG_CONSUMPTION = 66;     // float getElecCsumpPerKm (ultimo km, kWh/100km)
    private static final int CHG_CONSUMPTION_LIST = 68;// String getElecCsumpPerKmList (JSON, ultimi 50 km)
    private static final int CHG_ACC_AFTER_CHARGE = 73, CHG_ACC_AFTER_START = 74,
            CHG_TOT_AFTER_CHARGE = 75, CHG_TOT_AFTER_START = 76;   // solo diagnostica

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
    private static final int AC_GET_OUT_TEMP = 88;    // float, -10000 = non disponibile
    // pulsanti come l'app Clima originale: il valore 1 = "premuto" (commuta)
    private static final int AC_SET_AUTO = 7, AC_SET_AC = 8, AC_GET_AUTO = 54, AC_GET_AC = 55;
    // riscaldamento: come l'app Clima, il valore 1 = "premuto"; sedili 0..3 (4 = non disponibile), volante 0 / 3
    private static final int AC_SET_DRV_SEAT_HEAT = 33, AC_SET_PSG_SEAT_HEAT = 37, AC_SET_WHEEL_HEAT = 52;
    private static final int AC_GET_DRV_SEAT_HEAT = 77, AC_GET_PSG_SEAT_HEAT = 79, AC_GET_WHEEL_HEAT = 94;
    private static final int AC_OPEN_FRONT_DEFROST = 48, AC_CLOSE_FRONT_DEFROST = 49, AC_GET_FRONT_DEFROST = 92;

    // fendinebbia, come la barra di stato originale (SystemUI): set(1) = premuto, get 1 = acceso
    private static final String SET = "com.saicmotor.sdk.vehiclesettings.IVehicleSettingService";
    private static final int SET_GET_REAR_FOG = 178, SET_SET_REAR_FOG = 179, SET_GET_FRONT_FOG = 187, SET_SET_FRONT_FOG = 188;
    private static final String COND = "com.saicmotor.sdk.vehiclesettings.IVehicleConditionService";
    private static final int COND_FRONT_FOG_CONFIG = 45;   // 0 = fendinebbia anteriori presenti

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
        public int chargeLimit = -1;      // % (convertito dal codice SAIC)
        public float consumption = -1;    // kWh/100km, media degli ultimi 50 km come la pagina Energia
        public int dischargeStatus = -1;
        public int acTemp = -1;           // °C, 17 = LO, 33 = HI
        public int hvacPower = -1;
        public int acOn = -1, autoOn = -1, defrostOn = -1;   // 1 acceso, 0 spento, -1 sconosciuto
        public int seatLeft = -1, seatRight = -1;          // 0..3, -1 non disponibile
        public int wheelHeat = -1;                         // 1 acceso, 0 spento, -1 non disponibile
        public float[] tireBar = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};   // AS, AD, PS, PD
        public float insideTemp = Float.NaN;
        public int rearFog = -1, frontFog = -1;           // 1 acceso, 0 spento, -1 non disponibile
        public boolean hasFrontFog;
        public int fanLevel = -1;         // 0 spenta, 1..max, 15 auto
        public int fanMin = 1, fanMax = 11;
        public float outsideTemp = -10000f;   // °C
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

    private final Context ctx;

    public VehicleClient(Context ctx) {
        this.ctx = ctx.getApplicationContext();
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

    /**
     * Limite di ricarica: il servizio restituisce un codice, non una percentuale.
     * 1 = 40%, 2 = 50% … 5 = 80% (modalità salute batteria) … 7 = 100% (come l'app MG iSmart).
     */
    static int limitPercent(int code) {
        if (code >= 1 && code <= 7) return (code + 3) * 10;
        return -1;
    }

    /** Media dell'elenco JSON dei consumi per km (es. "[15.2,16.0,…]"), ignorando i valori non validi. */
    static float average(String json) {
        if (json == null) return -1;
        float sum = 0;
        int n = 0;
        for (String part : json.replace("[", "").replace("]", "").split(",")) {
            try {
                float v = Float.parseFloat(part.trim());
                if (v > 0 && v < 100) {
                    sum += v;
                    n++;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return n == 0 ? -1 : sum / n;
    }

    private static int count(String json) {
        if (json == null || json.length() < 3) return 0;
        return json.split(",").length;
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
                    int limitCode = Rpc.callInt(c, CHG, CHG_LIMIT_SOC);
                    s.chargeLimit = limitPercent(limitCode);
                    s.dischargeStatus = Rpc.callInt(c, CHG, CHG_DISCHARGE_STATUS);
                    float amps = Rpc.callFloat(c, CHG, CHG_CURRENT);
                    float volts = Rpc.callFloat(c, CHG, CHG_BATTERY_VOLT);
                    s.chargeKw = Math.abs(amps * volts) / 1000f;
                    float lastKm = Rpc.callFloat(c, CHG, CHG_CONSUMPTION);
                    String list = null;
                    try {
                        list = Rpc.callString(c, CHG, CHG_CONSUMPTION_LIST);
                    } catch (Exception e) {
                        Diag.error("consumo.elenco", e.toString());
                    }
                    float avg = average(list);
                    s.consumption = avg > 0 ? avg : lastKm;
                    Diag.putQuiet("veicolo.consumo.ultimoKm", lastKm);
                    Diag.put("veicolo.consumo.media50km", avg > 0 ? String.format(java.util.Locale.ITALY, "%.1f", avg)
                            + " (" + count(list) + " km)" : "nessun dato");
                    try {
                        Diag.put("veicolo.consumo.altri", "accDaAvvio " + Rpc.callFloat(c, CHG, CHG_ACC_AFTER_START)
                                + " · totDaAvvio " + Rpc.callFloat(c, CHG, CHG_TOT_AFTER_START)
                                + " · accDaRicarica " + Rpc.callFloat(c, CHG, CHG_ACC_AFTER_CHARGE)
                                + " · totDaRicarica " + Rpc.callFloat(c, CHG, CHG_TOT_AFTER_CHARGE));
                    } catch (Exception e) {
                        Diag.error("consumo.altri", e.toString());
                    }
                    Diag.put("veicolo.soc", s.soc + " (valido " + Rpc.callBool(c, CHG, CHG_SOC_VALID) + ")");
                    Diag.put("veicolo.autonomia", s.rangeKm + " (valido " + Rpc.callBool(c, CHG, CHG_RANGE_VALID) + ")");
                    Diag.put("veicolo.statoRicarica", s.chargingStatus);
                    Diag.put("veicolo.minutiRicarica", s.predictMinutes);
                    Diag.putQuiet("veicolo.correnteA/tensioneV", amps + " / " + volts);
                    Diag.put("veicolo.limiteRicarica", "codice " + limitCode + " = " + s.chargeLimit + "%");
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
                    s.outsideTemp = Rpc.callFloat(a, AC, AC_GET_OUT_TEMP);
                    Diag.put("clima.tempEsterna", s.outsideTemp);
                    Diag.put("clima.tempGuidatore", readTemp + (s.acTemp != readTemp ? " (richiesta " + s.acTemp + ")" : ""));
                    Diag.put("clima.ventola", readFan + (s.fanLevel != readFan ? " (richiesta " + s.fanLevel + ")" : ""));
                    Diag.put("clima.accensione", s.hvacPower);
                    s.acOn = held(AC_GET_AC, Rpc.callInt(a, AC, AC_GET_AC), now);
                    s.autoOn = held(AC_GET_AUTO, Rpc.callInt(a, AC, AC_GET_AUTO), now);
                    s.defrostOn = held(AC_GET_FRONT_DEFROST, Rpc.callInt(a, AC, AC_GET_FRONT_DEFROST), now);
                    Diag.put("clima.ac/auto/sbrinamento", s.acOn + " / " + s.autoOn + " / " + s.defrostOn);
                    int ls = Rpc.callInt(a, AC, AC_GET_DRV_SEAT_HEAT), rs = Rpc.callInt(a, AC, AC_GET_PSG_SEAT_HEAT);
                    int wh = Rpc.callInt(a, AC, AC_GET_WHEEL_HEAT);
                    s.seatLeft = held(AC_GET_DRV_SEAT_HEAT, ls >= 0 && ls <= 3 ? ls : -1, now);
                    s.seatRight = held(AC_GET_PSG_SEAT_HEAT, rs >= 0 && rs <= 3 ? rs : -1, now);
                    s.wheelHeat = held(AC_GET_WHEEL_HEAT, wh == 3 ? 1 : wh == 0 ? 0 : -1, now);
                    Diag.put("clima.sedili sx/dx · volante", ls + " / " + rs + " · " + wh);
                }
            } catch (Exception e) {
                Diag.error("clima", e.toString());
                synchronized (subs) {
                    subs.remove("aircondition");
                }
            }
        }
        if (s.connected) {
            try {
                if (frontFogConfig == Integer.MIN_VALUE) {
                    IBinder cnd = sub("vehiclecondition");
                    if (cnd != null) {
                        frontFogConfig = Rpc.callInt(cnd, COND, COND_FRONT_FOG_CONFIG);
                        Diag.put("luci.fendinebbiaAnterioriConfig", frontFogConfig);
                    }
                }
                IBinder st = sub("vehiclesetting");
                if (st != null) {
                    long now = android.os.SystemClock.uptimeMillis();
                    s.rearFog = held(SET_GET_REAR_FOG, Rpc.callInt(st, SET, SET_GET_REAR_FOG), now);
                    s.hasFrontFog = frontFogConfig == 0;
                    if (s.hasFrontFog) s.frontFog = held(SET_GET_FRONT_FOG, Rpc.callInt(st, SET, SET_GET_FRONT_FOG), now);
                    Diag.put("luci.fendinebbia ant/post", s.frontFog + " / " + s.rearFog);
                }
            } catch (Exception e) {
                Diag.error("luci", e.toString());
                synchronized (subs) {
                    subs.remove("vehiclesetting");
                    subs.remove("vehiclecondition");
                }
            }
        }
        readCarProps(s);
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

    // ---- TPMS e temperatura interna dal CarService ----------------------------------------------

    private CarProps props;
    private static volatile CarProps shared;

    /** Lettore del CarService condiviso (null finché il servizio veicolo non l'ha creato). */
    public static CarProps carProps() {
        return shared;
    }
    private long propsAt;
    private final float[] tireCache = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
    private float insideCache = Float.NaN;

    private void readCarProps(State s) {
        if (props == null) {
            props = new CarProps(ctx);
            props.connect();
            shared = props;
        }
        long now = android.os.SystemClock.uptimeMillis();
        if (props.ready() && now - propsAt > 5000) {
            propsAt = now;
            int[] ids = {CarProps.TIRE_FL, CarProps.TIRE_FR, CarProps.TIRE_RL, CarProps.TIRE_RR};
            StringBuilder raw = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                int v = props.getInt(ids[i]);
                tireCache[i] = CarProps.toBar(v);
                raw.append(i == 0 ? "" : " / ").append(v == Integer.MIN_VALUE ? "?" : String.valueOf(v));
            }
            Diag.put("pneumatici (grezzo AS/AD/PS/PD)", raw);
            // temperatura interna: si legge solo se l'opzione è accesa nelle impostazioni (spesso l'auto non la fornisce)
            boolean wantInside = ctx.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE).getBoolean("showInsideTemp", false);
            if (wantInside) {
                float in = props.getFloat(CarProps.HVAC_TEMPERATURE_CURRENT);
                float amb = props.getFloat(CarProps.HVAC_AMBIENT_TEMPERATURE);
                Diag.put("clima.tempInterna (corrente / ambient)", in + " / " + amb);
                insideCache = valid(in) ? in : valid(amb) ? amb : Float.NaN;
            } else {
                Diag.put("clima.tempInterna (corrente / ambient)", "non letta (opzione spenta)");
                insideCache = Float.NaN;
            }
        }
        System.arraycopy(tireCache, 0, s.tireBar, 0, 4);
        s.insideTemp = insideCache;
    }

    private static boolean valid(float t) {
        return !Float.isNaN(t) && t > -40 && t < 80 && t != 0f;
    }

    // ---- pulsanti a commutazione (A/C, AUTO, sbrinamento, fendinebbia) ------------------------

    private int frontFogConfig = Integer.MIN_VALUE;
    private final Map<Integer, long[]> holds = new HashMap<>();   // codice lettura -> {valore, scadenza}

    /** Dopo un tocco mostra subito il nuovo stato per qualche secondo, finché l'auto non lo conferma. */
    private int held(int getCode, int read, long now) {
        synchronized (holds) {
            long[] h = holds.get(getCode);
            if (h == null) return read;
            if (now < h[1] && read != h[0]) return (int) h[0];
            holds.remove(getCode);
            return read;
        }
    }

    private void hold(int getCode, int value) {
        synchronized (holds) {
            holds.put(getCode, new long[]{value, android.os.SystemClock.uptimeMillis() + HOLD_MS});
        }
    }

    private void press(String service, String desc, int setCode, int getCode, int current, String what) {
        if (bg == null) return;
        if (current == 0 || current == 1) hold(getCode, 1 - current);
        bg.post(() -> {
            try {
                IBinder b = sub(service);
                if (b != null) Rpc.call(b, desc, setCode, p -> p.writeInt(1));
            } catch (Exception e) {
                Diag.error(what, e.toString());
                synchronized (subs) {
                    subs.remove(service);
                }
            }
            poll();
        });
    }

    public void toggleAc() {
        press("aircondition", AC, AC_SET_AC, AC_GET_AC, last.acOn, "clima.ac");
    }

    public void toggleAuto() {
        press("aircondition", AC, AC_SET_AUTO, AC_GET_AUTO, last.autoOn, "clima.auto");
    }

    /** Sedile: ogni tocco passa al livello successivo (0 → 1 → 2 → 3 → 0), come il tasto dell'app Clima. */
    public void pressSeatLeft() {
        pressLevel(AC_SET_DRV_SEAT_HEAT, AC_GET_DRV_SEAT_HEAT, last.seatLeft, "clima.sedileSx");
    }

    public void pressSeatRight() {
        pressLevel(AC_SET_PSG_SEAT_HEAT, AC_GET_PSG_SEAT_HEAT, last.seatRight, "clima.sedileDx");
    }

    public void toggleWheelHeat() {
        if (last.wheelHeat == 0 || last.wheelHeat == 1) hold(AC_GET_WHEEL_HEAT, 1 - last.wheelHeat);
        sendPress(AC_SET_WHEEL_HEAT, "clima.volante");
    }

    private void pressLevel(int setCode, int getCode, int current, String what) {
        if (current >= 0) hold(getCode, (current + 1) % 4);
        sendPress(setCode, what);
    }

    private void sendPress(int setCode, String what) {
        if (bg == null) return;
        bg.post(() -> {
            try {
                IBinder a = sub("aircondition");
                if (a != null) Rpc.call(a, AC, setCode, p -> p.writeInt(1));
            } catch (Exception e) {
                Diag.error(what, e.toString());
            }
            poll();
        });
    }

    public void toggleRearFog() {
        press("vehiclesetting", SET, SET_SET_REAR_FOG, SET_GET_REAR_FOG, last.rearFog, "luci.retronebbia");
    }

    public void toggleFrontFog() {
        press("vehiclesetting", SET, SET_SET_FRONT_FOG, SET_GET_FRONT_FOG, last.frontFog, "luci.fendinebbia");
    }

    public void toggleFrontDefrost() {
        if (bg == null) return;
        boolean on = last.defrostOn == 1;
        hold(AC_GET_FRONT_DEFROST, on ? 0 : 1);
        bg.post(() -> {
            try {
                IBinder a = sub("aircondition");
                if (a != null) Rpc.call(a, AC, on ? AC_CLOSE_FRONT_DEFROST : AC_OPEN_FRONT_DEFROST, Rpc.NONE);
            } catch (Exception e) {
                Diag.error("clima.sbrinamento", e.toString());
            }
            poll();
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
