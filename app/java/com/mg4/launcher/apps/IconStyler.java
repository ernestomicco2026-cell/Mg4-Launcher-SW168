package com.mg4.launcher.apps;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import java.util.HashMap;
import java.util.Map;

/**
 * Ridisegna l'icona di un'app installata nello stile del launcher:
 *  1. icona "adattiva" → si usa il livello in primo piano (il simbolo) colorato in tinta unita;
 *  2. icona con sfondo trasparente → sagoma colorata in tinta unita;
 *  3. icona piena (simbolo su sfondo colorato) → si isola il simbolo dallo sfondo;
 *  4. se nessun metodo dà un risultato pulito → icona originale desaturata e arrotondata.
 */
public final class IconStyler {
    private static final int SIZE = 96;
    private static final Map<String, Bitmap> CACHE = new HashMap<>();

    private IconStyler() {}

    public static synchronized void clearCache() {
        CACHE.clear();
    }

    /** Restituisce una bitmap quadrata SIZE×SIZE: simbolo in {@code fg} su trasparente, oppure icona desaturata. */
    public static synchronized Bitmap style(String key, Drawable d, int fg) {
        String k = key + "#" + Integer.toHexString(fg);
        Bitmap cached = CACHE.get(k);
        if (cached != null) return cached;
        Bitmap out = null;
        try {
            if (d instanceof AdaptiveIconDrawable) {
                Drawable fgLayer = ((AdaptiveIconDrawable) d).getForeground();
                if (fgLayer != null) {
                    // il livello è 108dp con zona sicura di 72dp al centro: si ingrandisce di 1,5×
                    Bitmap raw = render(fgLayer, SIZE, 1.5f);
                    out = glyphFromAlpha(raw, fg);
                    if (!isClean(out)) out = null;
                }
            }
            if (out == null) {
                Bitmap raw = render(d, SIZE, 1f);
                float coverage = coverage(raw);
                if (coverage < 0.55f) {
                    out = glyphFromAlpha(raw, fg);
                } else {
                    out = glyphFromBackground(raw, fg);
                }
                if (!isClean(out)) out = desaturated(raw);
            }
        } catch (Throwable t) {
            out = null;
        }
        if (out == null) out = desaturated(render(d, SIZE, 1f));
        CACHE.put(k, out);
        return out;
    }

    private static Bitmap render(Drawable d, int size, float scale) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        int inset = Math.round(size * (1 - scale) / 2f);
        d.setBounds(inset, inset, size - inset, size - inset);
        d.draw(c);
        return b;
    }

    private static float coverage(Bitmap b) {
        int n = 0, tot = b.getWidth() * b.getHeight();
        int[] px = new int[tot];
        b.getPixels(px, 0, b.getWidth(), 0, 0, b.getWidth(), b.getHeight());
        for (int p : px) if (Color.alpha(p) > 128) n++;
        return n / (float) tot;
    }

    private static Bitmap glyphFromAlpha(Bitmap raw, int fg) {
        int w = raw.getWidth(), h = raw.getHeight();
        int[] px = new int[w * h];
        raw.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < px.length; i++) {
            int a = Color.alpha(px[i]);
            px[i] = (a << 24) | (fg & 0x00FFFFFF);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    /** Icona piena: colore di sfondo stimato dai bordi, il simbolo è ciò che se ne discosta. */
    private static Bitmap glyphFromBackground(Bitmap raw, int fg) {
        int w = raw.getWidth(), h = raw.getHeight();
        int[] px = new int[w * h];
        raw.getPixels(px, 0, w, 0, 0, w, h);
        // campiona l'anello interno (evita gli angoli arrotondati trasparenti)
        long r = 0, g = 0, b = 0;
        int n = 0;
        int m = w / 6;
        for (int i = m; i < w - m; i += 2) {
            int[] samples = {px[m * w + i], px[(h - 1 - m) * w + i], px[i * w + m], px[i * w + (w - 1 - m)]};
            for (int s : samples) {
                if (Color.alpha(s) < 200) continue;
                r += Color.red(s);
                g += Color.green(s);
                b += Color.blue(s);
                n++;
            }
        }
        if (n == 0) return null;
        int br = (int) (r / n), bgc = (int) (g / n), bb = (int) (b / n);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = Color.alpha(p);
            if (a < 60) {
                px[i] = 0;
                continue;
            }
            double dist = Math.sqrt(sq(Color.red(p) - br) + sq(Color.green(p) - bgc) + sq(Color.blue(p) - bb));
            int alpha = (int) Math.max(0, Math.min(255, (dist - 40) * 3.2));
            px[i] = ((alpha * a / 255) << 24) | (fg & 0x00FFFFFF);
        }
        return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
    }

    private static double sq(double v) {
        return v * v;
    }

    /** Un simbolo è "pulito" se copre una parte ragionevole della tessera. */
    private static boolean isClean(Bitmap b) {
        if (b == null) return false;
        float c = coverage(b);
        return c > 0.03f && c < 0.6f;
    }

    private static Bitmap desaturated(Bitmap raw) {
        int s = raw.getWidth();
        Bitmap out = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Path clip = new Path();
        clip.addRoundRect(0, 0, s, s, s * 0.24f, s * 0.24f, Path.Direction.CW);
        c.clipPath(clip);
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0.15f);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        p.setColorFilter(new ColorMatrixColorFilter(cm));
        c.drawBitmap(raw, 0, 0, p);
        return out;
    }

    public static boolean isGlyph(Bitmap b) {
        // le icone desaturate hanno pixel di colori diversi; i simboli sono monocromatici
        return monochrome(b);
    }

    private static boolean monochrome(Bitmap b) {
        if (b == null) return false;
        int first = -1;
        for (int y = 0; y < b.getHeight(); y += 7) {
            for (int x = 0; x < b.getWidth(); x += 7) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 40) continue;
                int rgb = p & 0x00FFFFFF;
                if (first == -1) first = rgb;
                else if (rgb != first) return false;
            }
        }
        return true;
    }
}
