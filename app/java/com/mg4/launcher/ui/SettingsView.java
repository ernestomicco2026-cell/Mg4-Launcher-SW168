package com.mg4.launcher.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mg4.launcher.L;
import com.mg4.launcher.R;
import com.mg4.launcher.svc.CarFeatures;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.LogUploader;
import com.mg4.launcher.svc.Updater;

/**
 * Impostazioni del launcher a schermo intero: a sinistra il menu delle sezioni (con versione e stato degli
 * aggiornamenti sempre visibili in basso), a destra il contenuto della sezione scelta. Con la guida a destra il
 * menu passa a destra. Interruttori, scelte e pulsanti seguono la "Dimensione comandi".
 */
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

    public static final int SEC_CAR = 0, SEC_HOME = 1, SEC_LOOK = 2, SEC_DRIVE = 3, SEC_SYSTEM = 4;
    private static final int[][] SECTIONS = {
            {R.drawable.ic_car, R.string.sec_car, R.string.sec_car_sub},
            {R.drawable.ic_home, R.string.sec_home, R.string.sec_home_sub},
            {R.drawable.ic_palette, R.string.sec_look, R.string.sec_look_sub},
            {R.drawable.ic_route, R.string.sec_drive, R.string.sec_drive_sub},
            {R.drawable.ic_gear, R.string.sec_system, R.string.sec_system_sub}};

    /** Sezione aperta: resta la stessa quando l'interfaccia si ricostruisce (tema, lingua, dimensione). */
    private static int current = SEC_CAR;
    private static long lastCheck;
    private static Updater.Release found;

    /** Sezione da mostrare alla prossima apertura (es. Sistema dal popup degli aggiornamenti). */
    public static void openAt(int section) {
        current = section;
    }

    private final Theme t;
    private final Prefs prefs;
    private final Host host;
    private final Handler main = new Handler(Looper.getMainLooper());

    private final LinearLayout menu;
    private final LinearLayout body;
    private final TextView sectionTitle;
    private TextView verStatus, verInstall;
    // sezione Sistema
    private TextView updHint, updCheck, updInstall, logHint, logBtn, logSaveHint, diagText;
    private String updText = "";
    private boolean updBusy;
    private boolean diagOpen;
    // sezione Auto
    private CarView preview;

    public SettingsView(Context c, Theme t, Prefs prefs, Host host) {
        super(c);
        this.t = t;
        this.prefs = prefs;
        this.host = host;
        setBackgroundColor(t.bg);
        setClickable(true);
        setPadding(dp(20), dp(20), dp(20), dp(20));

        boolean rhd = Prefs.DRIVE_RIGHT.equals(prefs.driveSide())
                || (Prefs.DRIVE_AUTO.equals(prefs.driveSide()) && CarFeatures.isRightHandDrive(c));

        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.FILL_VERTICAL);
        addView(row, new LayoutParams(-1, -1));

        // --- menu delle sezioni ---
        menu = Ui.column(c);
        menu.setBackground(Ui.round(t.panel, dp(24)));
        menu.setPadding(dp(18), dp(22), dp(18), dp(18));
        LinearLayout head = Ui.row(c);
        head.setPadding(dp(8), 0, 0, dp(14));
        head.addView(Ui.oneLine(Ui.text(c, L.s(R.string.app_settings), 30, t.text, Ui.MEDIUM)), Ui.lpWeight(0, -2, 1));
        head.addView(Ui.iconButton(c, t, R.drawable.ic_close, t.text, t.chip, 56, 26, 28, L.s(R.string.close), v -> host.closeSettings()));
        menu.addView(head, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout items = Ui.column(c);
        menu.addView(items, new LinearLayout.LayoutParams(-1, -2));
        for (int i = 0; i < SECTIONS.length; i++) {
            final int idx = i;
            // voci del menu: crescono con i comandi ma al massimo come "Grande", così il menu resta senza scorrere
            items.addView(menuItem(c, i), Ui.margins(new LinearLayout.LayoutParams(-1, dp(76 * Math.min(Ui.K, 1.15f))), 0, i == 0 ? 0 : dp(6), 0, 0));
            items.getChildAt(i).setOnClickListener(v -> select(idx));
        }
        menu.addView(Ui.spacer(c), Ui.lpWeight(-1, 0, 1));
        menu.addView(versionCard(c), new LinearLayout.LayoutParams(-1, -2));

        // --- contenuto della sezione ---
        LinearLayout content = Ui.column(c);
        content.setBackground(Ui.round(t.panel, dp(24)));
        content.setPadding(dp(32), dp(26), dp(32), dp(24));
        sectionTitle = Ui.text(c, "", 14, t.sub, Ui.MEDIUM);
        sectionTitle.setLetterSpacing(0.1f);
        content.addView(sectionTitle);
        ScrollView sv = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(OVER_SCROLL_NEVER);
        body = Ui.column(c);
        sv.addView(body, new FrameLayout.LayoutParams(-1, -2));
        content.addView(sv, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(16), 0, 0));

        if (!rhd) {
            row.addView(menu, new LinearLayout.LayoutParams(dp(420), -1));
            row.addView(content, Ui.margins(Ui.lpWeight(0, -1, 1), dp(16), 0, 0, 0));
        } else {
            row.addView(content, Ui.lpWeight(0, -1, 1));
            row.addView(menu, Ui.margins(new LinearLayout.LayoutParams(dp(420), -1), dp(16), 0, 0, 0));
        }

        // stato degli aggiornamenti: un solo ascoltatore per il menu e la sezione Sistema
        Updater up = host.updater();
        up.setListener((text, r, busy) -> {
            if (r != null) found = r;
            updText = text;
            updBusy = busy;
            refreshUpdates();
        });
        updText = L.s(up.isBusy() ? R.string.busy : R.string.press_check);
        updBusy = up.isBusy();
        if (!up.isBusy() && SystemClock.uptimeMillis() - lastCheck > 10 * 60_000L) {
            lastCheck = SystemClock.uptimeMillis();
            up.check(prefs.updateRepo(), prefs.githubToken());
        }
        select(current);
    }

    // =========================================================================================
    // Menu
    // =========================================================================================

    private View menuItem(Context c, int i) {
        LinearLayout it = Ui.row(c);
        it.setPadding(dp(18), 0, dp(14), 0);
        it.setClickable(true);
        FrameLayout box = new FrameLayout(c);
        box.addView(Ui.icon(c, SECTIONS[i][0], t.sub, 24), new FrameLayout.LayoutParams(tp(24), tp(24), Gravity.CENTER));
        it.addView(box, new LinearLayout.LayoutParams(tp(44), tp(44)));
        LinearLayout tx = Ui.column(c);
        tx.addView(Ui.oneLine(Ui.text(c, L.s(SECTIONS[i][1]), Ui.ts(21), t.text, Ui.MEDIUM)));
        tx.addView(Ui.oneLine(Ui.text(c, L.s(SECTIONS[i][2]), 14, t.sub, Ui.REGULAR)));
        it.addView(tx, Ui.margins(Ui.lpWeight(0, -2, 1), dp(16), 0, 0, 0));
        return it;
    }

    private void styleMenu() {
        LinearLayout items = (LinearLayout) menu.getChildAt(1);
        for (int i = 0; i < items.getChildCount(); i++) {
            boolean on = i == current;
            LinearLayout it = (LinearLayout) items.getChildAt(i);
            it.setBackground(on ? Ui.round(t.text, dp(18)) : Ui.pressable(getContext(), 0, 18, t));
            FrameLayout box = (FrameLayout) it.getChildAt(0);
            box.setBackground(Ui.round(on ? t.panel : t.chip, tp(14)));
            ((android.widget.ImageView) box.getChildAt(0)).setColorFilter(on ? t.text : t.sub);
            LinearLayout tx = (LinearLayout) it.getChildAt(1);
            ((TextView) tx.getChildAt(0)).setTextColor(on ? t.panel : t.text);
            ((TextView) tx.getChildAt(1)).setTextColor(on ? t.track : t.sub);
        }
    }

    /** Riquadro in basso nel menu: versione installata, stato degli aggiornamenti e "Installa". */
    private View versionCard(Context c) {
        LinearLayout card = Ui.row(c);
        card.setBackground(Ui.round(t.chip, dp(18)));
        card.setPadding(dp(18), dp(14), dp(14), dp(14));
        LinearLayout tx = Ui.column(c);
        tx.addView(Ui.oneLine(Ui.text(c, "MG4 Launcher " + host.updater().currentVersion(), 16, t.text, Ui.MEDIUM)));
        verStatus = Ui.oneLine(Ui.text(c, "", 14, t.sub, Ui.REGULAR));
        tx.addView(verStatus, Ui.margins(new LinearLayout.LayoutParams(-2, -2), 0, dp(2), 0, 0));
        card.addView(tx, Ui.lpWeight(0, -2, 1));
        verInstall = button(c, L.s(R.string.install), true, v -> install());
        verInstall.setMinHeight(tp(44));
        verInstall.setPadding(tp(18), 0, tp(18), 0);
        card.addView(verInstall, Ui.margins(Ui.lp(-2, tp(44)), dp(10), 0, 0, 0));
        return card;
    }

    private void select(int section) {
        current = section;
        diagOpen = false;
        main.removeCallbacks(refreshDiag);
        styleMenu();
        sectionTitle.setText(L.s(SECTIONS[section][1]).toUpperCase(L.locale()));
        body.removeAllViews();
        updHint = updCheck = updInstall = logHint = logBtn = logSaveHint = diagText = null;
        preview = null;
        Context c = getContext();
        switch (section) {
            case SEC_CAR: buildCar(c); break;
            case SEC_HOME: buildHome(c); break;
            case SEC_LOOK: buildLook(c); break;
            case SEC_DRIVE: buildDrive(c); break;
            default: buildSystem(c); break;
        }
        refreshUpdates();
    }

    // =========================================================================================
    // Sezioni
    // =========================================================================================

    private void buildCar(Context c) {
        LinearLayout two = Ui.row(c);
        two.setGravity(Gravity.TOP);
        LinearLayout left = Ui.column(c);
        int sel = prefs.carColorIndex();
        View colorRow = row(c, L.s(R.string.car_color_title), Prefs.CAR_COLORS[sel], null);
        left.addView(colorRow, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout sw = Ui.row(c);
        sw.setPadding(dp(4), dp(14), dp(4), dp(14));
        for (int i = 0; i < Prefs.CAR_COLORS.length; i++) {
            final int idx = i;
            sw.addView(swatch(c, Prefs.CAR_COLOR_VALUES[i], i == sel, Prefs.CAR_COLORS[i], v -> {
                prefs.setCarColorIndex(idx);
                host.settingsChanged(false);
                select(SEC_CAR);
            }), Ui.margins(Ui.lp(tp(60), tp(60)), i == 0 ? 0 : dp(14), 0, 0, 0));
        }
        left.addView(sw);
        left.addView(row(c, L.s(R.string.black_roof), null, toggle(c, prefs.blackRoof(), on -> {
            prefs.setBlackRoof(on);
            host.settingsChanged(false);
            if (preview != null) preview.setLook(Prefs.CAR_COLOR_VALUES[prefs.carColorIndex()], on, t);
        })), new LinearLayout.LayoutParams(-1, -2));
        two.addView(left, Ui.lpWeight(0, -2, 1.25f));

        FrameLayout pv = new FrameLayout(c);
        pv.setBackground(Ui.round(t.chip, dp(20)));
        pv.setPadding(dp(24), dp(24), dp(24), dp(24));
        preview = new CarView(c);
        preview.setLook(Prefs.CAR_COLOR_VALUES[sel], prefs.blackRoof(), t);
        pv.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        two.addView(pv, Ui.margins(Ui.lpWeight(0, dp(560), 1f), dp(24), 0, 0, 0));
        body.addView(two, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildHome(Context c) {
        add(row(c, L.s(R.string.soc_decimals), L.s(R.string.soc_decimals_hint), toggle(c, prefs.socDecimals(), on -> {
            prefs.setSocDecimals(on);
            host.settingsChanged(false);
        })));
        add(row(c, L.s(R.string.show_tpms), L.s(R.string.show_tpms_hint), toggle(c, prefs.showTpms(), on -> {
            prefs.setShowTpms(on);
            host.settingsChanged(false);
        })));
        add(row(c, L.s(R.string.show_inside_temp), L.s(R.string.show_inside_temp_hint), toggle(c, prefs.showInsideTemp(), on -> {
            prefs.setShowInsideTemp(on);
            host.settingsChanged(false);
        })));
    }

    private void buildLook(Context c) {
        String[] modes = {Prefs.THEME_AUTO, Prefs.THEME_NIGHT, Prefs.THEME_DAY};
        add(row(c, L.s(R.string.theme_title), null, seg(c,
                new String[]{L.s(R.string.auto), L.s(R.string.night), L.s(R.string.day)}, indexOf(modes, prefs.themeMode()), i -> {
                    if (modes[i].equals(prefs.themeMode())) return;
                    prefs.setThemeMode(modes[i]);
                    host.settingsChanged(true);
                })));
        add(row(c, L.s(R.string.ui_size_title), L.s(R.string.ui_size_hint), seg(c,
                new String[]{L.s(R.string.ui_size_normal), L.s(R.string.ui_size_large), L.s(R.string.ui_size_xlarge)}, prefs.uiScale(), i -> {
                    if (prefs.uiScale() == i) return;
                    prefs.setUiScale(i);
                    host.settingsChanged(true);
                })));
        add(row(c, L.s(R.string.language_title), L.s(R.string.lang_auto_hint), null));
        String[] codes = new String[L.CODES.length + 1];
        String[] names = new String[L.CODES.length + 1];
        codes[0] = L.AUTO;
        names[0] = L.f(R.string.lang_auto, L.nameOf(L.resolve(L.AUTO)));
        for (int i = 0; i < L.CODES.length; i++) {
            codes[i + 1] = L.CODES[i];
            names[i + 1] = L.NAMES[i];
        }
        // con i comandi grandi le lingue vanno su due righe
        int perRow = Ui.K > 1.05f ? 4 : codes.length;
        LinearLayout line = null;
        for (int i = 0; i < codes.length; i++) {
            if (i % perRow == 0) {
                line = Ui.row(c);
                body.addView(line, Ui.margins(new LinearLayout.LayoutParams(-2, -2), dp(4), dp(i == 0 ? 2 : 10), 0, 0));
            }
            final String code = codes[i];
            boolean on = code.equals(prefs.language());
            line.addView(chip(c, names[i], on, v -> {
                if (code.equals(prefs.language())) return;
                prefs.setLanguage(code);
                host.languageChanged();
            }), Ui.margins(Ui.lp(-2, tp(56)), i % perRow == 0 ? 0 : dp(10), 0, 0, 0));
        }
    }

    private void buildDrive(Context c) {
        boolean carRhd = CarFeatures.isRightHandDrive(c);
        String[] sides = {Prefs.DRIVE_AUTO, Prefs.DRIVE_LEFT, Prefs.DRIVE_RIGHT};
        add(row(c, L.s(R.string.drive_side_title), null, seg(c, new String[]{
                L.s(R.string.drive_auto) + " · " + L.s(carRhd ? R.string.right : R.string.left),
                L.s(R.string.left), L.s(R.string.right)}, indexOf(sides, prefs.driveSide()), i -> {
            if (sides[i].equals(prefs.driveSide())) return;
            prefs.setDriveSide(sides[i]);
            host.settingsChanged(true);
        })));
        String[] navs = {Prefs.NAV_AUTO, Prefs.NAV_MG, Prefs.NAV_PHONE};
        add(row(c, L.s(R.string.default_nav_title), L.s(R.string.nav_auto_hint), seg(c,
                new String[]{L.s(R.string.auto), "MG", L.s(R.string.app_phone)}, indexOf(navs, prefs.navApp()), i -> {
                    prefs.setNavApp(navs[i]);
                    host.settingsChanged(false);
                    select(SEC_DRIVE);
                })));
        add(row(c, L.s(R.string.original_home_title), L.s(R.string.original_home_hint),
                button(c, L.s(R.string.open), false, v -> host.openOriginalHome())));
    }

    private void buildSystem(Context c) {
        Updater up = host.updater();
        LinearLayout ub = Ui.row(c);
        updCheck = button(c, L.s(R.string.check), false, v -> {
            lastCheck = SystemClock.uptimeMillis();
            up.check(prefs.updateRepo(), prefs.githubToken());
        });
        ub.addView(updCheck, Ui.lp(-2, tp(56)));
        updInstall = button(c, L.s(R.string.install), true, v -> install());
        ub.addView(updInstall, Ui.margins(Ui.lp(-2, tp(56)), dp(10), 0, 0, 0));
        View ur = row(c, L.s(R.string.updates_title), "", ub);
        updHint = hintOf(ur);
        add(ur);
        add(row(c, L.s(R.string.upd_popup_setting), null, toggle(c, prefs.updatePopup(), on -> {
            prefs.setUpdatePopup(on);
            if (on) prefs.setDismissedUpdate("");
        })));
        View tg = toggle(c, host.logs().enabled(), on -> {
            host.logs().setEnabled(on);
            if (logHint != null) logHint.setText(logUploadText());
        });
        if (!LogUploader.configured()) {
            tg.setEnabled(false);
            tg.setAlpha(0.4f);
        }
        View lr = row(c, L.s(R.string.log_upload_on), logUploadText(), tg);
        logHint = hintOf(lr);
        add(lr);
        logBtn = button(c, L.s(R.string.save_log), false, v -> saveLog());
        View sr = row(c, L.s(R.string.log_full), L.s(R.string.log_full_hint), logBtn);
        logSaveHint = hintOf(sr);
        add(sr);
        add(row(c, L.s(R.string.diag_short), L.s(R.string.diag_hint), button(c, L.s(R.string.show), false, v -> showDiag())));
        main.removeCallbacks(refreshLogHint);
        main.postDelayed(refreshLogHint, 5000);
    }

    /** Diagnostica completa al posto delle righe di Sistema, con "Indietro". */
    private void showDiag() {
        Context c = getContext();
        diagOpen = true;
        body.removeAllViews();
        updHint = updCheck = updInstall = logHint = null;
        LinearLayout h = Ui.row(c);
        h.addView(button(c, L.s(R.string.back), false, v -> select(SEC_SYSTEM)), Ui.lp(-2, tp(56)));
        h.addView(Ui.text(c, L.s(R.string.diag_hint), 16, t.sub, Ui.REGULAR), Ui.margins(Ui.lpWeight(0, -2, 1), dp(16), 0, 0, 0));
        logBtn = button(c, L.s(R.string.save_log), false, v -> saveLog());
        h.addView(logBtn, Ui.lp(-2, tp(56)));
        body.addView(h, new LinearLayout.LayoutParams(-1, -2));
        diagText = Ui.text(c, "", 13, t.sub, Typeface.MONOSPACE);
        diagText.setPadding(dp(18), dp(14), dp(18), dp(14));
        diagText.setTextIsSelectable(true);
        diagText.setBackground(Ui.round(t.bg, dp(16)));
        body.addView(diagText, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, dp(14), 0, 0));
        logHint = Ui.text(c, "", 14, t.sub, Ui.REGULAR);
        body.addView(logHint, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, dp(10), 0, 0));
        main.removeCallbacks(refreshDiag);
        refreshDiag.run();
    }

    // =========================================================================================
    // Aggiornamenti e log
    // =========================================================================================

    private void install() {
        if (found == null || !found.newer || found.apkUrl == null) return;
        if (current != SEC_SYSTEM || diagOpen) select(SEC_SYSTEM);
        host.updater().install(found, prefs.githubToken());
    }

    private void refreshUpdates() {
        boolean canInstall = !updBusy && found != null && found.newer && found.apkUrl != null;
        if (verStatus != null) {
            String s;
            if (updBusy) s = updText;
            else if (found != null && found.newer) s = L.f(R.string.ver_new, found.tag);
            else if (found != null) s = L.s(R.string.ver_ok);
            else s = L.s(R.string.ver_unknown);
            verStatus.setText(s);
            verInstall.setVisibility(canInstall ? VISIBLE : GONE);
        }
        if (updHint != null) {
            updHint.setText(updText);
            updCheck.setEnabled(!updBusy);
            updCheck.setAlpha(updBusy ? 0.5f : 1f);
            updInstall.setVisibility(canInstall ? VISIBLE : GONE);
            if (canInstall) updInstall.setText(L.f(R.string.install_v, found.tag));
        }
    }

    private String logUploadText() {
        if (!LogUploader.configured()) return L.s(R.string.log_upload_nokey);
        if (!host.logs().enabled()) return L.s(R.string.log_upload_off);
        String st = LogUploader.status();
        if (st.startsWith("ok ")) return L.f(R.string.log_upload_last, st.substring(3));
        if (st.startsWith("errore ")) return L.f(R.string.log_upload_error, st.substring(7));
        return L.s(R.string.log_upload_never);
    }

    private final Runnable refreshLogHint = new Runnable() {
        @Override
        public void run() {
            if (current == SEC_SYSTEM && !diagOpen && logHint != null) logHint.setText(logUploadText());
            if (isAttachedToWindow() && current == SEC_SYSTEM && !diagOpen) main.postDelayed(this, 5000);
        }
    };

    /** Salva il log completo (in background) e mostra dove è finito; con l'invio attivo parte anche quello. */
    private void saveLog() {
        TextView btn = logBtn;
        if (btn == null) return;
        btn.setEnabled(false);
        btn.setAlpha(0.5f);
        TextView status = diagOpen ? logHint : logSaveHint;
        if (status != null) status.setText(L.s(R.string.saving_log));
        Context app = getContext().getApplicationContext();
        new Thread(() -> {
            Diag.Saved r = Diag.saveLog(app);
            main.post(() -> {
                btn.setEnabled(true);
                btn.setAlpha(1f);
                if (status == null) return;
                if (r.paths.isEmpty()) {
                    status.setText(L.s(R.string.log_failed));
                    return;
                }
                StringBuilder sb = new StringBuilder(L.s(r.onUsb ? R.string.log_saved_usb : R.string.log_saved)).append(' ');
                sb.append(r.paths.get(0));
                if (!r.onUsb) sb.append('\n').append(L.s(R.string.log_usb_hint));
                status.setText(sb.toString());
            });
        }, "mg4-log").start();
    }

    private final Runnable refreshDiag = new Runnable() {
        @Override
        public void run() {
            if (diagText == null || !diagOpen) return;
            String d = Diag.dump();
            diagText.setText(d.isEmpty() ? L.s(R.string.waiting_services) : d);
            if (isAttachedToWindow()) main.postDelayed(this, 1000);
        }
    };

    // =========================================================================================
    // Elementi grafici
    // =========================================================================================

    private interface OnSwitch {
        void changed(boolean on);
    }

    private interface OnPick {
        void picked(int index);
    }

    private void add(View v) {
        body.addView(v, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, body.getChildCount() == 0 ? 0 : dp(14), 0, 0));
    }

    /** Riga grande: titolo, spiegazione facoltativa e comando a destra. */
    private LinearLayout row(Context c, String title, String hint, View control) {
        LinearLayout r = Ui.row(c);
        r.setBackground(Ui.round(t.chip, dp(20)));
        r.setPadding(dp(24), dp(12), dp(20), dp(12));
        r.setMinimumHeight(tp(84));
        LinearLayout tx = Ui.column(c);
        tx.addView(Ui.text(c, title, 22, t.text, Ui.REGULAR));
        TextView h = Ui.text(c, hint == null ? "" : hint, 15, t.sub, Ui.REGULAR);
        h.setVisibility(hint == null ? GONE : VISIBLE);
        tx.addView(h, Ui.margins(new LinearLayout.LayoutParams(-2, -2), 0, dp(3), 0, 0));
        r.addView(tx, Ui.lpWeight(0, -2, 1));
        if (control != null) r.addView(control, Ui.margins(new LinearLayout.LayoutParams(-2, -2), dp(20), 0, 0, 0));
        return r;
    }

    private static TextView hintOf(View row) {
        return (TextView) ((LinearLayout) ((LinearLayout) row).getChildAt(0)).getChildAt(1);
    }

    /** Interruttore a pillola con i colori della home; segue la dimensione comandi. */
    private View toggle(Context c, boolean checked, OnSwitch l) {
        FrameLayout f = new FrameLayout(c);
        View knob = new View(c);
        FrameLayout.LayoutParams kl = new FrameLayout.LayoutParams(tp(32), tp(32), Gravity.CENTER_VERTICAL);
        f.addView(knob, kl);
        f.setClickable(true);
        f.setTag(checked);
        Runnable style = () -> {
            boolean on = (Boolean) f.getTag();
            f.setBackground(Ui.round(on ? t.text : t.track, tp(20)));
            knob.setBackground(Ui.round(on ? t.panel : t.sub, tp(16)));
            FrameLayout.LayoutParams p = (FrameLayout.LayoutParams) knob.getLayoutParams();
            p.gravity = Gravity.CENTER_VERTICAL | (on ? Gravity.END : Gravity.START);
            p.leftMargin = p.rightMargin = tp(4);
            knob.setLayoutParams(p);
        };
        style.run();
        f.setOnClickListener(v -> {
            boolean on = !(Boolean) f.getTag();
            f.setTag(on);
            style.run();
            l.changed(on);
        });
        f.setLayoutParams(new LinearLayout.LayoutParams(tp(72), tp(40)));
        return f;
    }

    /** Scelta tra più opzioni (tema, dimensione, lato di guida...). */
    private View seg(Context c, String[] labels, int sel, OnPick l) {
        LinearLayout s = Ui.row(c);
        s.setBackground(Ui.round(t.track, tp(16)));
        s.setPadding(dp(5), dp(5), dp(5), dp(5));
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            boolean on = i == sel;
            TextView b = Ui.text(c, labels[i], Ui.ts(18), on ? t.panel : t.sub, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setPadding(tp(22), 0, tp(22), 0);
            b.setBackground(on ? Ui.round(t.text, tp(12)) : Ui.pressable(c, 0, 12 * Ui.K, t));
            b.setClickable(true);
            b.setOnClickListener(v -> l.picked(idx));
            s.addView(b, Ui.margins(Ui.lp(-2, tp(52)), i == 0 ? 0 : dp(6), 0, 0, 0));
        }
        return s;
    }

    private TextView chip(Context c, String text, boolean on, View.OnClickListener l) {
        TextView b = Ui.text(c, text, Ui.ts(18), on ? t.panel : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(tp(24), 0, tp(24), 0);
        b.setBackground(on ? Ui.round(t.text, tp(16)) : Ui.pressable(c, t.chip, 16 * Ui.K, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    private TextView button(Context c, String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(c, text, Ui.ts(18), primary ? t.panel : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(tp(24), 0, tp(24), 0);
        b.setMinHeight(tp(56));
        b.setBackground(Ui.pressable(c, primary ? t.text : t.track, 16 * Ui.K, t));
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

    private static int indexOf(String[] a, String v) {
        for (int i = 0; i < a.length; i++) if (a[i].equals(v)) return i;
        return 0;
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    private int tp(float v) {
        return Ui.tp(getContext(), v);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        main.removeCallbacksAndMessages(null);
        host.updater().setListener(null);
    }
}
