package com.mg4.launcher.svc;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.mg4.launcher.ui.Prefs;

/**
 * Imposta il token GitHub da computer, senza scriverlo sullo schermo dell'auto:
 * adb shell am broadcast -n com.mg4.launcher/.svc.TokenReceiver --es token ghp_xxx [--es repo utente/repo]
 * Protetto da DUMP: lo possono inviare solo adb (shell) e il sistema.
 */
public final class TokenReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        Prefs p = new Prefs(c);
        String tok = i.getStringExtra("token");
        String repo = i.getStringExtra("repo");
        if (tok != null) p.setGithubToken(tok);
        if (repo != null) p.setUpdateRepo(repo);
        Log.i("MG4L", "token GitHub " + (tok == null ? "invariato" : tok.isEmpty() ? "rimosso" : "salvato") + ", repo " + p.updateRepo());
        setResultData("ok: repo " + p.updateRepo());
    }
}
