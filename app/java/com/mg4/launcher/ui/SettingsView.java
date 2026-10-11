package com.mg4.launcher.ui;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.mg4.launcher.R;
import com.mg4.launcher.svc.Diag;

/** Impostazioni del launcher (si aprono tenendo premuta l'auto o dalla griglia app). */
public final class SettingsView extends FrameLayout {

    public interface Host {
        void closeSettings();

        void settingsChanged(boolean rebuild);

        void openOriginalHome();
    }

    private final Theme t;
    private final Prefs prefs;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView diag;
    private final LinearLayout colorRow, accentRow, themeRow;
    private final TextView colorName;

    public SettingsView(Context c, Theme t, Prefs prefs, Host host) {
        super(c);
        this.t = t;
        this.prefs = prefs;
        this.host = host;
        setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        setClickable(true);
        setOnClickListener(v -> host.closeSettings());

        LinearLayout card = Ui.row(c);
        card.setGravity(Gravity.TOP);
        card.setBackground(Ui.round(t.panel, dp(28)));
        card.setPadding(dp(36), dp(28), dp(28), dp(28));
        card.setClickable(true);
        LayoutParams lp = new LayoutParams(dp(1500), dp(580), Gravity.CENTER);
        addView(card, lp);

        // --- colonna impostazioni ---
        ScrollView left = new ScrollView(c);
        LinearLayout col = Ui.column(c);
        left.addView(col);
        card.addView(left, Ui.lpWeight(0, -1, 1.15f));

        LinearLayout titleRow = Ui.row(c);
        titleRow.addView(Ui.text(c, "Impostazioni launcher", 30, t.text, Ui.MEDIUM));
        col.addView(titleRow);

        col.addView(label(c, "COLORE AUTO"), top(22));
        colorRow = Ui.row(c);
        col.addView(colorRow, top(10));
        colorName = Ui.text(c, "", 18, t.sub, Ui.REGULAR);
        col.addView(colorName, top(8));

        LinearLayout roof = Ui.row(c);
        roof.addView(Ui.text(c, "Tetto nero", 20, t.text, Ui.REGULAR), Ui.lpWeight(0, -2, 1));
        Switch sw = new Switch(c);
        sw.setChecked(prefs.blackRoof());
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setBlackRoof(on);
            host.settingsChanged(false);
        });
        roof.addView(sw);
        col.addView(roof, top(18));

        col.addView(label(c, "COLORE D'ACCENTO"), top(24));
        accentRow = Ui.row(c);
        col.addView(accentRow, top(10));

        col.addView(label(c, "TEMA"), top(24));
        themeRow = Ui.row(c);
        col.addView(themeRow, top(10));

        TextView original = Ui.text(c, "Apri la home originale MG", 19, t.text, Ui.MEDIUM);
        original.setGravity(Gravity.CENTER_VERTICAL);
        original.setPadding(dp(22), 0, dp(22), 0);
        original.setMinHeight(dp(56));
        original.setBackground(Ui.pressable(c, t.chip, 16, t));
        original.setClickable(true);
        original.setOnClickListener(v -> host.openOriginalHome());
        col.addView(original, top(28));

        String version = "";
        try {
            PackageInfo pi = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            version = "MG4 Launcher " + pi.versionName;
        } catch (Exception ignored) {
        }
        col.addView(Ui.text(c, version, 15, t.sub, Ui.REGULAR), top(18));

        // --- colonna diagnostica ---
        LinearLayout right = Ui.column(c);
        LinearLayout rh = Ui.row(c);
        rh.addView(label(c, "DIAGNOSTICA SERVIZI AUTO"), Ui.lpWeight(0, -2, 1));
        rh.addView(Ui.iconButton(c, t, R.drawable.ic_close, t.text, t.chip, 56, 26, 28, "Chiudi", v -> host.closeSettings()));
        right.addView(rh);
        ScrollView ds = new ScrollView(c);
        ds.setBackground(Ui.round(t.bg, dp(16)));
        diag = Ui.text(c, "", 13, t.sub, Typeface.MONOSPACE);
        diag.setPadding(dp(16), dp(14), dp(16), dp(14));
        diag.setTextIsSelectable(true);
        ds.addView(diag);
        right.addView(ds, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(12), 0, 0));
        card.addView(right, Ui.margins(Ui.lpWeight(0, -1, 1f), dp(32), 0, 0, 0));

        buildSwatches();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        main.removeCallbacks(refreshDiag);
        refreshDiag.run();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private LinearLayout.LayoutParams top(int m) {
        return Ui.margins(new LinearLayout.LayoutParams(-2, -2), 0, dp(m), 0, 0);
    }

    private TextView label(Context c, String s) {
        TextView tv = Ui.text(c, s, 14, t.sub, Ui.MEDIUM);
        tv.setLetterSpacing(0.08f);
        return tv;
    }

    private void buildSwatches() {
        Context c = getContext();
        colorRow.removeAllViews();
        int sel = prefs.carColorIndex();
        for (int i = 0; i < Prefs.CAR_COLORS.length; i++) {
            final int idx = i;
            colorRow.addView(swatch(c, Prefs.CAR_COLOR_VALUES[i], i == sel, Prefs.CAR_COLORS[i], v -> {
                prefs.setCarColorIndex(idx);
                buildSwatches();
                host.settingsChanged(false);
            }), Ui.margins(Ui.lp(dp(56), dp(56)), 0, 0, dp(12), 0));
        }
        colorName.setText(Prefs.CAR_COLORS[sel]);

        accentRow.removeAllViews();
        int acc = prefs.accent();
        for (int a : Prefs.ACCENTS) {
            accentRow.addView(swatch(c, a, a == acc, "Accento", v -> {
                prefs.setAccent(a);
                host.settingsChanged(true);
            }), Ui.margins(Ui.lp(dp(56), dp(56)), 0, 0, dp(12), 0));
        }

        themeRow.removeAllViews();
        String mode = prefs.themeMode();
        String[][] modes = {{Prefs.THEME_AUTO, "Automatico"}, {Prefs.THEME_NIGHT, "Notte"}, {Prefs.THEME_DAY, "Giorno"}};
        for (String[] m : modes) {
            boolean on = m[0].equals(mode);
            TextView b = Ui.text(c, m[1], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(22), 0, dp(22), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                prefs.setThemeMode(m[0]);
                host.settingsChanged(true);
            });
            themeRow.addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }
    }

    private View swatch(Context c, int color, boolean selected, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        g.setStroke(dp(selected ? 4 : 1), selected ? t.text : t.track);
        f.setBackground(g);
        f.setContentDescription(desc);
        f.setClickable(true);
        f.setOnClickListener(l);
        return f;
    }

    private final Runnable refreshDiag = new Runnable() {
        @Override
        public void run() {
            String d = Diag.dump();
            diag.setText(d.isEmpty() ? "In attesa dei servizi dell'auto…" : d);
            if (isAttachedToWindow()) main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacks(refreshDiag);
    }
}
