package com.mg4.launcher.ui;

import android.content.Context;
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

import com.mg4.launcher.L;
import com.mg4.launcher.R;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.Updater;

/** Impostazioni del launcher (si aprono tenendo premuta l'auto o dalla griglia app). */
public final class SettingsView extends FrameLayout {

    public interface Host {
        void closeSettings();

        void settingsChanged(boolean rebuild);

        void openOriginalHome();

        Updater updater();

        com.mg4.launcher.svc.LogUploader logs();

        /** La lingua è cambiata: ricostruire tutta l'interfaccia. */
        void languageChanged();
    }

    private final Theme t;
    private final Prefs prefs;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());
    private TextView diag;
    private final LinearLayout colorRow, themeRow, navRow, langRow1, langRow2;
    private final TextView colorName;
    private TextView updStatus, updInstall, updCheck, logBtn, logStatus;
    private Updater.Release found;

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
        titleRow.addView(Ui.text(c, L.s(R.string.settings_title), 30, t.text, Ui.MEDIUM));
        col.addView(titleRow);

        col.addView(label(c, L.s(R.string.car_color)), top(22));
        colorRow = Ui.row(c);
        col.addView(colorRow, top(10));
        colorName = Ui.text(c, "", 18, t.sub, Ui.REGULAR);
        col.addView(colorName, top(8));

        LinearLayout roof = Ui.row(c);
        roof.addView(Ui.text(c, L.s(R.string.black_roof), 20, t.text, Ui.REGULAR), Ui.lpWeight(0, -2, 1));
        Switch sw = new Switch(c);
        sw.setChecked(prefs.blackRoof());
        sw.setOnCheckedChangeListener((b, on) -> {
            prefs.setBlackRoof(on);
            host.settingsChanged(false);
        });
        roof.addView(sw);
        col.addView(roof, top(18));

        col.addView(label(c, L.s(R.string.display)), top(24));
        col.addView(switchRow(c, L.s(R.string.soc_decimals), prefs.socDecimals(), on -> {
            prefs.setSocDecimals(on);
            host.settingsChanged(false);
        }), top(10));
        col.addView(switchRow(c, L.s(R.string.show_tpms), prefs.showTpms(), on -> {
            prefs.setShowTpms(on);
            host.settingsChanged(false);
        }), top(10));

        col.addView(label(c, L.s(R.string.theme)), top(24));
        themeRow = Ui.row(c);
        col.addView(themeRow, top(10));

        // dimensione dei comandi toccabili della home
        col.addView(label(c, L.s(R.string.set_ui_size)), top(24));
        LinearLayout sizeRow = Ui.row(c);
        int[] sizeNames = {R.string.ui_size_normal, R.string.ui_size_large, R.string.ui_size_xlarge};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            sizeRow.addView(choice(c, L.s(sizeNames[i]), prefs.uiScale() == i, v -> {
                if (prefs.uiScale() == idx) return;
                prefs.setUiScale(idx);
                host.settingsChanged(true);
            }), Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }
        col.addView(sizeRow, top(10));

        // posizione di guida: come l'auto, sinistra, destra
        col.addView(label(c, L.s(R.string.set_drive_side)), top(24));
        LinearLayout sideRow = Ui.row(c);
        boolean carRhd = com.mg4.launcher.svc.CarFeatures.isRightHandDrive(c);
        String carSide = L.s(carRhd ? R.string.right : R.string.left);
        String[][] sides = {{Prefs.DRIVE_AUTO, L.s(R.string.drive_auto) + " · " + carSide},
                {Prefs.DRIVE_LEFT, L.s(R.string.drive_left)}, {Prefs.DRIVE_RIGHT, L.s(R.string.drive_right)}};
        for (String[] sd : sides) {
            sideRow.addView(choice(c, sd[1], sd[0].equals(prefs.driveSide()), v -> {
                if (sd[0].equals(prefs.driveSide())) return;
                prefs.setDriveSide(sd[0]);
                host.settingsChanged(true);
            }), Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }
        col.addView(sideRow, top(10));

        col.addView(label(c, L.s(R.string.language)), top(24));
        langRow1 = Ui.row(c);
        col.addView(langRow1, top(10));
        langRow2 = Ui.row(c);
        col.addView(langRow2, top(10));
        col.addView(Ui.text(c, L.s(R.string.lang_auto_hint), 14, t.sub, Ui.REGULAR), top(8));

        col.addView(label(c, L.s(R.string.default_nav)), top(24));
        navRow = Ui.row(c);
        col.addView(navRow, top(10));
        col.addView(Ui.text(c, L.s(R.string.nav_auto_hint), 14, t.sub, Ui.REGULAR), top(8));

        TextView original = Ui.text(c, L.s(R.string.open_original_home), 19, t.text, Ui.MEDIUM);
        original.setGravity(Gravity.CENTER_VERTICAL);
        original.setPadding(dp(22), 0, dp(22), 0);
        original.setMinHeight(dp(56));
        original.setBackground(Ui.pressable(c, t.chip, 16, t));
        original.setClickable(true);
        original.setOnClickListener(v -> host.openOriginalHome());
        col.addView(original, top(28));

        buildUpdates(c, col);

        // --- colonna diagnostica ---
        LinearLayout right = Ui.column(c);
        LinearLayout rh = Ui.row(c);
        rh.addView(label(c, L.s(R.string.diag_title)), Ui.lpWeight(0, -2, 1));
        logBtn = button(c, L.s(R.string.save_log), false, v -> saveLog());
        logBtn.setMinHeight(dp(48));
        rh.addView(logBtn, Ui.margins(Ui.lp(-2, dp(48)), 0, 0, dp(10), 0));
        rh.addView(Ui.iconButton(c, t, R.drawable.ic_close, t.text, t.chip, 56, 26, 28, L.s(R.string.close), v -> host.closeSettings()));
        right.addView(rh);
        ScrollView ds = new ScrollView(c);
        ds.setBackground(Ui.round(t.bg, dp(16)));
        diag = Ui.text(c, "", 13, t.sub, Typeface.MONOSPACE);
        diag.setPadding(dp(16), dp(14), dp(16), dp(14));
        diag.setTextIsSelectable(true);
        ds.addView(diag);
        right.addView(ds, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(12), 0, 0));
        logStatus = Ui.text(c, L.s(R.string.save_log_hint), 14, t.sub, Ui.REGULAR);
        right.addView(logStatus, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, dp(10), 0, 0));
        card.addView(right, Ui.margins(Ui.lpWeight(0, -1, 1f), dp(32), 0, 0, 0));

        buildSwatches();
    }

    // ---- aggiornamenti da GitHub ----------------------------------------------------------------

    private void buildUpdates(Context c, LinearLayout col) {
        Updater up = host.updater();
        col.addView(label(c, L.s(R.string.updates)), top(30));
        col.addView(Ui.text(c, "MG4 Launcher " + up.currentVersion() + " · " + prefs.updateRepo(), 17, t.text, Ui.REGULAR), top(10));

        LinearLayout btns = Ui.row(c);
        updCheck = button(c, L.s(R.string.check_updates), false, v -> up.check(prefs.updateRepo(), prefs.githubToken()));
        btns.addView(updCheck, Ui.margins(Ui.lp(-2, dp(56)), 0, 0, dp(12), 0));
        updInstall = button(c, L.s(R.string.download_install), true, v -> up.install(found, prefs.githubToken()));
        updInstall.setVisibility(GONE);
        btns.addView(updInstall, Ui.lp(-2, dp(56)));
        col.addView(btns, top(12));

        updStatus = Ui.text(c, "", 16, t.sub, Ui.REGULAR);
        col.addView(updStatus, top(10));
        col.addView(switchRow(c, L.s(R.string.upd_popup_setting), prefs.updatePopup(), on -> {
            prefs.setUpdatePopup(on);
            if (on) prefs.setDismissedUpdate("");
        }), top(14));

        up.setListener((text, r, busy) -> {
            if (r != null) found = r;
            updStatus.setText(text);
            updCheck.setEnabled(!busy);
            updCheck.setAlpha(busy ? 0.5f : 1f);
            boolean canInstall = !busy && found != null && found.newer && found.apkUrl != null;
            updInstall.setVisibility(canInstall ? VISIBLE : GONE);
            if (canInstall) updInstall.setText(L.f(R.string.download_install_v, found.tag));
        });
        updStatus.setText(L.s(up.isBusy() ? R.string.busy : R.string.press_check));
    }

    private interface OnSwitch {
        void changed(boolean on);
    }

    /** Riga "testo ........ interruttore", come Tetto nero. */
    private View switchRow(Context c, String text, boolean checked, OnSwitch l) {
        LinearLayout r = Ui.row(c);
        r.addView(Ui.text(c, text, 20, t.text, Ui.REGULAR), Ui.lpWeight(0, -2, 1));
        Switch sw = new Switch(c);
        sw.setChecked(checked);
        sw.setOnCheckedChangeListener((b, on) -> l.changed(on));
        r.addView(sw);
        r.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return r;
    }

    private TextView button(Context c, String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(c, text, 18, primary ? t.onAccent : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(22), 0, dp(22), 0);
        b.setMinHeight(dp(56));
        b.setBackground(Ui.pressable(c, primary ? t.accent : t.chip, 16, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    /** Salva il log completo (in background) e mostra dove è finito. */
    private void saveLog() {
        logBtn.setEnabled(false);
        logBtn.setAlpha(0.5f);
        logStatus.setText(L.s(R.string.saving_log));
        Context app = getContext().getApplicationContext();
        new Thread(() -> {
            Diag.Saved r = Diag.saveLog(app);
            main.post(() -> {
                logBtn.setEnabled(true);
                logBtn.setAlpha(1f);
                if (r.paths.isEmpty()) {
                    logStatus.setText(L.s(R.string.log_failed));
                    return;
                }
                StringBuilder sb = new StringBuilder(L.s(r.onUsb ? R.string.log_saved_usb : R.string.log_saved)).append('\n');
                for (String path : r.paths) sb.append(path).append('\n');
                if (!r.onUsb) sb.append(L.s(R.string.log_usb_hint));
                logStatus.setText(sb.toString().trim());
            });
        }, "mg4-log").start();
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

        navRow.removeAllViews();
        String nav = prefs.navApp();
        String[][] navs = {{Prefs.NAV_AUTO, L.s(R.string.auto)}, {Prefs.NAV_MG, "MG"}, {Prefs.NAV_PHONE, L.s(R.string.app_phone)}};
        for (String[] m : navs) {
            boolean on = m[0].equals(nav);
            TextView b = Ui.text(c, m[1], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(22), 0, dp(22), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                prefs.setNavApp(m[0]);
                buildSwatches();
                host.settingsChanged(false);
            });
            navRow.addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }

        langRow1.removeAllViews();
        langRow2.removeAllViews();
        String lang = prefs.language();
        String[] codes = new String[L.CODES.length + 1];
        String[] names = new String[L.CODES.length + 1];
        codes[0] = L.AUTO;
        names[0] = L.f(R.string.lang_auto, L.nameOf(L.resolve(L.AUTO)));
        for (int i = 0; i < L.CODES.length; i++) {
            codes[i + 1] = L.CODES[i];
            names[i + 1] = L.NAMES[i];
        }
        for (int i = 0; i < codes.length; i++) {
            final String code = codes[i];
            boolean on = code.equals(lang);
            TextView b = Ui.text(c, names[i], 18, on ? t.panel : t.text, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(20), 0, dp(20), 0);
            b.setMinHeight(dp(52));
            b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
            b.setClickable(true);
            b.setOnClickListener(v -> {
                if (code.equals(prefs.language())) return;
                prefs.setLanguage(code);
                host.languageChanged();
            });
            (i < 4 ? langRow1 : langRow2).addView(b, Ui.margins(Ui.lp(-2, dp(52)), 0, 0, dp(10), 0));
        }

        themeRow.removeAllViews();
        String mode = prefs.themeMode();
        String[][] modes = {{Prefs.THEME_AUTO, L.s(R.string.auto)}, {Prefs.THEME_NIGHT, L.s(R.string.night)}, {Prefs.THEME_DAY, L.s(R.string.day)}};
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

    /** Pulsante di scelta come quelli del tema: selezionato = sfondo colore testo, scritta colore pannello. */
    private TextView choice(Context c, String text, boolean on, View.OnClickListener l) {
        TextView b = Ui.text(c, text, 18, on ? t.panel : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(22), 0, dp(22), 0);
        b.setMinHeight(dp(52));
        b.setBackground(on ? Ui.round(t.text, dp(14)) : Ui.pressable(c, t.chip, 14, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
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
            diag.setText(d.isEmpty() ? L.s(R.string.waiting_services) : d);
            if (isAttachedToWindow()) main.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacks(refreshDiag);
        host.updater().setListener(null);
    }
}
