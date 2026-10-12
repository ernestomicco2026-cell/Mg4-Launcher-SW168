package com.mg4.launcher.ui;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mg4.launcher.R;
import com.mg4.launcher.svc.MediaClient;
import com.mg4.launcher.svc.NavClient;
import com.mg4.launcher.svc.VehicleClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home in stile Tesla: auto + energia a sinistra, pannello Mappa/Media/CarPlay a destra, barra in basso. */
public final class HomeView extends LinearLayout {

    /** Azioni che la home chiede all'activity. */
    public interface Host {
        void openApps();

        void openSettings();

        void launch(String pkg, String cls);

        void launchNavigator();

        void launchNavigator(String which);

        java.util.List<com.mg4.launcher.apps.AppCatalog.App> otherNavApps();
    }

    private static final int TAB_MAP = 0, TAB_MEDIA = 1, TAB_PHONE = 2;

    private final Theme t;
    private final Prefs prefs;
    private final Host host;
    private final VehicleClient vehicle;
    private final MediaClient media;
    private final NavClient nav;

    // pannello auto
    private TextView rangeTv, socTv, statusTv, chipConsumo, chipLimite, chipV2l;
    private ImageView statusIcon;
    private Bars.Progress battery;
    private CarView car;

    // pannello destro
    private FrameLayout right;
    private View mapPanel, mediaPanel, phonePanel;
    private TextView tabMap, tabMedia, tabPhone;
    private int tab = TAB_MEDIA;
    private boolean userPickedTab;
    private boolean wasNavigating;

    // mappa
    private LinearLayout navIdle, navCard, navEta;
    private TextView navCardTitle, navCardSub, navEtaArrive, navEtaMin, navEtaKm;
    private TextView navHomeBtn, navOfficeBtn;
    private LinearLayout navWith;
    private boolean navWithPhone;

    // media
    private LinearLayout srcRow;
    private String srcSignature = "";
    private int pendingSource, pendingBand;
    private long pendingSourceUntil, pendingBandUntil;
    private TextView mTitle, mSub, mMeta, mPos, mDur, artFreq, artUnit, artBand;
    private ImageView artIcon, artLogo;
    private LinearLayout artRadio, progressRow, bandRow, favRow;
    private View favScroll;
    private String favSignature = "";
    private Bars.Progress mProgress;
    private ImageView playIcon, miniPlayIcon;
    private TextView[] bandChips;

    // telefono
    private TextView phoneTitle, phoneSub;

    // barra
    private TextView tempTv, miniTitle, miniSub;
    private ImageView miniArt;

    private int acTemp = -1;
    private int fanLevel = -1, fanMin = 1, fanMax = 11;
    private long localTempUntil, localFanUntil;
    private TextView fanTv, outTv;
    private ImageView outIcon;

    public HomeView(Context c, Theme t, Prefs prefs, Host host, VehicleClient vehicle, MediaClient media, NavClient nav) {
        super(c);
        this.t = t;
        this.prefs = prefs;
        this.host = host;
        this.vehicle = vehicle;
        this.media = media;
        this.nav = nav;
        setOrientation(VERTICAL);
        setBackgroundColor(t.bg);
        int pad = dp(20);
        setPadding(pad, pad, pad, pad);

        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.FILL_VERTICAL);
        addView(top, Ui.lpWeight(LayoutParams.MATCH_PARENT, 0, 1f));
        top.addView(buildCarPanel(c), new LayoutParams(dp(720), LayoutParams.MATCH_PARENT));
        right = buildRightPanel(c);
        top.addView(right, Ui.margins(Ui.lpWeight(0, LayoutParams.MATCH_PARENT, 1f), dp(16), 0, 0, 0));
        addView(buildBottomBar(c), Ui.margins(new LayoutParams(LayoutParams.MATCH_PARENT, dp(92)), 0, dp(16), 0, 0));

        applyCarLook();
        selectTab(TAB_MEDIA, false);
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    public void refreshNav() {
        buildNavWith(getContext(), navWithPhone);
    }

    public void applyCarLook() {
        car.setLook(Prefs.CAR_COLOR_VALUES[prefs.carColorIndex()], prefs.blackRoof(), t);
    }

    // =========================================================================================
    // Pannello auto
    // =========================================================================================

    private View buildCarPanel(Context c) {
        LinearLayout p = Ui.column(c);
        p.setBackground(Ui.pressable(c, t.panel, 24, t));
        p.setPadding(dp(32), dp(28), dp(32), dp(24));
        p.setClickable(true);
        p.setOnClickListener(v -> host.launch("com.saicmotor.hmi.vehiclesettings",
                "com.saicmotor.hmi.vehiclesettings.chargemanagement.ui.ChargeManagementActivity"));
        p.setOnLongClickListener(v -> {
            host.openSettings();
            return true;
        });

        LinearLayout head = Ui.row(c);
        head.setGravity(Gravity.BOTTOM);
        rangeTv = Ui.text(c, "—", 76, t.text, Ui.LIGHT);
        rangeTv.setLetterSpacing(-0.03f);
        head.addView(rangeTv);
        TextView km = Ui.text(c, "km", 26, t.sub, Ui.REGULAR);
        head.addView(km, Ui.margins(Ui.lp(-2, -2), dp(14), 0, 0, dp(8)));
        head.addView(Ui.spacer(c));
        socTv = Ui.text(c, "—", 30, t.text, Ui.MEDIUM);
        head.addView(socTv, Ui.margins(Ui.lp(-2, -2), 0, 0, 0, dp(8)));
        p.addView(head);

        battery = new Bars.Progress(c);
        battery.setColors(t.track, t.battery);
        p.addView(battery, Ui.margins(new LayoutParams(-1, dp(8)), 0, dp(16), 0, 0));

        LinearLayout status = Ui.row(c);
        statusIcon = Ui.icon(c, R.drawable.ic_bolt, t.battery, 22);
        status.addView(statusIcon);
        statusTv = Ui.oneLine(Ui.text(c, "Collegamento al veicolo…", 20, t.sub, Ui.REGULAR));
        status.addView(statusTv, Ui.margins(Ui.lpWeight(0, -2, 1), dp(10), 0, 0, 0));
        // temperatura esterna
        outIcon = Ui.icon(c, R.drawable.ic_thermo, t.sub, 22);
        outIcon.setVisibility(GONE);
        status.addView(outIcon);
        outTv = Ui.text(c, "", 20, t.sub, Ui.REGULAR);
        status.addView(outTv, Ui.margins(Ui.lp(-2, -2), dp(6), 0, 0, 0));
        p.addView(status, Ui.margins(new LayoutParams(-1, -2), 0, dp(14), 0, 0));

        car = new CarView(c);
        p.addView(car, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(4), 0, dp(4)));

        LinearLayout chips = Ui.row(c);
        chipConsumo = addChip(c, chips, "Consumo medio", true);
        chipLimite = addChip(c, chips, "Ricarica fino a", true);
        chipV2l = addChip(c, chips, "V2L", false);
        p.addView(chips, new LayoutParams(-1, -2));
        return p;
    }

    private TextView addChip(Context c, LinearLayout parent, String label, boolean gap) {
        LinearLayout ch = Ui.column(c);
        ch.setBackground(Ui.round(t.chip, dp(14)));
        ch.setPadding(dp(14), dp(12), dp(14), dp(12));
        ch.addView(Ui.oneLine(Ui.text(c, label, 14, t.sub, Ui.REGULAR)));
        TextView v = Ui.oneLine(Ui.text(c, "—", 20, t.text, Ui.MEDIUM));
        ch.addView(v, Ui.margins(new LayoutParams(-2, -2), 0, dp(4), 0, 0));
        parent.addView(ch, Ui.margins(Ui.lpWeight(0, -2, 1), 0, 0, gap ? dp(10) : 0, 0));
        return v;
    }

    private final long createdAt = System.currentTimeMillis();

    public void onVehicle(VehicleClient.State s) {
        if (!s.connected) {
            boolean early = System.currentTimeMillis() - createdAt < 12000;
            statusTv.setText(early ? "Collegamento al veicolo…" : "Servizio veicolo non disponibile");
            statusIcon.setImageResource(R.drawable.ic_info);
            statusIcon.setColorFilter(t.sub);
            return;
        }
        rangeTv.setText(s.rangeKm > 0 && s.rangeKm < 2000 ? String.valueOf(s.rangeKm) : "—");
        if (s.soc >= 0 && s.soc <= 100) {
            socTv.setText(Math.round(s.soc) + "%");
            battery.setValue(s.soc / 100f);
        }
        battery.setLimit(s.chargeLimit > 0 && s.chargeLimit < 100 ? s.chargeLimit / 100f : -1);
        statusTv.setText(chargeText(s));
        boolean charging = s.chargingStatus == 1 || s.chargingStatus == 10 || s.chargingStatus == 11 || s.chargingStatus == 16;
        statusIcon.setImageResource(s.chargingStatus <= 0 ? R.drawable.ic_car : (s.chargingStatus == 13 ? R.drawable.ic_plug : R.drawable.ic_bolt));
        statusIcon.setColorFilter(charging || s.chargingStatus == 2 || s.chargingStatus == 13 ? t.battery : t.sub);
        chipConsumo.setText(s.consumption > 0 && s.consumption < 80
                ? String.format(Locale.ITALY, "%.1f kWh/100", s.consumption) : "—");
        chipLimite.setText(s.chargeLimit > 0 && s.chargeLimit <= 100 ? s.chargeLimit + "%" : "—");
        chipV2l.setText(s.chargingStatus == 13 ? "Attivo" : "Spento");
        if (s.acTemp > 0 && SystemClock.uptimeMillis() > localTempUntil) {
            acTemp = s.acTemp;
            tempTv.setText(tempLabel(acTemp));
        }
        boolean outOk = s.outsideTemp > -60 && s.outsideTemp < 70;
        outIcon.setVisibility(outOk ? VISIBLE : GONE);
        outTv.setText(outOk ? String.format(Locale.ITALY, "%.0f° esterni", s.outsideTemp) : "");
        if (s.fanLevel >= 0 && SystemClock.uptimeMillis() > localFanUntil) {
            fanLevel = s.fanLevel;
            fanTv.setText(fanLabel(fanLevel));
        }
        fanMin = s.fanMin;
        fanMax = s.fanMax;
    }

    private String chargeText(VehicleClient.State s) {
        String kw = s.chargeKw > 0.3f ? String.format(Locale.ITALY, " · %.1f kW", s.chargeKw) : "";
        String eta = "";
        if (s.predictMinutes > 0 && s.predictMinutes < 24 * 60) {
            Date end = new Date(System.currentTimeMillis() + s.predictMinutes * 60000L);
            eta = " · piena alle " + new SimpleDateFormat("HH:mm", Locale.ITALY).format(end);
        }
        switch (s.chargingStatus) {
            case 1: return "In carica" + kw + eta;
            case 10:
            case 11: return "Ricarica rapida" + kw + eta;
            case 16: return "Ricarica wireless" + kw + eta;
            case 2: return "Ricarica completata";
            case 3: return "Bilanciamento batteria";
            case 4: return "Errore di ricarica";
            case 5: return "Collegamento in corso…";
            case 6: return "Collegato · non riconosciuto";
            case 7: return "Collegato · non in carica";
            case 8: return "Ricarica interrotta";
            case 9: return "Ricarica programmata";
            case 12: return "Ricarica multipla";
            case 13: return "V2L attivo · alimentazione esterna";
            case 14: return "V2L interrotto";
            case 15: return "V2L terminato";
            default: return "Pronta";
        }
    }

    // =========================================================================================
    // Pannello destro
    // =========================================================================================

    private FrameLayout buildRightPanel(Context c) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Ui.round(t.panel, dp(24)));
        f.setClipToOutline(true);

        mapPanel = buildMapPanel(c);
        mediaPanel = buildMediaPanel(c);
        phonePanel = buildPhonePanel(c);
        f.addView(mapPanel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(mediaPanel, new FrameLayout.LayoutParams(-1, -1));
        f.addView(phonePanel, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout tabs = Ui.row(c);
        tabs.setBackground(Ui.round(t.overlay, dp(16)));
        tabs.setPadding(dp(6), dp(6), dp(6), dp(6));
        tabMap = addTab(c, tabs, "Mappa", TAB_MAP);
        tabMedia = addTab(c, tabs, "Media", TAB_MEDIA);
        tabPhone = addTab(c, tabs, "CarPlay", TAB_PHONE);
        tabPhone.setVisibility(GONE);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(20), dp(20), 0);
        f.addView(tabs, lp);
        return f;
    }

    private TextView addTab(Context c, LinearLayout parent, String label, int which) {
        TextView tv = Ui.text(c, label, 18, t.sub, Ui.MEDIUM);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(22), 0, dp(22), 0);
        tv.setMinHeight(dp(48));
        tv.setClickable(true);
        tv.setOnClickListener(v -> selectTab(which, true));
        parent.addView(tv, Ui.margins(Ui.lp(-2, dp(48)), which == 0 ? 0 : dp(6), 0, 0, 0));
        return tv;
    }

    private void selectTab(int which, boolean byUser) {
        tab = which;
        if (byUser) userPickedTab = true;
        mapPanel.setVisibility(which == TAB_MAP ? VISIBLE : GONE);
        mediaPanel.setVisibility(which == TAB_MEDIA ? VISIBLE : GONE);
        phonePanel.setVisibility(which == TAB_PHONE ? VISIBLE : GONE);
        styleTab(tabMap, which == TAB_MAP);
        styleTab(tabMedia, which == TAB_MEDIA);
        styleTab(tabPhone, which == TAB_PHONE);
    }

    private void styleTab(TextView tv, boolean on) {
        tv.setBackground(on ? Ui.round(t.text, dp(12)) : Ui.pressable(getContext(), 0, 12, t));
        tv.setTextColor(on ? t.panel : t.sub);
    }

    // ---- mappa -------------------------------------------------------------------------------

    private View buildMapPanel(Context c) {
        FrameLayout f = new FrameLayout(c);
        Bars.MapDecor decor = new Bars.MapDecor(c);
        decor.setColors(t.map, t.road, t.roadMinor);
        f.addView(decor, new FrameLayout.LayoutParams(-1, -1));

        // nessuna guida: azioni rapide
        navIdle = Ui.column(c);
        navIdle.setGravity(Gravity.CENTER);
        TextView q = Ui.text(c, "Dove andiamo?", 34, t.text, Ui.MEDIUM);
        navIdle.addView(q);
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.CENTER);
        TextView open = pill(c, R.drawable.ic_nav, "Navigatore", true, v -> host.launchNavigator());
        navHomeBtn = pill(c, R.drawable.ic_home, "Casa", false, v -> nav.goHome());
        navOfficeBtn = pill(c, R.drawable.ic_work, "Lavoro", false, v -> nav.goOffice());
        btns.addView(open);
        btns.addView(navHomeBtn, Ui.margins(Ui.lp(-2, dp(64)), dp(14), 0, 0, 0));
        btns.addView(navOfficeBtn, Ui.margins(Ui.lp(-2, dp(64)), dp(14), 0, 0, 0));
        navIdle.addView(btns, Ui.margins(new LayoutParams(-2, -2), 0, dp(26), 0, 0));
        // apri con: MG / telefono (CarPlay, Android Auto) / navigatori installati (Waze, Google Maps...)
        navWith = Ui.row(c);
        navWith.setGravity(Gravity.CENTER);
        navIdle.addView(navWith, Ui.margins(new LayoutParams(-2, -2), 0, dp(22), 0, 0));
        buildNavWith(c, false);
        f.addView(navIdle, new FrameLayout.LayoutParams(-1, -1));

        // guida in corso: indicazione in alto a sinistra + arrivo in basso
        navCard = Ui.row(c);
        navCard.setBackground(Ui.round(t.overlay, dp(18)));
        navCard.setPadding(dp(22), dp(16), dp(26), dp(16));
        navCard.addView(Ui.icon(c, R.drawable.ic_turn, t.text, 44));
        LinearLayout txt = Ui.column(c);
        navCardTitle = Ui.oneLine(Ui.text(c, "", 30, t.text, Ui.MEDIUM));
        navCardSub = Ui.oneLine(Ui.text(c, "", 18, t.sub, Ui.REGULAR));
        txt.addView(navCardTitle);
        txt.addView(navCardSub, Ui.margins(new LayoutParams(-2, -2), 0, dp(4), 0, 0));
        navCard.addView(txt, Ui.margins(new LayoutParams(-2, -2), dp(18), 0, 0, 0));
        navCard.setClickable(true);
        navCard.setOnClickListener(v -> openNavigation());
        FrameLayout.LayoutParams cl = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.START);
        cl.setMargins(dp(20), dp(20), dp(20), 0);
        f.addView(navCard, cl);

        navEta = Ui.row(c);
        navEta.setBackground(Ui.round(t.overlay, dp(16)));
        navEta.setPadding(dp(18), dp(12), dp(18), dp(12));
        navEtaArrive = Ui.text(c, "", 18, t.text, Ui.MEDIUM);
        navEtaMin = Ui.text(c, "", 18, t.text, Ui.MEDIUM);
        navEtaKm = Ui.text(c, "", 18, t.text, Ui.MEDIUM);
        navEta.addView(navEtaArrive);
        navEta.addView(navEtaMin, Ui.margins(new LayoutParams(-2, -2), dp(22), 0, 0, 0));
        navEta.addView(navEtaKm, Ui.margins(new LayoutParams(-2, -2), dp(22), 0, 0, 0));
        FrameLayout.LayoutParams el = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.START);
        el.setMargins(dp(20), 0, 0, dp(20));
        f.addView(navEta, el);
        navCard.setVisibility(GONE);
        navEta.setVisibility(GONE);
        return f;
    }

    private TextView pill(Context c, int icon, String label, boolean primary, View.OnClickListener l) {
        TextView tv = Ui.text(c, label, 20, primary ? t.onAccent : t.text, Ui.MEDIUM);
        tv.setGravity(Gravity.CENTER_VERTICAL);
        tv.setPadding(dp(24), 0, dp(28), 0);
        tv.setMinHeight(dp(64));
        android.graphics.drawable.Drawable d = c.getDrawable(icon).mutate();
        d.setTint(primary ? t.onAccent : t.text);
        d.setBounds(0, 0, dp(26), dp(26));
        tv.setCompoundDrawables(d, null, null, null);
        tv.setCompoundDrawablePadding(dp(12));
        tv.setBackground(Ui.pressable(c, primary ? t.accent : t.overlay, 32, t));
        tv.setClickable(true);
        tv.setOnClickListener(l);
        return tv;
    }

    /** Riga "Apri con": piccoli pulsanti; tieni premuto per renderlo il navigatore predefinito. */
    private void buildNavWith(Context c, boolean phone) {
        navWithPhone = phone;
        navWith.removeAllViews();
        String pref = prefs.navApp();
        addNavChip(c, "MG", Prefs.NAV_MG, pref);
        if (phone) {
            addNavChip(c, media.last().deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay", Prefs.NAV_PHONE, pref);
        }
        for (com.mg4.launcher.apps.AppCatalog.App a : host.otherNavApps()) addNavChip(c, a.label, a.packageName, pref);
        navWith.setVisibility(navWith.getChildCount() > 1 ? VISIBLE : GONE);
    }

    private void addNavChip(Context c, String label, String value, String pref) {
        boolean def = value.equals(pref) || (Prefs.NAV_AUTO.equals(pref) && value.equals(navWithPhone ? Prefs.NAV_PHONE : Prefs.NAV_MG));
        TextView chip = Ui.text(c, label, 16, def ? t.text : t.sub, Ui.MEDIUM);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(18), 0, dp(18), 0);
        chip.setMinHeight(dp(44));
        chip.setBackground(Ui.pressable(c, def ? t.chip : t.overlay, 22, t));
        chip.setClickable(true);
        chip.setOnClickListener(v -> host.launchNavigator(value));
        chip.setOnLongClickListener(v -> {
            prefs.setNavApp(value);
            buildNavWith(getContext(), navWithPhone);
            return true;
        });
        navWith.addView(chip, Ui.margins(Ui.lp(-2, dp(44)), dp(5), 0, dp(5), 0));
    }

    private void openNavigation() {
        MediaClient.State m = media.last();
        if (m.deviceType != 0 && m.tbtActive) {
            media.openProjection();
            media.openRemoteNavigation();
        } else {
            host.launchNavigator();
        }
    }

    public void onNav(NavClient.State n) {
        MediaClient.State m = media.last();
        boolean remote = m.deviceType != 0 && m.tbtActive;
        boolean guiding = n.navigating || remote;
        navHomeBtn.setVisibility(n.connected && n.hasHome ? VISIBLE : GONE);
        navOfficeBtn.setVisibility(n.connected && n.hasOffice ? VISIBLE : GONE);
        navIdle.setVisibility(guiding ? GONE : VISIBLE);
        navCard.setVisibility(guiding ? VISIBLE : GONE);
        navEta.setVisibility(n.navigating && !remote && n.remainSeconds > 0 ? VISIBLE : GONE);
        if (remote) {
            navCardTitle.setText(m.tbtDistance.isEmpty() ? (m.deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay") : m.tbtDistance);
            navCardSub.setText(m.tbtRoad.isEmpty() ? "Navigazione dal telefono" : m.tbtRoad);
        } else if (n.navigating) {
            if (n.turnMeters >= 0) {
                navCardTitle.setText(km(n.turnMeters));
                String next = !n.turnText.isEmpty() ? n.turnText : n.road;
                navCardSub.setText(next.isEmpty() ? "Prossima manovra" : next);
            } else {
                navCardTitle.setText(n.road.isEmpty() ? "Navigazione in corso" : n.road);
                navCardSub.setText(n.remainMeters > 0 ? "Mancano " + km(n.remainMeters) : "Tocca per aprire il navigatore");
            }
            if (n.remainSeconds > 0) {
                Date arrive = new Date(System.currentTimeMillis() + n.remainSeconds * 1000L);
                navEtaArrive.setText(new SimpleDateFormat("HH:mm", Locale.ITALY).format(arrive) + " arrivo");
                navEtaMin.setText(Math.max(1, Math.round(n.remainSeconds / 60f)) + " min");
                navEtaKm.setText(n.remainMeters > 0 ? km(n.remainMeters) : "");
            }
        }
        // all'avvio di una guida mostra la mappa, a meno che l'utente non abbia scelto un'altra scheda
        if (guiding && !wasNavigating && (!userPickedTab || tab != TAB_PHONE)) selectTab(TAB_MAP, false);
        wasNavigating = guiding;
    }

    private static String km(int meters) {
        if (meters < 1000) return meters + " m";
        return String.format(Locale.ITALY, meters < 10000 ? "%.1f km" : "%.0f km", meters / 1000f);
    }

    // ---- media -------------------------------------------------------------------------------

    private View buildMediaPanel(Context c) {
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(28), dp(20), dp(36), dp(22));

        // sorgenti: un pulsante per ciascuna (Radio, USB, Bluetooth, Online, + CarPlay/Android Auto se collegato)
        srcRow = Ui.row(c);
        srcRow.setBackground(Ui.round(t.overlay, dp(16)));
        srcRow.setPadding(dp(6), dp(6), dp(6), dp(6));
        col.addView(srcRow, new LayoutParams(-2, dp(60)));

        // copertina / logo + informazioni + comandi
        LinearLayout main = Ui.row(c);
        main.setGravity(Gravity.CENTER_VERTICAL);
        col.addView(main, Ui.margins(Ui.lpWeight(-1, 0, 1), dp(8), dp(4), 0, 0));

        FrameLayout art = new FrameLayout(c);
        art.setBackground(Ui.pressable(c, t.chip, 26, t));
        art.setClipToOutline(true);
        art.setClickable(true);
        art.setContentDescription("Apri l'app della sorgente");
        art.setOnClickListener(v -> openCurrentSourceApp());
        artIcon = Ui.icon(c, R.drawable.ic_music, t.sub, 100);
        art.addView(artIcon, new FrameLayout.LayoutParams(dp(100), dp(100), Gravity.CENTER));
        artLogo = new ImageView(c);
        artLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artLogo.setPadding(dp(20), dp(20), dp(20), dp(20));
        art.addView(artLogo, new FrameLayout.LayoutParams(-1, -1));
        artRadio = Ui.column(c);
        artRadio.setGravity(Gravity.CENTER);
        artBand = Ui.text(c, "FM", 20, t.sub, Ui.MEDIUM);
        artBand.setLetterSpacing(0.12f);
        artBand.setGravity(Gravity.CENTER);
        artFreq = Ui.oneLine(Ui.text(c, "", 64, t.text, Ui.LIGHT));
        artFreq.setGravity(Gravity.CENTER);
        artUnit = Ui.oneLine(Ui.text(c, "MHz", 19, t.sub, Ui.REGULAR));
        artUnit.setGravity(Gravity.CENTER);
        artRadio.addView(artBand, new LayoutParams(-1, -2));
        artRadio.addView(artFreq, Ui.margins(new LayoutParams(-1, -2), 0, dp(2), 0, dp(2)));
        artRadio.addView(artUnit, new LayoutParams(-1, -2));
        artRadio.setPadding(dp(12), 0, dp(12), 0);
        art.addView(artRadio, new FrameLayout.LayoutParams(-1, -1));
        main.addView(art, new LayoutParams(dp(260), dp(260)));

        LinearLayout info = Ui.column(c);
        main.addView(info, Ui.margins(Ui.lpWeight(0, -2, 1), dp(40), 0, 0, 0));

        mMeta = Ui.oneLine(Ui.text(c, "", 17, t.sub, Ui.MEDIUM));
        mMeta.setLetterSpacing(0.06f);
        info.addView(mMeta, new LayoutParams(-1, -2));
        mTitle = Ui.oneLine(Ui.text(c, "", 46, t.text, Ui.MEDIUM));
        info.addView(mTitle, Ui.margins(new LayoutParams(-1, -2), 0, dp(4), 0, 0));
        mSub = Ui.oneLine(Ui.text(c, "", 21, t.sub, Ui.REGULAR));
        info.addView(mSub, Ui.margins(new LayoutParams(-1, -2), 0, dp(4), 0, 0));

        progressRow = Ui.column(c);
        mProgress = new Bars.Progress(c);
        mProgress.setColors(t.track, t.text);
        progressRow.addView(mProgress, new LayoutParams(-1, dp(6)));
        LinearLayout times = Ui.row(c);
        mPos = Ui.text(c, "", 16, t.sub, Ui.REGULAR);
        mDur = Ui.text(c, "", 16, t.sub, Ui.REGULAR);
        times.addView(mPos);
        times.addView(Ui.spacer(c));
        times.addView(mDur);
        progressRow.addView(times, Ui.margins(new LayoutParams(-1, -2), 0, dp(8), 0, 0));
        info.addView(progressRow, Ui.margins(new LayoutParams(-1, -2), 0, dp(16), 0, 0));

        // comandi grandi + scelta della banda (FM / DAB) subito accanto
        LinearLayout ctr = Ui.row(c);
        ctr.setGravity(Gravity.CENTER_VERTICAL);
        ctr.addView(Ui.iconButton(c, t, R.drawable.ic_prev, t.text, t.chip, 72, 30, 36, "Precedente", v -> media.skip(false)));
        FrameLayout play = Ui.iconButton(c, t, R.drawable.ic_pause, t.panel, t.text, 92, 38, 46, "Play o pausa", v -> media.playPause());
        playIcon = (ImageView) play.getChildAt(0);
        ctr.addView(play, Ui.margins(Ui.lp(dp(92), dp(92)), dp(20), 0, dp(20), 0));
        ctr.addView(Ui.iconButton(c, t, R.drawable.ic_next, t.text, t.chip, 72, 30, 36, "Successivo", v -> media.skip(true)));
        bandRow = Ui.row(c);
        bandRow.setBackground(Ui.round(t.chip, dp(16)));
        bandRow.setPadding(dp(5), dp(5), dp(5), dp(5));
        bandChips = new TextView[2];
        String[] bands = {"FM", "DAB"};
        int[] bandIds = {MediaClient.BAND_FM, MediaClient.BAND_DAB};
        for (int i = 0; i < 2; i++) {
            final int band = bandIds[i];
            TextView b = Ui.text(c, bands[i], 18, t.sub, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(dp(76));
            b.setPadding(dp(16), 0, dp(16), 0);
            b.setClickable(true);
            b.setContentDescription("Radio " + bands[i]);
            b.setOnClickListener(v -> {
                pendingBand = band;
                pendingBandUntil = SystemClock.uptimeMillis() + 4000;
                styleBands(band);
                media.switchBand(band);
            });
            bandRow.addView(b, Ui.margins(Ui.lp(-2, dp(54)), i == 0 ? 0 : dp(4), 0, 0, 0));
            bandChips[i] = b;
        }
        ctr.addView(bandRow, Ui.margins(Ui.lp(-2, dp(64)), dp(36), 0, 0, 0));
        info.addView(ctr, Ui.margins(new LayoutParams(-1, -2), 0, dp(20), 0, 0));

        // preferiti radio con i loghi (FM e DAB)
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        favRow = Ui.row(c);
        hs.addView(favRow);
        favScroll = hs;
        col.addView(hs, Ui.margins(new LayoutParams(-1, dp(122)), dp(8), dp(10), 0, 0));
        return col;
    }

    private void styleBands(int band) {
        int sel = band == MediaClient.BAND_DAB ? 1 : band == MediaClient.BAND_FM ? 0 : -1;
        for (int i = 0; i < bandChips.length; i++) {
            bandChips[i].setBackground(i == sel ? Ui.round(t.text, dp(12)) : Ui.pressable(getContext(), 0, 12, t));
            bandChips[i].setTextColor(i == sel ? t.panel : t.sub);
        }
    }

    private static String sourceLabel(int src) {
        switch (src) {
            case MediaClient.SRC_RADIO: return "Radio";
            case MediaClient.SRC_USB: return "USB";
            case MediaClient.SRC_BT: return "Bluetooth";
            case MediaClient.SRC_ONLINE: return "Online";
            case MediaClient.SRC_CARPLAY: return "CarPlay";
            case MediaClient.SRC_AAUTO: return "Android Auto";
            default: return "Media";
        }
    }

    /** Pulsanti delle sorgenti: si ricreano solo se cambia l'elenco, poi si aggiorna l'evidenziazione. */
    private void showSources(MediaClient.State m) {
        Context c = getContext();
        StringBuilder sig = new StringBuilder();
        for (int src : m.sources) sig.append(src).append(',');
        if (!sig.toString().equals(srcSignature)) {
            srcSignature = sig.toString();
            srcRow.removeAllViews();
            boolean compact = m.sources.size() > 4;
            for (int i = 0; i < m.sources.size(); i++) {
                final int src = m.sources.get(i);
                TextView b = Ui.text(c, sourceLabel(src), 17, t.sub, Ui.MEDIUM);
                b.setGravity(Gravity.CENTER_VERTICAL);
                b.setPadding(dp(compact ? 14 : 18), 0, dp(compact ? 16 : 22), 0);
                b.setCompoundDrawablePadding(dp(10));
                b.setClickable(true);
                b.setTag(src);
                b.setContentDescription(sourceLabel(src));
                b.setOnClickListener(v -> {
                    pendingSource = src;
                    pendingSourceUntil = SystemClock.uptimeMillis() + 3000;
                    styleSources(src);
                    media.selectSource(src);
                });
                srcRow.addView(b, Ui.margins(Ui.lp(-2, -1), i == 0 ? 0 : dp(4), 0, 0, 0));
            }
        }
        int shown = SystemClock.uptimeMillis() < pendingSourceUntil ? pendingSource : m.source;
        styleSources(shown);
    }

    private void styleSources(int selected) {
        for (int i = 0; i < srcRow.getChildCount(); i++) {
            TextView b = (TextView) srcRow.getChildAt(i);
            int src = (Integer) b.getTag();
            boolean on = src == selected;
            b.setBackground(on ? Ui.round(t.text, dp(12)) : Ui.pressable(getContext(), 0, 12, t));
            b.setTextColor(on ? t.panel : t.sub);
            android.graphics.drawable.Drawable d = getContext().getDrawable(sourceIcon(src)).mutate();
            d.setTint(on ? t.panel : t.sub);
            d.setBounds(0, 0, dp(22), dp(22));
            b.setCompoundDrawables(d, null, null, null);
        }
    }

    /** Riquadro di una stazione preferita: logo (o iniziali) + nome. */
    private View stationTile(Context c, MediaClient.Station st, boolean current) {
        LinearLayout tile = Ui.column(c);
        tile.setGravity(Gravity.CENTER_HORIZONTAL);
        tile.setClickable(true);
        tile.setContentDescription(st.label());
        tile.setBackground(Ui.pressable(c, 0, 16, t));
        tile.setOnClickListener(v -> media.tune(st));
        FrameLayout box = new FrameLayout(c);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(t.chip);
        g.setCornerRadius(dp(18));
        if (current) g.setStroke(dp(2), t.text);
        box.setBackground(g);
        box.setClipToOutline(true);
        if (st.logo != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(st.logo);
            iv.setPadding(dp(8), dp(8), dp(8), dp(8));
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView ini = Ui.text(c, initials(st), 18, t.text, Ui.MEDIUM);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        }
        TextView band = Ui.text(c, st.band == MediaClient.BAND_DAB ? "DAB" : "FM", 10, t.sub, Ui.MEDIUM);
        band.setPadding(dp(5), dp(1), dp(5), dp(1));
        band.setBackground(Ui.round(t.panel, dp(6)));
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        blp.setMargins(0, 0, dp(5), dp(5));
        box.addView(band, blp);
        tile.addView(box, new LayoutParams(dp(80), dp(80)));
        TextView name = Ui.oneLine(Ui.text(c, st.label(), 14, current ? t.text : t.sub, Ui.MEDIUM));
        name.setGravity(Gravity.CENTER);
        tile.addView(name, Ui.margins(new LayoutParams(dp(96), -2), 0, dp(6), 0, 0));
        tile.setPadding(dp(4), dp(4), dp(4), dp(4));
        return tile;
    }

    private static String initials(MediaClient.Station st) {
        String n = st.name.trim();
        if (n.isEmpty()) return st.label();
        String[] w = n.split("\\s+");
        if (w.length == 1) return w[0].length() <= 5 ? w[0].toUpperCase(Locale.ITALY) : w[0].substring(0, 4).toUpperCase(Locale.ITALY);
        return (w[0].substring(0, 1) + w[1].substring(0, 1)).toUpperCase(Locale.ITALY);
    }

    private void showFavorites(MediaClient.State m) {
        StringBuilder sig = new StringBuilder(m.stationKey);
        for (MediaClient.Station st : m.favorites) sig.append('|').append(st.key).append(st.logo != null ? '+' : '-');
        if (sig.toString().equals(favSignature)) return;
        favSignature = sig.toString();
        favRow.removeAllViews();
        for (MediaClient.Station st : m.favorites) {
            favRow.addView(stationTile(getContext(), st, st.key.equals(m.stationKey)), Ui.margins(new LayoutParams(-2, -2), 0, 0, dp(8), 0));
        }
    }

    private void openCurrentSourceApp() {
        MediaClient.State m = media.last();
        switch (m.source) {
            case MediaClient.SRC_RADIO:
                host.launch("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity");
                break;
            case MediaClient.SRC_ONLINE:
                host.launch("com.saicmotor.mediacenter", "com.saicmotor.mediacenter.spotify.MainActivity");
                break;
            case MediaClient.SRC_CARPLAY:
            case MediaClient.SRC_AAUTO:
                media.openProjection();
                break;
            default:
                host.launch("com.saicmotor.hmi.music", "com.saicmotor.hmi.music.ui.activity.MusicActivity");
                break;
        }
    }

    private static int sourceIcon(int src) {
        switch (src) {
            case MediaClient.SRC_USB: return R.drawable.ic_usb;
            case MediaClient.SRC_BT: return R.drawable.ic_bluetooth;
            case MediaClient.SRC_ONLINE: return R.drawable.ic_wave;
            case MediaClient.SRC_CARPLAY:
            case MediaClient.SRC_AAUTO: return R.drawable.ic_smartphone;
            default: return R.drawable.ic_radio;
        }
    }

    public void onMedia(MediaClient.State m) {
        boolean radio = m.source == MediaClient.SRC_RADIO;
        showSources(m);
        mTitle.setText(m.title);
        mSub.setText(m.subtitle);
        boolean logo = radio && m.logo != null;
        artLogo.setVisibility(logo ? VISIBLE : GONE);
        if (logo) artLogo.setImageBitmap(m.logo);
        artRadio.setVisibility(radio && !logo ? VISIBLE : GONE);
        artIcon.setVisibility(radio ? GONE : VISIBLE);
        artIcon.setImageResource(sourceIcon(m.source));
        if (radio) {
            boolean dab = m.radioBand == MediaClient.BAND_DAB;
            String band = dab ? "DAB+" : m.radioBand == MediaClient.BAND_AM ? "AM" : "FM";
            artBand.setText(band);
            artFreq.setText(dab ? m.title : m.radioFreq);
            artFreq.setTextSize(dab ? 30 : 56);
            artUnit.setText(m.radioUnit);
            mMeta.setText(dab ? band + (m.radioUnit.isEmpty() ? "" : " · " + m.radioUnit)
                    : band + " · " + m.radioFreq + " " + m.radioUnit);
            styleBands(SystemClock.uptimeMillis() < pendingBandUntil ? pendingBand : m.radioBand);
            showFavorites(m);
        } else {
            mMeta.setText(MediaClient.sourceName(m.source));
        }
        bandRow.setVisibility(radio ? VISIBLE : GONE);
        favScroll.setVisibility(radio && !m.favorites.isEmpty() ? VISIBLE : GONE);
        boolean hasProgress = !radio && m.durationMs > 0;
        progressRow.setVisibility(hasProgress ? VISIBLE : GONE);
        if (hasProgress) {
            long pos = Math.max(0, m.positionMs);
            mProgress.setValue(m.positionMs >= 0 ? pos / (float) m.durationMs : 0);
            mPos.setText(m.positionMs >= 0 ? time(pos) : "");
            mDur.setText(time(m.durationMs));
        }
        playIcon.setImageResource(m.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        miniPlayIcon.setImageResource(m.playing ? R.drawable.ic_pause : R.drawable.ic_play);
        miniTitle.setText(m.title);
        miniSub.setText(MediaClient.sourceName(m.source) + (m.subtitle.isEmpty() ? "" : " · " + m.subtitle));
        if (radio && m.logo != null) {
            miniArt.clearColorFilter();
            miniArt.setImageBitmap(m.logo);
        } else {
            miniArt.setColorFilter(t.sub, android.graphics.PorterDuff.Mode.SRC_IN);
            miniArt.setImageResource(sourceIcon(m.source));
        }

        // CarPlay / Android Auto: la scheda compare solo con il telefono collegato
        boolean phone = m.deviceType != 0;
        if (phone != navWithPhone) buildNavWith(getContext(), phone);
        tabPhone.setVisibility(phone ? VISIBLE : GONE);
        tabPhone.setText(m.deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay");
        if (!phone && tab == TAB_PHONE) selectTab(TAB_MEDIA, false);
        if (phone) {
            phoneTitle.setText(m.deviceName.isEmpty() ? "Telefono collegato" : m.deviceName);
            phoneSub.setText((m.deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay")
                    + " pronto. Tocca per aprire a schermo intero.");
        }
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(Locale.ITALY, "%02d", s % 60);
    }

    // ---- CarPlay / Android Auto --------------------------------------------------------------

    private View buildPhonePanel(Context c) {
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(40), dp(40), dp(40), dp(40));
        col.addView(Ui.icon(c, R.drawable.ic_smartphone, t.sub, 84));
        phoneTitle = Ui.text(c, "Telefono collegato", 30, t.text, Ui.MEDIUM);
        phoneTitle.setGravity(Gravity.CENTER);
        col.addView(phoneTitle, Ui.margins(new LayoutParams(-2, -2), 0, dp(18), 0, 0));
        phoneSub = Ui.text(c, "", 20, t.sub, Ui.REGULAR);
        phoneSub.setGravity(Gravity.CENTER);
        col.addView(phoneSub, Ui.margins(new LayoutParams(-2, -2), 0, dp(12), 0, 0));
        TextView open = Ui.text(c, "Apri", 20, t.onAccent, Ui.MEDIUM);
        open.setGravity(Gravity.CENTER);
        open.setPadding(dp(40), 0, dp(40), 0);
        open.setMinHeight(dp(60));
        open.setBackground(Ui.pressable(c, t.accent, 30, t));
        open.setClickable(true);
        open.setOnClickListener(v -> media.openProjection());
        col.addView(open, Ui.margins(new LayoutParams(-2, dp(60)), 0, dp(26), 0, 0));
        return col;
    }

    // =========================================================================================
    // Barra in basso
    // =========================================================================================

    private View buildBottomBar(Context c) {
        LinearLayout bar = Ui.row(c);
        bar.setBackground(Ui.round(t.panel, dp(24)));
        bar.setPadding(dp(16), 0, dp(16), 0);

        bar.addView(Ui.iconButton(c, t, R.drawable.ic_chev_left, t.sub, 0, 56, 26, 28, "Diminuisci temperatura",
                v -> changeTemp(-1)));
        tempTv = Ui.text(c, "—", 32, t.text, Ui.REGULAR);
        tempTv.setGravity(Gravity.CENTER);
        bar.addView(tempTv, new LayoutParams(dp(108), -2));
        bar.addView(Ui.iconButton(c, t, R.drawable.ic_chev_right, t.sub, 0, 56, 26, 28, "Aumenta temperatura",
                v -> changeTemp(1)));
        bar.addView(Ui.divider(c, t.track, 48));

        // ventola: ‹ livello ›, tocca l'icona = app Clima, tieni premuto = accendi/spegni il clima
        bar.addView(Ui.iconButton(c, t, R.drawable.ic_chev_left, t.sub, 0, 56, 26, 28, "Ventola meno",
                v -> changeFan(-1)));
        LinearLayout fanBox = Ui.row(c);
        fanBox.setGravity(Gravity.CENTER);
        ImageView fanIc = Ui.icon(c, R.drawable.ic_fan, t.accent, 26);
        fanBox.addView(fanIc, new LayoutParams(dp(26), dp(26)));
        fanTv = Ui.text(c, "—", 26, t.text, Ui.REGULAR);
        fanTv.setGravity(Gravity.CENTER);
        fanBox.addView(fanTv, Ui.margins(new LayoutParams(-2, -2), dp(8), 0, 0, 0));
        fanBox.setMinimumWidth(dp(104));
        fanBox.setClickable(true);
        fanBox.setContentDescription("Clima");
        fanBox.setBackground(Ui.pressable(c, 0, 16, t));
        fanBox.setOnClickListener(v -> host.launch("com.saicmotor.hmi.hvac", "com.saicmotor.hmi.hvac.HvacActivity"));
        fanBox.setOnLongClickListener(v -> {
            vehicle.setHvacPower(vehicle.last().hvacPower != 1);
            return true;
        });
        bar.addView(fanBox, new LayoutParams(-2, dp(56)));
        bar.addView(Ui.iconButton(c, t, R.drawable.ic_chev_right, t.sub, 0, 56, 26, 28, "Ventola più",
                v -> changeFan(1)));

        bar.addView(Ui.divider(c, t.track, 48));

        LinearLayout mini = Ui.row(c);
        mini.setPadding(dp(8), 0, dp(8), 0);
        miniArt = Ui.icon(c, R.drawable.ic_radio, t.sub, 30);
        FrameLayout artBox = new FrameLayout(c);
        artBox.setBackground(Ui.round(t.chip, dp(12)));
        artBox.addView(miniArt, new FrameLayout.LayoutParams(dp(30), dp(30), Gravity.CENTER));
        mini.addView(artBox, new LayoutParams(dp(56), dp(56)));
        LinearLayout mt = Ui.column(c);
        miniTitle = Ui.oneLine(Ui.text(c, "", 19, t.text, Ui.MEDIUM));
        miniSub = Ui.oneLine(Ui.text(c, "", 16, t.sub, Ui.REGULAR));
        mt.addView(miniTitle);
        mt.addView(miniSub, Ui.margins(new LayoutParams(-2, -2), 0, dp(4), 0, 0));
        mini.addView(mt, Ui.margins(Ui.lpWeight(0, -2, 1), dp(14), 0, dp(8), 0));
        mini.setClickable(true);
        mini.setOnClickListener(v -> selectTab(TAB_MEDIA, true));
        FrameLayout mp = Ui.iconButton(c, t, R.drawable.ic_pause, t.text, 0, 56, 28, 28, "Play o pausa", v -> media.playPause());
        miniPlayIcon = (ImageView) mp.getChildAt(0);
        mini.addView(mp);
        mini.addView(Ui.iconButton(c, t, R.drawable.ic_next, t.text, 0, 56, 26, 28, "Successivo", v -> media.skip(true)));
        bar.addView(mini, Ui.lpWeight(0, -1, 1));

        bar.addView(Ui.divider(c, t.track, 48));

        bar.addView(shortcut(c, R.drawable.ic_phone, "Telefono", v -> host.launch("com.saicmotor.hmi.btcall", "com.saicmotor.hmi.btcall.BtCallActivity")));
        bar.addView(shortcut(c, R.drawable.ic_radio, "Radio", v -> host.launch("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity")));
        bar.addView(shortcut(c, R.drawable.ic_cam360, "Telecamera 360", v -> host.launch("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.AVMActivity")));
        bar.addView(shortcut(c, R.drawable.ic_gear, "Impostazioni", v -> host.launch("com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.systemsettings.SettingsActivity")));
        // le app si aprono scorrendo la home verso sinistra: niente pulsante dedicato
        return bar;
    }

    private View shortcut(Context c, int icon, String desc, View.OnClickListener l) {
        FrameLayout f = Ui.iconButton(c, t, icon, t.text, 0, 68, 30, 18, desc, l);
        f.setLayoutParams(Ui.margins(Ui.lp(dp(68), dp(68)), dp(8), 0, 0, 0));
        return f;
    }

    private static String tempLabel(int v) {
        if (v <= VehicleClient.TEMP_MIN) return "LO";
        if (v >= VehicleClient.TEMP_MAX) return "HI";
        return v + "°";
    }

    private void changeTemp(int delta) {
        if (acTemp <= 0) acTemp = vehicle.last().acTemp;
        if (acTemp <= 0) return;
        acTemp = Math.max(VehicleClient.TEMP_MIN, Math.min(VehicleClient.TEMP_MAX, acTemp + delta));
        localTempUntil = SystemClock.uptimeMillis() + 4000;
        tempTv.setText(tempLabel(acTemp));
        vehicle.setDriverTemp(acTemp);
    }

    private static String fanLabel(int v) {
        if (v == VehicleClient.FAN_AUTO) return "A";
        if (v <= 0) return "OFF";
        return String.valueOf(v);
    }

    /** Come l'originale: da spenta o automatico il primo tocco parte dal minimo/massimo. */
    private void changeFan(int delta) {
        if (fanLevel < 0) fanLevel = vehicle.last().fanLevel;
        int cur = fanLevel;
        int next;
        if (cur == VehicleClient.FAN_AUTO) next = delta > 0 ? fanMax : fanMax - 1;
        else if (cur <= 0) next = delta > 0 ? fanMin : 0;
        else next = cur + delta;
        if (next < fanMin) next = VehicleClient.FAN_OFF;
        if (next > fanMax) next = fanMax;
        if (next == cur) return;
        fanLevel = next;
        localFanUntil = SystemClock.uptimeMillis() + 4000;
        fanTv.setText(fanLabel(fanLevel));
        vehicle.setFanLevel(fanLevel);
    }
}
