package com.mg4.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

import com.mg4.launcher.R;

/**
 * La MG4 ricavata dalla foto: carrozzeria ricolorata (ombre × colore + riflessi), tetto nero
 * opzionale, tutto il resto (vetri, gomme, cerchi, plastiche, luci) originale.
 */
public final class CarView extends View {
    private static Bitmap base, shade, highlight, roofShade;
    private final Paint paintLayer = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint roofLayer = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint hlLayer = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint plain = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint floor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF dst = new RectF();
    private boolean blackRoof;
    private int glow, shadow;

    public CarView(Context c) {
        super(c);
        load(c);
        roofLayer.setColorFilter(new PorterDuffColorFilter(0xFF1A1B1E, PorterDuff.Mode.MULTIPLY));
        hlLayer.setAlpha(140);
    }

    private static synchronized void load(Context c) {
        if (base != null) return;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        base = BitmapFactory.decodeResource(c.getResources(), R.drawable.car_base, o);
        shade = BitmapFactory.decodeResource(c.getResources(), R.drawable.car_shade, o);
        highlight = BitmapFactory.decodeResource(c.getResources(), R.drawable.car_highlight, o);
        Bitmap roofMask = BitmapFactory.decodeResource(c.getResources(), R.drawable.car_roof, o);
        // ombre della vernice limitate alla zona del tetto
        roofShade = Bitmap.createBitmap(shade.getWidth(), shade.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(roofShade);
        cv.drawBitmap(shade, 0, 0, null);
        Paint p = new Paint();
        p.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        cv.drawBitmap(roofMask, 0, 0, p);
    }

    public void setLook(int bodyColor, boolean blackRoof, Theme t) {
        paintLayer.setColorFilter(new PorterDuffColorFilter(bodyColor, PorterDuff.Mode.MULTIPLY));
        this.blackRoof = blackRoof;
        glow = t.glow;
        shadow = t.shadow;
        floor.setShader(null);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0 || base == null) return;
        float bw = base.getWidth(), bh = base.getHeight();
        float s = Math.min(w / bw, (h * 0.9f) / bh);
        float dw = bw * s, dh = bh * s;
        float left = (w - dw) / 2f, top = (h - dh) / 2f - h * 0.02f;
        dst.set(left, top, left + dw, top + dh);

        // luce sul "pavimento" e ombra sotto l'auto
        float cy = dst.bottom - dh * 0.035f;
        if (floor.getShader() == null) {
            floor.setShader(new RadialGradient(w / 2f, cy, dw * 0.55f, glow, glow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        }
        c.save();
        c.scale(1f, 0.09f, w / 2f, cy);
        c.drawCircle(w / 2f, cy, dw * 0.55f, floor);
        c.restore();
        shadowPaint.setColor(shadow);
        c.drawOval(dst.left + dw * 0.06f, cy - dh * 0.025f, dst.right - dw * 0.06f, cy + dh * 0.025f, shadowPaint);

        c.drawBitmap(shade, null, dst, paintLayer);
        if (blackRoof) c.drawBitmap(roofShade, null, dst, roofLayer);
        c.drawBitmap(highlight, null, dst, hlLayer);
        c.drawBitmap(base, null, dst, plain);
    }
}
