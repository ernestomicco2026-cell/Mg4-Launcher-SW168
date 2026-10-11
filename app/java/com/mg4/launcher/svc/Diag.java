package com.mg4.launcher.svc;

import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostica: ultimi valori letti dai servizi ed errori recenti.
 * Si vede dalle impostazioni del launcher e su logcat (tag MG4L) — serve per la prima prova sull'auto.
 */
public final class Diag {
    private static final String TAG = "MG4L";
    private static final Map<String, String> VALUES = new TreeMap<>();
    private static final ArrayDeque<String> ERRORS = new ArrayDeque<>();

    private Diag() {}

    public static synchronized void put(String key, Object value) {
        String v = String.valueOf(value);
        String old = VALUES.put(key, v);
        if (!v.equals(old)) Log.d(TAG, key + " = " + v);
    }

    public static synchronized void error(String where, String msg) {
        String line = new SimpleDateFormat("HH:mm:ss", Locale.ITALY).format(new Date()) + "  " + where + ": " + msg;
        Log.w(TAG, line);
        if (!ERRORS.isEmpty() && ERRORS.peekLast().endsWith(where + ": " + msg)) return;
        ERRORS.addLast(line);
        while (ERRORS.size() > 40) ERRORS.removeFirst();
    }

    public static synchronized String dump() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : VALUES.entrySet()) {
            sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        if (!ERRORS.isEmpty()) {
            sb.append("\nERRORI RECENTI\n");
            for (String s : ERRORS) sb.append(s).append('\n');
        }
        return sb.toString();
    }
}
