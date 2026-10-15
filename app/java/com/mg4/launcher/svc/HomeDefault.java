package com.mg4.launcher.svc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Home predefinita (ACTION_MAIN + CATEGORY_HOME): la nostra o quella MG originale. replacePreferredActivity
 * richiede SET_PREFERRED_APPLICATIONS, concesso dalla firma platform. Un aggiornamento del firmware o di un'app
 * di sistema può azzerarla: per questo si riconferma a ogni avvio del launcher.
 */
public final class HomeDefault {
    public static final ComponentName MG = new ComponentName(BootWatcher.MG_HOME, BootWatcher.MG_HOME + ".ui.MainActivity");

    private HomeDefault() {}

    public static ComponentName ours(Context c) {
        return new ComponentName(c.getPackageName(), "com.mg4.launcher.MainActivity");
    }

    /** Riconferma la nostra home (solo se è attiva, cioè interruttore acceso e niente officina). */
    public static void ensure(Context c) {
        if (!HomeMode.active(c)) {
            Diag.put("avvio.homePredefinita", "non riconfermata (" + HomeMode.reason(c) + ")");
            return;
        }
        Diag.put("avvio.homePredefinita", set(c, ours(c)));
    }

    /** Rende predefinita la home MG originale. */
    public static void setMg(Context c) {
        Diag.put("avvio.homePredefinita", "MG: " + set(c, MG));
    }

    /** Imposta la home predefinita; restituisce "ok" (già così), "ripristinata" o "errore: …". */
    private static String set(Context c, ComponentName target) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo cur = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            String curPkg = cur != null && cur.activityInfo != null ? cur.activityInfo.packageName : "";
            if (target.getPackageName().equals(curPkg)) return "ok";
            List<ResolveInfo> all = pm.queryIntentActivities(home, 0);
            List<ComponentName> set = new ArrayList<>();
            int bestMatch = 0;
            for (ResolveInfo ri : all) {
                set.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                bestMatch = Math.max(bestMatch, ri.match);
            }
            IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
            f.addCategory(Intent.CATEGORY_HOME);
            f.addCategory(Intent.CATEGORY_DEFAULT);
            int match = bestMatch != 0 ? bestMatch : IntentFilter.MATCH_CATEGORY_EMPTY;
            // API nascosta in Android 9: si chiama per reflection
            PackageManager.class.getMethod("replacePreferredActivity", IntentFilter.class, int.class, ComponentName[].class, ComponentName.class)
                    .invoke(pm, f, match, set.toArray(new ComponentName[0]), target);
            ResolveInfo now = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            boolean ok = now != null && now.activityInfo != null && target.getPackageName().equals(now.activityInfo.packageName);
            return ok ? "ripristinata (era " + (curPkg.isEmpty() ? "nessuna" : curPkg) + ")"
                    : "errore: ancora " + (now != null && now.activityInfo != null ? now.activityInfo.packageName : "nessuna");
        } catch (Throwable e) {
            Throwable t = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            return "errore: " + t;
        }
    }
}
