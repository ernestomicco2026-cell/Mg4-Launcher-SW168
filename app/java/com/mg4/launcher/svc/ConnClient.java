package com.mg4.launcher.svc;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;

/**
 * Stato delle connessioni per gli indicatori in alto a destra: Bluetooth, Wi-Fi e rete mobile della T-Box (4G).
 * Il 4G si legge come la barra di stato originale: servizio engmode → "system_hardware" → getMobileNetworkInfoBean.
 */
public final class ConnClient {
    private static final String ENG_PKG = "com.saicmotor.service.engmode";
    private static final String ENG_ACTION = "com.saicmotor.service.engmode.EngineeringModeService";
    private static final String ENG = "com.saicmotor.sdk.engmode.IEngineeringMode";
    private static final int ENG_GET_SERVICE = 1;
    private static final String HW = "com.saicmotor.sdk.engmode.ISystemHardwareManager";
    private static final int HW_GET_MOBILE = 3;
    /** Soglie della barra originale (SignalController): 0..4 tacche. */
    private static final int[] SIGNAL_STEPS = {0, 3, 5, 8, 12};

    public static final class State {
        public boolean btOn, btConnected;
        public boolean wifiConnected;
        public int wifiLevel;          // 0..3
        public int mobileType = -1;    // 0 nessuna rete, 2 = 2G, 3 = 3G, 4 = 4G; -1 sconosciuto
        public int mobileLevel;        // 0..4
    }

    public interface Listener {
        void onConn(State s);
    }

    private final Context ctx;
    private final ServiceLink eng;
    private final HandlerThread thread = new HandlerThread("mg4-conn");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile State last = new State();
    private volatile IBinder hw;
    private boolean running;

    public ConnClient(Context c) {
        ctx = c.getApplicationContext();
        eng = new ServiceLink(ctx, "engmode", ServiceLink.byAction(ENG_PKG, ENG_ACTION));
        eng.setOnConnected(() -> hw = null);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public State last() {
        return last;
    }

    public void start() {
        if (bg == null) {
            thread.start();
            bg = new Handler(thread.getLooper());
        }
        eng.start();
        resume();
    }

    public void resume() {
        if (bg == null || running) return;
        running = true;
        bg.post(loop);
    }

    public void pause() {
        running = false;
        if (bg != null) bg.removeCallbacks(loop);
    }

    public void stop() {
        pause();
        eng.stop();
    }

    private final Runnable loop = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            poll();
            bg.postDelayed(this, 3000);
        }
    };

    private void poll() {
        if (com.mg4.launcher.Flavor.DEMO) {
            State d = Demo.conn();
            last = d;
            main.post(() -> {
                if (listener != null) listener.onConn(d);
            });
            return;
        }
        State s = new State();
        // Bluetooth: acceso e collegato a un telefono
        try {
            BluetoothAdapter bt = BluetoothAdapter.getDefaultAdapter();
            if (bt != null) {
                s.btOn = bt.isEnabled();
                if (s.btOn) {
                    Object st = BluetoothAdapter.class.getMethod("getConnectionState").invoke(bt);
                    s.btConnected = st instanceof Integer && (Integer) st == BluetoothAdapter.STATE_CONNECTED;
                }
            }
        } catch (Throwable e) {
            Diag.error("conn.bluetooth", e.toString());
        }
        // Wi-Fi: collegato a una rete e intensità 0..3
        try {
            ConnectivityManager cm = (ConnectivityManager) ctx.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm != null ? cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI) : null;
            s.wifiConnected = ni != null && ni.isConnected();
            if (s.wifiConnected) {
                WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
                WifiInfo wi = wm != null ? wm.getConnectionInfo() : null;
                s.wifiLevel = wi != null ? WifiManager.calculateSignalLevel(wi.getRssi(), 4) : 3;
            }
        } catch (Throwable e) {
            Diag.error("conn.wifi", e.toString());
        }
        // rete mobile della T-Box
        try {
            IBinder e = eng.binder();
            if (e != null) {
                if (hw == null || !hw.isBinderAlive()) {
                    hw = Rpc.callBinder(e, ENG, ENG_GET_SERVICE, p -> p.writeString("system_hardware"));
                }
                IBinder h = hw;
                if (h != null) {
                    int[] m = Rpc.call(h, HW, HW_GET_MOBILE, Rpc.NONE, p -> Rpc.readNullable(p, ConnClient::readMobile));
                    if (m != null) {
                        s.mobileType = m[0];
                        s.mobileLevel = m[1];
                    }
                }
            }
        } catch (Throwable e) {
            hw = null;
            Diag.error("conn.4g", e.toString());
        }
        Diag.putQuiet("conn.bt/wifi/rete", (s.btConnected ? "BT collegato" : s.btOn ? "BT acceso" : "BT spento")
                + " · " + (s.wifiConnected ? "Wi-Fi " + s.wifiLevel + "/3" : "Wi-Fi no")
                + " · " + (s.mobileType < 0 ? "rete ?" : s.mobileType == 0 ? "nessuna rete" : s.mobileType + "G " + s.mobileLevel + "/4"));
        last = s;
        main.post(() -> {
            if (listener != null) listener.onConn(s);
        });
    }

    /** MobileNetworkInfoBean: operatore, standard, intensità (stringa), area, IMSI, roaming, stato T-Box. */
    private static int[] readMobile(Parcel p) {
        p.readString();                       // operatore
        int standard = p.readInt();
        String strength = p.readString();
        p.readInt();                          // codice area
        p.readString();                       // IMSI (non usato)
        p.readInt();                          // roaming
        int tbox = p.readInt();
        if (tbox <= 0 || strength == null) return new int[]{0, 0};
        int value;
        try {
            value = Integer.parseInt(strength.trim());
        } catch (NumberFormatException ex) {
            return new int[]{0, 0};
        }
        int level = 0;
        if (value != 99) for (int i = SIGNAL_STEPS.length - 1; i >= 0; i--) if (value >= SIGNAL_STEPS[i]) { level = i; break; }
        int type;
        switch (standard) {
            case 1:
            case 3: type = 2; break;
            case 2:
            case 4:
            case 5: type = 3; break;
            case 6: type = 4; break;
            default: type = 0;
        }
        if (value == 99) type = 0;
        return new int[]{type, level};
    }
}
