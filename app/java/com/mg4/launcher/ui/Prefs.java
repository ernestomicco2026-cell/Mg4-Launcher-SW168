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
    public static final String DEFAULT_REPO = "ernestomicco2026-cell/mg4-launcher";

    public String updateRepo() {
        return sp.getString("ghRepo", DEFAULT_REPO);
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
}
