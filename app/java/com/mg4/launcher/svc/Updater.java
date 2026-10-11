package com.mg4.launcher.svc;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Aggiornamenti dal repository GitHub: legge l'ultima release, scarica l'APK allegato e lo installa
 * con PackageInstaller (il launcher è firmato con la chiave platform, quindi non serve conferma).
 */
public final class Updater {
    private static final String ACTION_RESULT = "com.mg4.launcher.INSTALL_RESULT";

    public static final class Release {
        public String tag = "";
        public String name = "";
        public String notes = "";
        public String apkUrl;       // URL API dell'allegato
        public String apkName = "";
        public long apkSize;
        public boolean newer;
    }

    public interface Listener {
        /** Messaggio di stato da mostrare (sul thread principale). */
        void onUpdateStatus(String text, Release r, boolean busy);
    }

    private final Context ctx;
    private final HandlerThread thread = new HandlerThread("mg4-update");
    private final Handler bg;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Listener listener;
    private volatile boolean busy;

    public Updater(Context c) {
        ctx = c.getApplicationContext();
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    public void setListener(Listener l) {
        listener = l;
    }

    public boolean isBusy() {
        return busy;
    }

    public String currentVersion() {
        try {
            PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            return pi.versionName;
        } catch (Exception e) {
            return "?";
        }
    }

    private void status(String s, Release r, boolean b) {
        busy = b;
        Diag.put("aggiornamenti", s);
        main.post(() -> {
            if (listener != null) listener.onUpdateStatus(s, r, b);
        });
    }

    /** Controlla l'ultima release del repository. */
    public void check(String repo, String token) {
        if (busy) return;
        status("Controllo su GitHub…", null, true);
        bg.post(() -> {
            try {
                Release r = latest(repo, token);
                if (r.apkUrl == null) {
                    status("L'ultima release (" + r.tag + ") non contiene un APK", r, false);
                } else if (r.newer) {
                    status("Nuova versione disponibile: " + r.tag, r, false);
                } else {
                    status("Sei aggiornato (" + currentVersion() + ", ultima release " + r.tag + ")", r, false);
                }
            } catch (HttpError e) {
                status(e.getMessage(), null, false);
            } catch (Exception e) {
                status("Nessuna connessione a GitHub (" + e.getClass().getSimpleName() + ")", null, false);
                Diag.error("aggiornamenti", e.toString());
            }
        });
    }

    /** Scarica l'APK della release e lo installa. */
    public void install(Release r, String token) {
        if (busy || r == null || r.apkUrl == null) return;
        status("Download di " + r.apkName + "…", r, true);
        bg.post(() -> {
            File apk = new File(ctx.getCacheDir(), "update.apk");
            try {
                download(r, token, apk);
                PackageInfo pi = ctx.getPackageManager().getPackageArchiveInfo(apk.getPath(), 0);
                if (pi == null || !ctx.getPackageName().equals(pi.packageName)) {
                    status("Il file scaricato non è un aggiornamento del launcher", r, false);
                    return;
                }
                status("Installazione di " + pi.versionName + "… il launcher si riavvierà da solo", r, true);
                installApk(apk);
            } catch (HttpError e) {
                status(e.getMessage(), r, false);
            } catch (Exception e) {
                status("Download non riuscito (" + e.getClass().getSimpleName() + ")", r, false);
                Diag.error("aggiornamenti.download", e.toString());
            }
        });
    }

    // ------------------------------------------------------------------------------------------

    private static final class HttpError extends IOException {
        HttpError(String m) {
            super(m);
        }
    }

    private Release latest(String repo, String token) throws Exception {
        HttpURLConnection c = open("https://api.github.com/repos/" + repo + "/releases/latest", token, "application/vnd.github+json");
        int code = c.getResponseCode();
        if (code == 404) {
            c.disconnect();
            throw new HttpError(token.isEmpty()
                    ? "Repository privato: inserisci il token GitHub qui sotto"
                    : "Nessuna release trovata su " + repo + " (o il token non può leggerla)");
        }
        if (code == 401) throw new HttpError("Token GitHub non valido o scaduto");
        if (code == 403) throw new HttpError("GitHub ha rifiutato la richiesta (limite richieste o permessi del token)");
        if (code != 200) throw new HttpError("Risposta GitHub inattesa: " + code);
        JSONObject o = new JSONObject(readAll(c.getInputStream()));
        c.disconnect();

        Release r = new Release();
        r.tag = o.optString("tag_name", "");
        r.name = o.optString("name", r.tag);
        r.notes = o.optString("body", "");
        JSONArray assets = o.optJSONArray("assets");
        if (assets != null) {
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase().endsWith(".apk")) {
                    r.apkUrl = a.optString("url");
                    r.apkName = a.optString("name");
                    r.apkSize = a.optLong("size");
                    break;
                }
            }
        }
        r.newer = compare(r.tag, currentVersion()) > 0;
        return r;
    }

    private void download(Release r, String token, File out) throws Exception {
        // l'allegato risponde con un redirect verso lo storage di GitHub: lo si segue senza il token
        HttpURLConnection c = open(r.apkUrl, token, "application/octet-stream");
        c.setInstanceFollowRedirects(false);
        int code = c.getResponseCode();
        for (int hops = 0; code / 100 == 3 && hops < 5; hops++) {
            String loc = c.getHeaderField("Location");
            c.disconnect();
            c = open(loc, "", "application/octet-stream");
            c.setInstanceFollowRedirects(false);
            code = c.getResponseCode();
        }
        if (code != 200) throw new HttpError("Download non riuscito: risposta " + code);
        long total = c.getContentLengthLong() > 0 ? c.getContentLengthLong() : r.apkSize;
        try (InputStream in = c.getInputStream(); OutputStream os = new FileOutputStream(out)) {
            byte[] buf = new byte[64 * 1024];
            long done = 0, lastShown = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                os.write(buf, 0, n);
                done += n;
                if (total > 0 && done - lastShown > total / 20) {
                    lastShown = done;
                    status("Download " + (done * 100 / total) + "%…", r, true);
                }
            }
        } finally {
            c.disconnect();
        }
    }

    private void installApk(File apk) throws Exception {
        PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(ctx.getPackageName());
        p.setSize(apk.length());
        // memoria interna esplicita: il firmware MG blocca le app non di sistema sull'archivio interno "automatico"
        p.setInstallLocation(PackageInfo.INSTALL_LOCATION_INTERNAL_ONLY);
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (OutputStream os = s.openWrite("base.apk", 0, apk.length()); InputStream in = new FileInputStream(apk)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) os.write(buf, 0, n);
                s.fsync(os);
            }
            ctx.registerReceiver(result, new IntentFilter(ACTION_RESULT));
            PendingIntent cb = PendingIntent.getBroadcast(ctx, id,
                    new Intent(ACTION_RESULT).setPackage(ctx.getPackageName()), PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(cb.getIntentSender());
        }
    }

    private final BroadcastReceiver result = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int st = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
            if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                // senza il permesso di sistema Android chiede conferma: si mostra la schermata standard
                Intent confirm = i.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirm != null) ctx.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                status("Conferma l'installazione sullo schermo", null, true);
                return;
            }
            try {
                ctx.unregisterReceiver(this);
            } catch (Exception ignored) {
            }
            if (st == PackageInstaller.STATUS_SUCCESS) {
                status("Aggiornamento installato", null, false);
            } else {
                String msg = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                status("Installazione non riuscita: " + (msg == null ? "errore " + st : msg), null, false);
            }
        }
    };

    private static HttpURLConnection open(String url, String token, String accept) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", accept);
        c.setRequestProperty("User-Agent", "MG4Launcher");
        if (token != null && !token.isEmpty()) c.setRequestProperty("Authorization", "token " + token);
        return c;
    }

    private static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) > 0) bo.write(b, 0, n);
        in.close();
        return bo.toString("UTF-8");
    }

    /** Confronta versioni tipo "v0.3.0" e "0.2.0" numero per numero. */
    static int compare(String a, String b) {
        int[] x = nums(a), y = nums(b);
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int p = i < x.length ? x[i] : 0, q = i < y.length ? y[i] : 0;
            if (p != q) return p < q ? -1 : 1;
        }
        return 0;
    }

    private static int[] nums(String v) {
        String[] parts = v.replaceAll("[^0-9.]", "").split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                out[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                out[i] = 0;
            }
        }
        return out;
    }

    /** true se il launcher ha il permesso di installare senza conferma (firmato con la chiave platform). */
    public boolean canInstallSilently() {
        return ctx.checkSelfPermission("android.permission.INSTALL_PACKAGES") == PackageManager.PERMISSION_GRANTED;
    }
}
