package com.mg4.launcher.svc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.HandlerThread;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Loghi delle stazioni radio.
 * - DAB: il logo trasmesso dalla stazione (letto dal servizio radio) viene salvato e riusato.
 * - FM (e DAB senza logo): ricerca per nome nel database pubblico radio-browser.info, una volta sola per stazione.
 * Tutto resta in cache su disco (files/loghi), quindi funziona anche senza rete dopo il primo download.
 */
public final class StationLogos {
    private static final long RETRY_MS = 3L * 24 * 3600 * 1000;   // nuovo tentativo dopo 3 giorni se non trovato
    private static final String API = "https://de1.api.radio-browser.info/json/stations/search";

    private final File dir;
    private final Map<String, Bitmap> mem = new HashMap<>();
    private final Set<String> pending = new HashSet<>();
    private final Set<String> savedThisSession = new HashSet<>();
    private final HandlerThread thread = new HandlerThread("mg4-logos");
    private final Handler bg;
    private volatile Runnable onUpdate;

    public StationLogos(Context c) {
        dir = new File(c.getFilesDir(), "loghi");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        thread.start();
        bg = new Handler(thread.getLooper());
    }

    public void setOnUpdate(Runnable r) {
        onUpdate = r;
    }

    public static String key(int band, long sid, int khz, String name) {
        if (band == MediaClient.BAND_DAB && sid > 0) return "dab_" + Long.toHexString(sid);
        String n = clean(name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return n.isEmpty() ? "fm_" + khz : "fm_" + n;
    }

    private static String clean(String s) {
        return s == null ? "" : s.replaceAll("\\s+", " ").trim();
    }

    /** Logo in cache (memoria o disco); se manca e c'è un nome, avvia la ricerca online in background. */
    public synchronized Bitmap get(String key, String name) {
        if (mem.containsKey(key)) return mem.get(key);
        File f = new File(dir, key + ".png");
        if (f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getPath());
            mem.put(key, b);
            return b;
        }
        File none = new File(dir, key + ".none");
        boolean recentMiss = none.exists() && System.currentTimeMillis() - none.lastModified() < RETRY_MS;
        String n = clean(name);
        if (!recentMiss && n.length() >= 2 && !n.matches("[0-9.,\\s]+") && pending.add(key)) {
            bg.post(() -> lookup(key, n));
        }
        return null;
    }

    /** Logo ricevuto dalla stazione DAB: salvato per i preferiti. */
    public synchronized void put(String key, Bitmap b) {
        if (b == null || b.getWidth() < 8) return;
        mem.put(key, b);
        if (!savedThisSession.add(key)) return;   // salvato su disco una volta per avvio
        Bitmap copy = b;
        bg.post(() -> save(key, copy));
    }

    private void save(String key, Bitmap b) {
        try (FileOutputStream o = new FileOutputStream(new File(dir, key + ".png"))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
            //noinspection ResultOfMethodCallIgnored
            new File(dir, key + ".none").delete();
        } catch (Exception e) {
            Diag.error("loghi.salva", e.toString());
        }
    }

    private void lookup(String key, String name) {
        Bitmap found = null;
        try {
            String url = API + "?name=" + URLEncoder.encode(name, "UTF-8")
                    + "&countrycode=IT&hidebroken=true&order=clickcount&reverse=true&limit=8";
            JSONArray arr = new JSONArray(new String(fetch(url, 200_000), "UTF-8"));
            for (int i = 0; i < arr.length() && found == null; i++) {
                JSONObject o = arr.getJSONObject(i);
                String fav = o.optString("favicon", "");
                if (!fav.startsWith("http")) continue;
                try {
                    byte[] img = fetch(fav, 1_500_000);
                    Bitmap b = BitmapFactory.decodeByteArray(img, 0, img.length);
                    if (b != null && b.getWidth() >= 32 && b.getHeight() >= 32) found = scale(b);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            Diag.put("loghi.ultimoErrore", name + ": " + e.getClass().getSimpleName());
            synchronized (this) {
                pending.remove(key);   // probabilmente senza rete: si riprova più tardi
            }
            return;
        }
        synchronized (this) {
            pending.remove(key);
            if (found != null) {
                mem.put(key, found);
                save(key, found);
            } else {
                try {
                    //noinspection ResultOfMethodCallIgnored
                    new File(dir, key + ".none").createNewFile();
                    //noinspection ResultOfMethodCallIgnored
                    new File(dir, key + ".none").setLastModified(System.currentTimeMillis());
                } catch (Exception ignored) {
                }
            }
        }
        Diag.put("loghi." + key, found != null ? "trovato" : "non trovato");
        Runnable r = onUpdate;
        if (found != null && r != null) r.run();
    }

    private static Bitmap scale(Bitmap b) {
        int max = 192;
        if (b.getWidth() <= max && b.getHeight() <= max) return b;
        float k = max / (float) Math.max(b.getWidth(), b.getHeight());
        return Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
    }

    private static byte[] fetch(String url, int maxBytes) throws Exception {
        for (int hop = 0; hop < 4; hop++) {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(10000);
            c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent", "MG4Launcher/1.0");
            int code = c.getResponseCode();
            if (code / 100 == 3) {
                String loc = c.getHeaderField("Location");
                c.disconnect();
                if (loc == null) break;
                url = new URL(new URL(url), loc).toString();
                continue;
            }
            if (code != 200) {
                c.disconnect();
                throw new Exception("HTTP " + code);
            }
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                byte[] buf = new byte[16384];
                int n, total = 0;
                while ((n = in.read(buf)) > 0) {
                    total += n;
                    if (total > maxBytes) throw new Exception("troppo grande");
                    bo.write(buf, 0, n);
                }
                return bo.toByteArray();
            } finally {
                c.disconnect();
            }
        }
        throw new Exception("troppi redirect");
    }
}
