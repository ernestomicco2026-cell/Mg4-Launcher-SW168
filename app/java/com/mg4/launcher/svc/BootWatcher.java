package com.mg4.launcher.svc;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Avvio all'accensione. Il firmware, all'avvio, apre la home MG per nome (com.saicmotor.hmi.launcher) saltando la
 * home predefinita: questo receiver guarda l'attività in primo piano ogni 400 ms e, appena compare la home MG,
 * riporta in primo piano il nostro launcher.
 * <ul>
 *   <li>Si ferma quando il nostro launcher è in primo piano da 5 s, o al più tardi dopo 55 s (il limite di un
 *       receiver in background è 60 s).</li>
 *   <li>Riapre il launcher al massimo 3 volte (niente cicli con il firmware).</li>
 *   <li>Non interviene se in primo piano c'è un'altra app (retrocamera, CarPlay, Android Auto, chiamata, avvisi
 *       di sistema) né se la home MG è stata aperta apposta da "Apri la home originale MG".</li>
 * </ul>
 */
public final class BootWatcher extends BroadcastReceiver {
    public static final String MG_HOME = "com.saicmotor.hmi.launcher";
    private static final long PERIOD_MS = 400, OURS_STABLE_MS = 5000, MAX_MS = 55_000, ORIGINAL_GRACE_MS = 10 * 60_000L;
    private static final int MAX_RESTARTS = 3;

    private static volatile boolean running;

    /** Da chiamare quando l'utente apre apposta la home MG: per un po' il controllo all'avvio non interviene. */
    public static void userOpenedOriginal(Context c) {
        prefs(c).edit().putLong("homeOriginaleFinoA", System.currentTimeMillis() + ORIGINAL_GRACE_MS).apply();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        boolean locked = Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action);
        if (!locked && !Intent.ACTION_BOOT_COMPLETED.equals(action)) return;
        String time = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date());
        Diag.put("avvio.boot", "ricevuto alle " + time + " (" + (locked ? "locked" : "normale") + ")"
                + " · " + (SystemClock.elapsedRealtime() / 1000) + " s dall'accensione");
        if (running) return;   // il secondo broadcast (BOOT_COMPLETED dopo LOCKED) non ne avvia un altro
        running = true;
        PendingResult pending = goAsync();
        new Watch(context.getApplicationContext(), pending).start();
    }

    private static final class Watch {
        private final Context ctx;
        private final PendingResult pending;
        private final ActivityManager am;
        private final HandlerThread thread = new HandlerThread("mg4-boot");
        private Handler h;
        private final long start = SystemClock.elapsedRealtime();
        private long oursSince = -1;
        private int restarts;
        private boolean mgSeen;
        private String lastTop = "";

        Watch(Context c, PendingResult p) {
            ctx = c;
            pending = p;
            am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        }

        void start() {
            thread.start();
            h = new Handler(thread.getLooper());
            Diag.put("avvio.homeRipresa", "controllo in corso");
            h.post(tick);
        }

        private final Runnable tick = new Runnable() {
            @Override
            public void run() {
                long now = SystemClock.elapsedRealtime();
                String top = topPackage();
                if (top != null && !top.equals(lastTop)) {
                    lastTop = top;
                    Diag.putQuiet("avvio.ultimoPrimoPiano", top + " dopo " + (now / 1000) + " s");
                }
                if (MG_HOME.equals(top)) {
                    if (!mgSeen) {
                        mgSeen = true;
                        Diag.put("avvio.primoPiano", top + " dopo " + (now / 1000) + " s dall'accensione");
                    }
                    oursSince = -1;
                    long until = prefs(ctx).getLong("homeOriginaleFinoA", 0);
                    if (System.currentTimeMillis() < until) {
                        finish("no (home MG aperta apposta)");
                        return;
                    }
                    if (restarts < MAX_RESTARTS) {
                        restarts++;
                        openOurs();
                    }
                } else if (ctx.getPackageName().equals(top)) {
                    if (oursSince < 0) oursSince = now;
                    if (now - oursSince >= OURS_STABLE_MS) {
                        finish(restarts > 0 ? "sì" : "no (già in primo piano)");
                        return;
                    }
                } else {
                    oursSince = -1;   // un'altra app (retrocamera, CarPlay, chiamata...): non si tocca
                }
                if (now - start >= MAX_MS) {
                    finish(restarts > 0 ? "sì (tempo scaduto)" : "no (tempo scaduto)");
                    return;
                }
                h.postDelayed(this, PERIOD_MS);
            }
        };

        private String topPackage() {
            try {
                @SuppressWarnings("deprecation")
                List<ActivityManager.RunningTaskInfo> tasks = am.getRunningTasks(1);
                if (tasks == null || tasks.isEmpty() || tasks.get(0).topActivity == null) return null;
                return tasks.get(0).topActivity.getPackageName();
            } catch (Throwable e) {
                Diag.error("avvio.primoPiano", e.toString());
                return null;
            }
        }

        private void openOurs() {
            try {
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME)
                        .setComponent(new ComponentName(ctx.getPackageName(), ctx.getPackageName() + ".MainActivity"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                ctx.startActivity(i);
                Diag.put("avvio.homeRipresa", "riaperto il launcher (tentativo " + restarts + ")");
            } catch (Throwable e) {
                Diag.error("avvio.homeRipresa", e.toString());
            }
        }

        private void finish(String result) {
            Diag.put("avvio.homeRipresa", result + ", tentativi " + restarts
                    + " · fine controllo dopo " + ((SystemClock.elapsedRealtime() - start) / 1000) + " s");
            if (!mgSeen) Diag.put("avvio.primoPiano", "home MG non vista · ultimo: " + lastTop);
            running = false;
            thread.quitSafely();
            try {
                pending.finish();
            } catch (Throwable ignored) {
            }
        }
    }
}
