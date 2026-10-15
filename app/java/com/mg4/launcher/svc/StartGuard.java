package com.mg4.launcher.svc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;
import android.os.SystemClock;

import java.util.List;

/**
 * Logica comune dell'avvio all'accensione, usata dal servizio di accessibilità (eventi di cambio finestra) e,
 * se il servizio non è attivo, dal controllo di riserva ogni 400 ms (BootWatcher).
 * <p>
 * Finestra di avvio: dall'accensione (o dal risveglio dello schermo) fino a 5 s dopo che il nostro launcher è in
 * primo piano, al massimo 60 s. Dentro la finestra, se compare la home MG, si riapre il nostro launcher (al
 * massimo 3 volte). Non interviene se la home MG è stata aperta apposta, se in primo piano c'è un'altra app
 * (retrocamera, CarPlay, Android Auto, chiamata, avvisi) o se l'interruttore è spento / c'è l'officina.
 */
public final class StartGuard {
    private static final long WINDOW_MS = 60_000, OURS_STABLE_MS = 5000, ORIGINAL_GRACE_MS = 10 * 60_000L;
    private static final int MAX_RESTARTS = 3;

    // tempi da SystemClock.elapsedRealtime() (ms dall'accensione), -1 = non ancora
    private static long tProcess = -1, tA11y = -1, tBoot = -1, tMg = -1, tOurs = -1;
    private static long windowStart = -1, oursSince = -1;
    private static int restarts;
    private static String windowWhy = "";
    static volatile boolean a11yConnected;

    private StartGuard() {}

    /** Da chiamare quando l'utente apre apposta la home MG: per un po' l'avvio non interviene. */
    public static void userOpenedOriginal(Context c) {
        sp(c).edit().putLong("homeOriginaleFinoA", System.currentTimeMillis() + ORIGINAL_GRACE_MS).apply();
    }

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    public static synchronized void markProcess() {
        if (tProcess < 0) tProcess = Process.getStartElapsedRealtime();
    }

    public static synchronized void markA11y() {
        markProcess();
        if (tA11y < 0) tA11y = SystemClock.elapsedRealtime();
        times();
    }

    public static synchronized void markBoot() {
        markProcess();
        if (tBoot < 0) tBoot = SystemClock.elapsedRealtime();
        times();
    }

    /** Apre la finestra di avvio (accensione o risveglio dello schermo). */
    public static synchronized void openWindow(Context c, String why) {
        markProcess();
        if (!HomeMode.active(c)) {
            Diag.put("avvio.homeRipresa", "no (" + HomeMode.reason(c) + ")");
            return;
        }
        if (windowOpen()) return;
        windowStart = SystemClock.elapsedRealtime();
        oursSince = -1;
        restarts = 0;
        windowWhy = why;
        Diag.put("avvio.homeRipresa", "controllo in corso (" + why + ")");
    }

    public static synchronized boolean windowOpen() {
        return windowStart >= 0 && SystemClock.elapsedRealtime() - windowStart < WINDOW_MS;
    }

    /** Nuova app in primo piano (evento di accessibilità o controllo periodico). */
    public static synchronized void onForeground(Context c, String pkg) {
        if (pkg == null) return;
        long now = SystemClock.elapsedRealtime();
        if (BootWatcher.MG_HOME.equals(pkg) && tMg < 0) {
            tMg = now;
            Diag.put("avvio.primoPiano", pkg + " dopo " + (now / 1000) + " s");
            times();
        }
        if (c.getPackageName().equals(pkg) && tOurs < 0) {
            tOurs = now;
            times();
        }
        if (windowStart < 0) return;
        if (now - windowStart >= WINDOW_MS) {
            close(restarts > 0 ? "sì (tempo scaduto)" : "no (tempo scaduto)");
            return;
        }
        if (!HomeMode.active(c)) {
            close("no (" + HomeMode.reason(c) + ")");
            return;
        }
        if (BootWatcher.MG_HOME.equals(pkg)) {
            oursSince = -1;
            if (System.currentTimeMillis() < sp(c).getLong("homeOriginaleFinoA", 0)) {
                close("no (home MG aperta apposta)");
                return;
            }
            if (restarts < MAX_RESTARTS) {
                restarts++;
                HomeMode.openOurs(c);
                Diag.put("avvio.homeRipresa", "riaperto il launcher (tentativo " + restarts + ", " + windowWhy + ")");
            }
        } else if (c.getPackageName().equals(pkg)) {
            if (oursSince < 0) oursSince = now;
            if (now - oursSince >= OURS_STABLE_MS) close(restarts > 0 ? "sì" : "no (già in primo piano)");
        } else {
            oursSince = -1;   // un'altra app (retrocamera, CarPlay, chiamata...): non si tocca
        }
    }

    private static void close(String result) {
        Diag.put("avvio.homeRipresa", result + ", tentativi " + restarts + " (" + windowWhy + ")");
        windowStart = -1;
        oursSince = -1;
    }

    /** App in primo piano adesso (REAL_GET_TASKS, concesso dalla firma platform). */
    public static String topPackage(Context c) {
        try {
            ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
            @SuppressWarnings("deprecation")
            List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
            if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return null;
            return tasks.get(0).topActivity.getPackageName();
        } catch (Throwable e) {
            return null;
        }
    }

    private static String s(long t) {
        return t < 0 ? "—" : String.format(java.util.Locale.ROOT, "%.1fs", t / 1000f);
    }

    private static void times() {
        Diag.put("avvio.tempi", "processo " + s(tProcess) + " · accessibilità " + s(tA11y) + " · boot " + s(tBoot)
                + " · home MG " + s(tMg) + " · nostro launcher " + s(tOurs));
    }
}
