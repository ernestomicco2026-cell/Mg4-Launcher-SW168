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
 * Riconferma che il nostro launcher è la home predefinita (un aggiornamento del firmware o di un'app di sistema
 * può averla azzerata). replacePreferredActivity richiede SET_PREFERRED_APPLICATIONS, concesso dalla firma platform.
 */
public final class HomeDefault {
    private HomeDefault() {}

    public static void ensure(Context c) {
        try {
            PackageManager pm = c.getPackageManager();
            Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
            ResolveInfo cur = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            String curPkg = cur != null && cur.activityInfo != null ? cur.activityInfo.packageName : "";
            if (c.getPackageName().equals(curPkg)) {
                Diag.put("avvio.homePredefinita", "ok");
                return;
            }
            List<ResolveInfo> all = pm.queryIntentActivities(home, 0);
            List<ComponentName> set = new ArrayList<>();
            int bestMatch = 0;
            for (ResolveInfo ri : all) {
                set.add(new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name));
                bestMatch = Math.max(bestMatch, ri.match);
            }
            ComponentName ours = new ComponentName(c.getPackageName(), c.getPackageName() + ".MainActivity");
            IntentFilter f = new IntentFilter(Intent.ACTION_MAIN);
            f.addCategory(Intent.CATEGORY_HOME);
            f.addCategory(Intent.CATEGORY_DEFAULT);
            int match = bestMatch != 0 ? bestMatch : IntentFilter.MATCH_CATEGORY_EMPTY;
            // API nascosta in Android 9: si chiama per reflection
            PackageManager.class.getMethod("replacePreferredActivity", IntentFilter.class, int.class, ComponentName[].class, ComponentName.class)
                    .invoke(pm, f, match, set.toArray(new ComponentName[0]), ours);
            ResolveInfo now = pm.resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
            boolean ok = now != null && now.activityInfo != null && c.getPackageName().equals(now.activityInfo.packageName);
            Diag.put("avvio.homePredefinita", ok ? "ripristinata (era " + (curPkg.isEmpty() ? "nessuna" : curPkg) + ")"
                    : "errore: ancora " + (now != null && now.activityInfo != null ? now.activityInfo.packageName : "nessuna"));
        } catch (Throwable e) {
            Throwable t = e instanceof java.lang.reflect.InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            Diag.put("avvio.homePredefinita", "errore: " + t);
        }
    }
}
