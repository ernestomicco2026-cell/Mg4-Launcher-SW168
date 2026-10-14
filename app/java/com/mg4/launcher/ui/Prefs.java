package com.mg4.launcher.ui;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/** Impostazioni del launcher. */
public final class Prefs {
    public static final String[] CAR_COLORS = {
            "Arctic White", "Black Pearl", "Camden Grey", "Cosmic Silver",
            "Ealing Green", "Piccadilly Blue", "Dynamic Red", "Volcano Orange"};
    public static final int[] CAR_COLOR_VALUES = {
            0xFFE8E9E6, 0xFF202227, 0xFF5D6166, 0xFFA9ADB2,
            0xFF4C6A5A, 0xFF2E5597, 0xFFA5161E, 0xFFE1621F};
    public static final int[] ACCENTS = {0xFF3E8BFF, 0xFFFFFFFF, 0xFF2BD4A4, 0xFFF5A524};
    public static final String THEME_AUTO = "auto", THEME_NIGHT = "notte", THEME_DAY = "giorno";

    private final SharedPreferences sp;

    public Prefs(Context c) {
        sp = c.getSharedPreferences("mg4launcher", Context.MODE_PRIVATE);
    }

    public int carColorIndex() {
        return Math.max(0, Math.min(CAR_COLORS.length - 1, sp.getInt("carColor", 5)));
    }

    public void setCarColorIndex(int i) {
        sp.edit().putInt("carColor", i).apply();
    }

    public boolean blackRoof() {
        return sp.getBoolean("blackRoof", true);
    }

    public void setBlackRoof(boolean b) {
        sp.edit().putBoolean("blackRoof", b).apply();
    }

    public int accent() {
        return sp.getInt("accent", ACCENTS[0]);
    }

    public void setAccent(int c) {
        sp.edit().putInt("accent", c).apply();
    }

    public String themeMode() {
        return sp.getString("theme", THEME_AUTO);
    }

    public void setThemeMode(String m) {
        sp.edit().putString("theme", m).apply();
    }

    // --- app installate nascoste dalla griglia ---

    public Set<String> hiddenApps() {
        return new HashSet<>(sp.getStringSet("hiddenApps", new HashSet<>()));
    }

    public void setAppHidden(String key, boolean hide) {
        Set<String> s = hiddenApps();
        if (hide) s.add(key);
        else s.remove(key);
        sp.edit().putStringSet("hiddenApps", s).apply();
    }

    // --- aggiornamenti da GitHub ---
    public static final String DEFAULT_REPO = "ernestomicco2026-cell/Mg4-Launcher-SWI68";
    /** Nomi precedenti del repository (rinominato): si passa da soli al nuovo. */
    private static final String[] OLD_REPOS = {"ernestomicco2026-cell/mg4-launcher", "ernestomicco2026-cell/Mg4-Launcher-SW168"};

    public String updateRepo() {
        String r = sp.getString("ghRepo", DEFAULT_REPO);
        for (String old : OLD_REPOS) {
            if (old.equalsIgnoreCase(r)) {
                sp.edit().putString("ghRepo", DEFAULT_REPO).apply();
                return DEFAULT_REPO;
            }
        }
        return r;
    }

    public void setUpdateRepo(String r) {
        sp.edit().putString("ghRepo", r == null || r.trim().isEmpty() ? DEFAULT_REPO : r.trim()).apply();
    }

    /** Token GitHub di sola lettura: serve perché il repository è privato. */
    public String githubToken() {
        return sp.getString("ghToken", "");
    }

    public void setGithubToken(String tok) {
        sp.edit().putString("ghToken", tok == null ? "" : tok.trim()).apply();
    }

    // --- navigatore predefinito: "auto" (telefono se collegato, altrimenti MG), "mg", "phone" o nome pacchetto ---
    public static final String NAV_AUTO = "auto", NAV_MG = "mg", NAV_PHONE = "phone";

    public String navApp() {
        return sp.getString("navApp", NAV_AUTO);
    }

    public void setNavApp(String v) {
        sp.edit().putString("navApp", v).apply();
    }

    // --- lingua: "auto" (lingua di sistema) o codice (it, en, es, de, pt, fr, tr) ---
    public String language() {
        return sp.getString("language", com.mg4.launcher.L.AUTO);
    }

    public void setLanguage(String code) {
        sp.edit().putString("language", code).apply();
    }

    // --- visualizzazione ---
    public boolean socDecimals() {
        return sp.getBoolean("socDecimals", false);
    }

    public void setSocDecimals(boolean b) {
        sp.edit().putBoolean("socDecimals", b).apply();
    }

    public boolean showTpms() {
        return sp.getBoolean("showTpms", true);
    }

    public void setShowTpms(boolean b) {
        sp.edit().putBoolean("showTpms", b).apply();
    }

    /** Popup discreto quando su GitHub c'è una versione nuova. */
    public boolean updatePopup() {
        return sp.getBoolean("updatePopup", true);
    }

    public void setUpdatePopup(boolean b) {
        sp.edit().putBoolean("updatePopup", b).apply();
    }

    /** Ultima versione per cui è stato premuto "Più tardi": per quella il popup non ricompare. */
    public String dismissedUpdate() {
        return sp.getString("dismissedUpdate", "");
    }

    public void setDismissedUpdate(String tag) {
        sp.edit().putString("dismissedUpdate", tag == null ? "" : tag).apply();
    }

    /** Dimensione dei comandi: 0 Normale, 1 Grande, 2 Molto grande. */
    public static final float[] UI_SCALES = {1f, 1.15f, 1.3f};

    public int uiScale() {
        return Math.max(0, Math.min(2, sp.getInt("dimensioneComandi", 0)));
    }

    public void setUiScale(int i) {
        sp.edit().putInt("dimensioneComandi", i).apply();
    }

    public float uiScaleFactor() {
        return UI_SCALES[uiScale()];
    }

    /** Posizione di guida: "auto" (come l'auto), "left", "right". */
    public static final String DRIVE_AUTO = "auto", DRIVE_LEFT = "left", DRIVE_RIGHT = "right";

    public String driveSide() {
        String v = sp.getString("posizioneGuida", DRIVE_AUTO);
        return DRIVE_LEFT.equals(v) || DRIVE_RIGHT.equals(v) ? v : DRIVE_AUTO;
    }

    public void setDriveSide(String v) {
        sp.edit().putString("posizioneGuida", v).apply();
    }
}
