package com.mg4.launcher.svc;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;

/**
 * Navigatore MG tramite com.saicmotor.adapterservice (GeneralService): stato della guida,
 * via corrente, distanza/tempo rimanenti e comandi Casa / Lavoro / Apri navigatore.
 */
public final class NavClient {
    private static final String PKG = "com.saicmotor.adapterservice";
    private static final String CLS = "com.saicmotor.adapterservice.services.GeneralService";
    private static final String GS = "com.saicmotor.adapterservice.IGeneralService";
    private static final int GET_MAP_PKG = 9, HAS_HOME = 12, HAS_OFFICE = 13, IS_NAVIGATING = 18, START_NAV = 20,
            GO_HOME = 21, GO_OFFICE = 22, DIST_UNIT = 28, ROAD = 29, GUIDE_STATUS = 31, REMAIN_TIME = 33, REMAIN_DIST = 34;

    public static final class State {
        public boolean connected;
        public boolean navigating;
        public String road = "";
        public int remainMeters = -1;
        public int remainSeconds = -1;
        public boolean hasHome, hasOffice;
        public String mapPackage = "";
    }

    public interface Listener {
        void onNavState(State s);
    }

    private final ServiceLink link;
    private final HandlerThread thread = new HandlerThread("mg4-nav");
    private Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile State last = new State();
    private boolean running;

    public NavClient(Context ctx) {
        link = new ServiceLink(ctx, "navigatore", ServiceLink.byComponent(PKG, CLS, null));
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
        link.start();
        bg.post(loop);
    }

    public void stop() {
        pause();
        link.stop();
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

    private void poll() {
        State s = new State();
        IBinder b = link.binder();
        s.connected = b != null;
        if (b != null) {
            try {
                s.navigating = Rpc.callBool(b, GS, IS_NAVIGATING);
                s.hasHome = Rpc.callBool(b, GS, HAS_HOME);
                s.hasOffice = Rpc.callBool(b, GS, HAS_OFFICE);
                String pkg = Rpc.callString(b, GS, GET_MAP_PKG);
                s.mapPackage = pkg == null ? "" : pkg;
                if (s.navigating) {
                    String road = Rpc.callString(b, GS, ROAD);
                    s.road = road == null ? "" : road.trim();
                    s.remainMeters = Rpc.callInt(b, GS, REMAIN_DIST);
                    s.remainSeconds = Rpc.callInt(b, GS, REMAIN_TIME);
                    Diag.put("nav.statoGuida", Rpc.callInt(b, GS, GUIDE_STATUS));
                    Diag.put("nav.unitaDistanza", Rpc.callInt(b, GS, DIST_UNIT));
                }
                Diag.put("nav.inGuida", s.navigating + " · via " + s.road + " · " + s.remainMeters + " m · " + s.remainSeconds + " s");
                Diag.put("nav.app", s.mapPackage);
            } catch (Exception e) {
                Diag.error("navigatore", e.toString());
            }
        }
        last = s;
        main.post(() -> {
            if (listener != null) listener.onNavState(s);
        });
    }

    private void command(int code) {
        if (bg == null) return;
        bg.post(() -> {
            try {
                Rpc.call(link.binder(), GS, code, Rpc.NONE);
            } catch (Exception e) {
                Diag.error("navigatore.comando " + code, e.toString());
            }
        });
    }

    public boolean isConnected() {
        return link.isConnected();
    }

    public void openNavigator() {
        command(START_NAV);
    }

    public void goHome() {
        command(GO_HOME);
    }

    public void goOffice() {
        command(GO_OFFICE);
    }
}
