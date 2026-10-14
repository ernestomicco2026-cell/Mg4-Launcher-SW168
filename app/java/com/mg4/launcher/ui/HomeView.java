package com.mg4.launcher.ui;

import android.content.Context;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mg4.launcher.L;
import com.mg4.launcher.R;
import com.mg4.launcher.svc.CarFeatures;
import com.mg4.launcher.svc.MediaClient;
import com.mg4.launcher.svc.NavClient;
import com.mg4.launcher.svc.VehicleClient;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Home in stile Tesla (MG4, SWI68): auto + energia a sinistra, pannello Mappa/Media/CarPlay a destra, barra in basso. */
public final class HomeView extends LinearLayout {

    /** Azioni che la home chiede all'activity. */
    public interface Host {
        void openApps();

        void openSettings();

        void launch(String pkg, String cls);

        /** Gestione energia dell'auto su una scheda: "ChargeManagementFragment" o "DischargeManagementFragment" (V2L). */
        void openEnergy(String module);

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
    private TextView clockTv;
    private TextView acBtn, autoBtn;
    private FrameLayout frontFogBtn, rearFogBtn;
    private TextView rangeTv, socTv, statusTv, chipConsumo, chipLimite, chipV2l;
    private ImageView statusIcon;
    private Bars.Progress battery;
    private CarView car;

    // pannello destro
    private FrameLayout right;
    private View mapPanel, mediaPanel, phonePanel;
    private TextView tabMap, tabMedia, tabPhone;
    private LinearLayout connRow;
    private ImageView connBt, connWifi;
    private TextView connNet;
    private Bars.Signal connBars;
    private int tab = TAB_MEDIA;
    private boolean userPickedTab;
    private boolean wasNavigating;

    // mappa
    private LinearLayout navIdle, navCard, navEta;
    private TextView navCardTitle, navCardSub, navEtaArrive, navEtaMin, navEtaKm;
    private View navHomeBtn, navOfficeBtn;
    private LinearLayout navWith, navWithBox, navPlaces;
    private TextView navDefaultTv;
    private boolean navWithPhone;

    // media
    private LinearLayout srcRow, stationPanel, stationList, stationEmpty;
    private android.widget.ScrollView stationScroll;
    private TextView stationTitle, stationCount;
    private String stationSignature = "";
    private int stationBand = -1;
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
    private TextView fanTv, outTv, inTv;
    private LinearLayout tpmsRow, tpmsFront, tpmsRear;
    private FrameLayout seatLeftBtn, seatRightBtn, wheelBtn;
    private ImageView outIcon;

    /** Spazio lasciato libero in alto a sinistra quando la barra di sistema disegna la spia luci. */
    private static final int SYSTEM_LIGHT_INSET = 64;
    /** Home specchiata (guida a destra) e spia luci di sistema presente. */
    private final boolean rhd, sysIndicator;

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

        // guida a destra: dall'impostazione, oppure come l'auto
        boolean carRhd = CarFeatures.isRightHandDrive(c);
        String side = prefs.driveSide();
        rhd = Prefs.DRIVE_RIGHT.equals(side) || (Prefs.DRIVE_AUTO.equals(side) && carRhd);
        sysIndicator = CarFeatures.hasSystemLightIndicator(c) || carRhd;

        LinearLayout top = Ui.row(c);
        top.setGravity(Gravity.FILL_VERTICAL);
        addView(top, Ui.lpWeight(LayoutParams.MATCH_PARENT, 0, 1f));
        View carPanel = buildCarPanel(c);
        right = buildRightPanel(c);
        if (!rhd) {
            top.addView(carPanel, new LayoutParams(dp(720), LayoutParams.MATCH_PARENT));
            top.addView(right, Ui.margins(Ui.lpWeight(0, LayoutParams.MATCH_PARENT, 1f), dp(16), 0, 0, 0));
        } else {
            // auto e clima dal lato del guidatore
            top.addView(right, Ui.lpWeight(0, LayoutParams.MATCH_PARENT, 1f));
            top.addView(carPanel, Ui.margins(new LayoutParams(dp(720), LayoutParams.MATCH_PARENT), dp(16), 0, 0, 0));
        }
        addView(buildBottomBar(c), Ui.margins(new LayoutParams(LayoutParams.MATCH_PARENT, tp(100)), 0, dp(14), 0, 0));

        applyCarLook();
        selectTab(TAB_MEDIA, false);
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    /** Misura di un comando toccabile (cresce con la dimensione comandi). */
    private int tp(float v) {
        return Ui.tp(getContext(), v);
    }

    /** Testo di un comando toccabile. */
    private static float ts(float sp) {
        return Ui.ts(sp);
    }

    /** True se la home è specchiata per la guida a destra. */
    public boolean isRightHand() {
        return rhd;
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
        p.setOnClickListener(v -> host.openEnergy("ChargeManagementFragment"));
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
        // orologio, grande quanto l'autonomia
        clockTv = Ui.text(c, "", 76, t.text, Ui.LIGHT);
        clockTv.setLetterSpacing(-0.03f);
        head.addView(clockTv);
        // spia luci del sistema in alto a sinistra: l'autonomia parte più a destra
        if (sysIndicator && !rhd) head.setPadding(dp(SYSTEM_LIGHT_INSET), 0, 0, 0);
        p.addView(head);

        LinearLayout batRow = Ui.row(c);
        batRow.setGravity(Gravity.CENTER_VERTICAL);
        battery = new Bars.Progress(c);
        battery.setColors(t.track, t.battery);
        batRow.addView(battery, Ui.lpWeight(0, dp(8), 1));
        socTv = Ui.text(c, "—", 24, t.text, Ui.MEDIUM);
        batRow.addView(socTv, Ui.margins(Ui.lp(-2, -2), dp(16), 0, 0, 0));
        p.addView(batRow, Ui.margins(new LayoutParams(-1, -2), 0, dp(10), 0, 0));
        updateClock();

        LinearLayout status = Ui.row(c);
        statusIcon = Ui.icon(c, R.drawable.ic_bolt, t.battery, 22);
        status.addView(statusIcon);
        statusTv = Ui.oneLine(Ui.text(c, L.s(R.string.connecting_vehicle), 20, t.sub, Ui.REGULAR));
        status.addView(statusTv, Ui.margins(Ui.lpWeight(0, -2, 1), dp(10), 0, 0, 0));
        // temperatura esterna e, sotto, quella interna
        outIcon = Ui.icon(c, R.drawable.ic_thermo, t.sub, 22);
        outIcon.setVisibility(GONE);
        status.addView(outIcon);
        LinearLayout temps = Ui.column(c);
        temps.setGravity(Gravity.END);
        outTv = Ui.text(c, "", 20, t.sub, Ui.REGULAR);
        temps.addView(outTv);
        inTv = Ui.text(c, "", 17, t.sub, Ui.REGULAR);
        inTv.setVisibility(GONE);
        temps.addView(inTv);
        status.addView(temps, Ui.margins(Ui.lp(-2, -2), dp(6), 0, 0, 0));
        status.setGravity(Gravity.TOP);
        p.addView(status, Ui.margins(new LayoutParams(-1, -2), 0, dp(14), 0, 0));

        car = new CarView(c);
        p.addView(car, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(4), 0, dp(4)));

        // pressione pneumatici sotto le ruote dell'auto (anteriori a sinistra, posteriori a destra)
        tpmsRow = Ui.row(c);
        tpmsRow.addView(new View(c), Ui.lpWeight(0, 1, 0.09f));
        tpmsFront = tpmsBlock(c, L.s(R.string.tpms_front));
        tpmsRow.addView(tpmsFront, Ui.lpWeight(0, -2, 0.32f));
        tpmsRow.addView(new View(c), Ui.lpWeight(0, 1, 0.20f));
        tpmsRear = tpmsBlock(c, L.s(R.string.tpms_rear));
        tpmsRow.addView(tpmsRear, Ui.lpWeight(0, -2, 0.32f));
        tpmsRow.addView(new View(c), Ui.lpWeight(0, 1, 0.07f));
        tpmsRow.setVisibility(GONE);
        p.addView(tpmsRow, Ui.margins(new LayoutParams(-1, -2), 0, 0, 0, dp(10)));

        LinearLayout chips = Ui.row(c);
        chipConsumo = addChip(c, chips, L.s(R.string.chip_consumption), true);
        chipLimite = addChip(c, chips, L.s(R.string.chip_limit), true);
        chipV2l = addChip(c, chips, "V2L", false);
        // V2L: tocca per aprire la scheda Scarica (presa esterna) della gestione energia
        View v2l = (View) chipV2l.getParent();
        v2l.setBackground(Ui.pressable(c, t.chip, 14, t));
        v2l.setClickable(true);
        v2l.setContentDescription(L.s(R.string.v2l_desc));
        v2l.setOnClickListener(v -> host.openEnergy("DischargeManagementFragment"));
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
            statusTv.setText(L.s(early ? R.string.connecting_vehicle : R.string.vehicle_unavailable));
            statusIcon.setImageResource(R.drawable.ic_info);
            statusIcon.setColorFilter(t.sub);
            return;
        }
        rangeTv.setText(s.rangeKm > 0 && s.rangeKm < 2000 ? String.valueOf(s.rangeKm) : "—");
        if (s.soc >= 0 && s.soc <= 100) {
            socTv.setText(prefs.socDecimals() ? String.format(L.locale(), "%.1f%%", s.soc) : Math.round(s.soc) + "%");
            battery.setValue(s.soc / 100f);
        }
        battery.setLimit(s.chargeLimit > 0 && s.chargeLimit < 100 ? s.chargeLimit / 100f : -1);
        statusTv.setText(chargeText(s));
        boolean charging = s.chargingStatus == 1 || s.chargingStatus == 10 || s.chargingStatus == 11 || s.chargingStatus == 16;
        statusIcon.setImageResource(s.chargingStatus <= 0 ? R.drawable.ic_car : (s.chargingStatus == 13 ? R.drawable.ic_plug : R.drawable.ic_bolt));
        statusIcon.setColorFilter(charging || s.chargingStatus == 2 || s.chargingStatus == 13 ? t.battery : t.sub);
        chipConsumo.setText(s.consumption > 0 && s.consumption < 80
                ? String.format(L.locale(), "%.1f kWh/100", s.consumption) : "—");
        chipLimite.setText(s.chargeLimit > 0 && s.chargeLimit <= 100 ? s.chargeLimit + "%" : "—");
        chipV2l.setText(L.s(s.chargingStatus == 13 ? R.string.on : R.string.off));
        if (s.acTemp > 0 && SystemClock.uptimeMillis() > localTempUntil) {
            acTemp = s.acTemp;
            tempTv.setText(tempLabel(acTemp));
        }
        boolean outOk = s.outsideTemp > -60 && s.outsideTemp < 70;
        outIcon.setVisibility(outOk ? VISIBLE : GONE);
        outTv.setText(outOk ? L.f(R.string.outside_temp, String.valueOf(Math.round(s.outsideTemp))) : "");
        boolean inOk = !Float.isNaN(s.insideTemp);
        inTv.setVisibility(inOk ? VISIBLE : GONE);
        if (inOk) inTv.setText(L.f(R.string.inside_temp, String.valueOf(Math.round(s.insideTemp))));
        if (inOk && !outOk) outIcon.setVisibility(VISIBLE);
        showTpms(s.tireBar);
        if (s.fanLevel >= 0 && SystemClock.uptimeMillis() > localFanUntil) {
            fanLevel = s.fanLevel;
            fanTv.setText(fanLabel(fanLevel));
        }
        fanMin = s.fanMin;
        fanMax = s.fanMax;
        styleToggle(acBtn, s.acOn);
        styleToggle(autoBtn, s.autoOn);
        styleHeat(seatLeftBtn, s.seatLeft);
        styleHeat(seatRightBtn, s.seatRight);
        styleToggle(wheelBtn, s.wheelHeat, 0xFFFF7A3D);
        frontFogBtn.setVisibility(s.hasFrontFog ? VISIBLE : GONE);
        styleToggle(frontFogBtn, s.frontFog, 0xFF34C77B);
        styleToggle(rearFogBtn, s.rearFog, 0xFFFFB020);
    }

    private String chargeText(VehicleClient.State s) {
        String kw = s.chargeKw > 0.3f ? String.format(L.locale(), " · %.1f kW", s.chargeKw) : "";
        String eta = "";
        if (s.predictMinutes > 0 && s.predictMinutes < 24 * 60) {
            Date end = new Date(System.currentTimeMillis() + s.predictMinutes * 60000L);
            eta = " · " + L.f(R.string.full_at, new SimpleDateFormat("HH:mm", L.locale()).format(end));
        }
        switch (s.chargingStatus) {
            case 1: return L.s(R.string.charge_charging) + kw + eta;
            case 10:
            case 11: return L.s(R.string.charge_fast) + kw + eta;
            case 16: return L.s(R.string.charge_wireless) + kw + eta;
            case 2: return L.s(R.string.charge_done);
            case 3: return L.s(R.string.charge_balancing);
            case 4: return L.s(R.string.charge_error);
            case 5: return L.s(R.string.charge_connecting);
            case 6: return L.s(R.string.charge_not_recognized);
            case 7: return L.s(R.string.charge_not_charging);
            case 8: return L.s(R.string.charge_stopped);
            case 9: return L.s(R.string.charge_scheduled);
            case 12: return L.s(R.string.charge_multiple);
            case 13: return L.s(R.string.v2l_active);
            case 14: return L.s(R.string.v2l_stopped);
            case 15: return L.s(R.string.v2l_done);
            default: return L.s(R.string.ready);
        }
    }

    /** Aggiorna l'orologio (chiamato ogni minuto dall'activity). */
    public void updateClock() {
        if (clockTv == null) return;
        boolean h24 = android.text.format.DateFormat.is24HourFormat(getContext());
        clockTv.setText(new SimpleDateFormat(h24 ? "HH:mm" : "h:mm", L.locale()).format(new Date()));
    }

    /** Indicatori in alto a destra: visibili solo se c'è qualcosa da mostrare. */
    public void onConn(com.mg4.launcher.svc.ConnClient.State s) {
        connBt.setVisibility(s.btOn ? VISIBLE : GONE);
        connBt.setAlpha(s.btConnected ? 1f : 0.4f);
        connWifi.setVisibility(s.wifiConnected ? VISIBLE : GONE);
        connWifi.setAlpha(0.45f + 0.55f * s.wifiLevel / 3f);
        boolean net = s.mobileType >= 0;
        connNet.setVisibility(net ? VISIBLE : GONE);
        connBars.setVisibility(net ? VISIBLE : GONE);
        connNet.setText(s.mobileType >= 2 ? s.mobileType + "G" : "—");
        connBars.setLevel(s.mobileType >= 2 ? s.mobileLevel : 0);
        // margine del primo elemento visibile
        boolean first = true;
        for (View v : new View[]{connBt, connWifi, connNet}) {
            if (v.getVisibility() != VISIBLE) continue;
            LayoutParams lp = (LayoutParams) v.getLayoutParams();
            lp.leftMargin = first ? 0 : dp(12);
            v.setLayoutParams(lp);
            first = false;
        }
        connRow.setVisibility(s.btOn || s.wifiConnected || net ? VISIBLE : GONE);
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
        tabMap = addTab(c, tabs, L.s(R.string.tab_map), TAB_MAP);
        tabMedia = addTab(c, tabs, L.s(R.string.tab_media), TAB_MEDIA);
        tabPhone = addTab(c, tabs, "CarPlay", TAB_PHONE);
        tabPhone.setVisibility(GONE);
        // indicatori Bluetooth / Wi-Fi / 4G a sinistra delle schede
        LinearLayout topRight = Ui.row(c);
        topRight.setGravity(Gravity.CENTER_VERTICAL);
        connRow = Ui.row(c);
        connRow.setGravity(Gravity.CENTER_VERTICAL);
        connRow.setPadding(dp(14), 0, dp(14), 0);
        connRow.setBackground(Ui.round(t.overlay, dp(16)));
        connBt = Ui.icon(c, R.drawable.ic_bluetooth, t.text, 22);
        connRow.addView(connBt, new LayoutParams(dp(22), dp(22)));
        connWifi = Ui.icon(c, R.drawable.ic_wifi, t.text, 24);
        connRow.addView(connWifi, Ui.margins(new LayoutParams(dp(24), dp(24)), dp(12), 0, 0, 0));
        connNet = Ui.text(c, "4G", 14, t.text, Ui.MEDIUM);
        connRow.addView(connNet, Ui.margins(new LayoutParams(-2, -2), dp(12), 0, 0, 0));
        connBars = new Bars.Signal(c);
        connBars.setColors(t.text, t.track);
        connRow.addView(connBars, Ui.margins(new LayoutParams(dp(22), dp(18)), dp(5), 0, 0, dp(2)));
        connRow.setVisibility(GONE);
        topRight.addView(connRow, Ui.margins(new LayoutParams(-2, tp(48) + dp(12)), 0, 0, dp(10), 0));
        topRight.addView(tabs);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        lp.setMargins(0, dp(20), dp(20), 0);
        f.addView(topRight, lp);
        return f;
    }

    private TextView addTab(Context c, LinearLayout parent, String label, int which) {
        TextView tv = Ui.text(c, label, ts(18), t.sub, Ui.MEDIUM);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(tp(22), 0, tp(22), 0);
        tv.setMinHeight(tp(48));
        tv.setClickable(true);
        tv.setOnClickListener(v -> selectTab(which, true));
        parent.addView(tv, Ui.margins(Ui.lp(-2, tp(48)), which == 0 ? 0 : dp(6), 0, 0, 0));
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

        // velo sfumato da sinistra: il testo resta leggibile sopra la mappa decorativa
        View veil = new View(c);
        veil.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{withAlpha(t.panel, 0xF2), withAlpha(t.panel, 0xC8), withAlpha(t.panel, 0x00)}));
        f.addView(veil, new FrameLayout.LayoutParams(dp(820), -1));

        // nessuna guida: titolo, barra di ricerca, Casa / Lavoro, "Apri con"
        navIdle = Ui.column(c);
        navIdle.setGravity(Gravity.CENTER_VERTICAL);
        navIdle.setPadding(dp(48), dp(30), dp(40), dp(28));

        TextView q = Ui.text(c, L.s(R.string.where_to), 44, t.text, Ui.MEDIUM);
        q.setLetterSpacing(-0.01f);
        navIdle.addView(q);
        navDefaultTv = Ui.oneLine(Ui.text(c, "", 18, t.sub, Ui.REGULAR));
        navIdle.addView(navDefaultTv, Ui.margins(new LayoutParams(-2, -2), 0, dp(6), 0, 0));

        LinearLayout search = Ui.row(c);
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(26), 0, dp(12), 0);
        search.setBackground(Ui.pressable(c, t.chip, 38, t));
        search.setClickable(true);
        search.setContentDescription(L.s(R.string.search_destination));
        search.setOnClickListener(v -> host.launchNavigator());
        search.addView(Ui.icon(c, R.drawable.ic_search, t.sub, 28), new LayoutParams(tp(28), tp(28)));
        search.addView(Ui.oneLine(Ui.text(c, L.s(R.string.search_destination), ts(22), t.sub, Ui.REGULAR)),
                Ui.margins(Ui.lpWeight(0, -2, 1), dp(16), 0, dp(12), 0));
        FrameLayout go = new FrameLayout(c);
        go.setBackground(Ui.round(t.accent, tp(26)));
        go.addView(Ui.icon(c, R.drawable.ic_nav, t.onAccent, 26), new FrameLayout.LayoutParams(tp(26), tp(26), Gravity.CENTER));
        search.addView(go, new LayoutParams(tp(52), tp(52)));
        navIdle.addView(search, Ui.margins(new LayoutParams(dp(640), tp(76)), 0, dp(22), 0, 0));

        navPlaces = Ui.row(c);
        navHomeBtn = placeTile(c, R.drawable.ic_home, L.s(R.string.home_place), v -> nav.goHome());
        navOfficeBtn = placeTile(c, R.drawable.ic_work, L.s(R.string.work_place), v -> nav.goOffice());
        navPlaces.addView(navHomeBtn, Ui.margins(new LayoutParams(dp(312), tp(88)), 0, 0, dp(16), 0));
        navPlaces.addView(navOfficeBtn, new LayoutParams(dp(312), tp(88)));
        navIdle.addView(navPlaces, Ui.margins(new LayoutParams(-2, -2), 0, dp(16), 0, 0));

        // apri con: MG / telefono (CarPlay, Android Auto) / navigatori installati (Waze, Google Maps...)
        navWithBox = Ui.column(c);
        LinearLayout wl = Ui.row(c);
        wl.setGravity(Gravity.BOTTOM);
        TextView wt = Ui.text(c, L.s(R.string.open_with).toUpperCase(L.locale()), 13, t.sub, Ui.MEDIUM);
        wt.setLetterSpacing(0.08f);
        wl.addView(wt);
        wl.addView(Ui.oneLine(Ui.text(c, "· " + L.s(R.string.open_with_hint), 13, t.sub, Ui.REGULAR)),
                Ui.margins(new LayoutParams(-2, -2), dp(8), 0, 0, 0));
        navWithBox.addView(wl);
        navWith = Ui.row(c);
        navWithBox.addView(navWith, Ui.margins(new LayoutParams(-2, -2), -dp(5), dp(8), 0, 0));
        navIdle.addView(navWithBox, Ui.margins(new LayoutParams(-2, -2), 0, dp(22), 0, 0));
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
        cl.setMargins(dp(20 + (rhd && sysIndicator ? SYSTEM_LIGHT_INSET : 0)), dp(20), dp(20), 0);
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

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Riquadro Casa / Lavoro: icona in un cerchio + nome + "Avvia percorso". */
    private LinearLayout placeTile(Context c, int icon, String label, View.OnClickListener l) {
        LinearLayout tile = Ui.row(c);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(16), 0, dp(18), 0);
        tile.setBackground(Ui.pressable(c, t.chip, 22, t));
        tile.setClickable(true);
        tile.setContentDescription(label);
        tile.setOnClickListener(l);
        FrameLayout ic = new FrameLayout(c);
        ic.setBackground(Ui.round(t.overlay, dp(28)));
        ic.addView(Ui.icon(c, icon, t.text, 26), new FrameLayout.LayoutParams(tp(26), tp(26), Gravity.CENTER));
        tile.addView(ic, new LayoutParams(tp(56), tp(56)));
        LinearLayout tx = Ui.column(c);
        tx.addView(Ui.oneLine(Ui.text(c, label, ts(21), t.text, Ui.MEDIUM)));
        tx.addView(Ui.oneLine(Ui.text(c, L.s(R.string.go_there), ts(15), t.sub, Ui.REGULAR)), Ui.margins(new LayoutParams(-2, -2), 0, dp(2), 0, 0));
        tile.addView(tx, Ui.margins(Ui.lpWeight(0, -2, 1), dp(16), 0, 0, 0));
        tile.addView(Ui.icon(c, R.drawable.ic_chev_right, t.sub, 22), new LayoutParams(dp(22), dp(22)));
        return tile;
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
        String defName = "MG";
        for (com.mg4.launcher.apps.AppCatalog.App a : host.otherNavApps()) {
            addNavChip(c, a.label, a.packageName, pref);
            if (a.packageName.equals(pref)) defName = a.label;
        }
        if (Prefs.NAV_PHONE.equals(pref) || (Prefs.NAV_AUTO.equals(pref) && phone)) {
            defName = media.last().deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay";
        }
        navDefaultTv.setText(L.f(R.string.nav_default, defName));
        navWithBox.setVisibility(navWith.getChildCount() > 1 ? VISIBLE : GONE);
    }

    private void addNavChip(Context c, String label, String value, String pref) {
        boolean def = value.equals(pref) || (Prefs.NAV_AUTO.equals(pref) && value.equals(navWithPhone ? Prefs.NAV_PHONE : Prefs.NAV_MG));
        TextView chip = Ui.text(c, label, ts(16), def ? t.text : t.sub, Ui.MEDIUM);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(tp(18), 0, tp(18), 0);
        chip.setMinHeight(tp(44));
        chip.setBackground(Ui.pressable(c, def ? t.chip : t.overlay, 22, t));
        chip.setClickable(true);
        chip.setOnClickListener(v -> host.launchNavigator(value));
        chip.setOnLongClickListener(v -> {
            prefs.setNavApp(value);
            buildNavWith(getContext(), navWithPhone);
            return true;
        });
        navWith.addView(chip, Ui.margins(Ui.lp(-2, tp(44)), dp(5), 0, dp(5), 0));
        chip.setBackground(Ui.pressable(c, def ? t.text : t.chip, 22, t));
        chip.setTextColor(def ? t.panel : t.text);
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
        navPlaces.setVisibility(n.connected && (n.hasHome || n.hasOffice) ? VISIBLE : GONE);
        navIdle.setVisibility(guiding ? GONE : VISIBLE);
        navCard.setVisibility(guiding ? VISIBLE : GONE);
        navEta.setVisibility(n.navigating && !remote && n.remainSeconds > 0 ? VISIBLE : GONE);
        if (remote) {
            navCardTitle.setText(m.tbtDistance.isEmpty() ? (m.deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay") : m.tbtDistance);
            navCardSub.setText(m.tbtRoad.isEmpty() ? L.s(R.string.nav_phone) : m.tbtRoad);
        } else if (n.navigating) {
            if (n.turnMeters >= 0) {
                navCardTitle.setText(km(n.turnMeters));
                String next = !n.turnText.isEmpty() ? n.turnText : n.road;
                navCardSub.setText(next.isEmpty() ? L.s(R.string.next_maneuver) : next);
            } else {
                navCardTitle.setText(n.road.isEmpty() ? L.s(R.string.navigating) : n.road);
                navCardSub.setText(n.remainMeters > 0 ? L.f(R.string.remaining, km(n.remainMeters)) : L.s(R.string.tap_open_nav));
            }
            if (n.remainSeconds > 0) {
                Date arrive = new Date(System.currentTimeMillis() + n.remainSeconds * 1000L);
                navEtaArrive.setText(L.f(R.string.arrival, new SimpleDateFormat("HH:mm", L.locale()).format(arrive)));
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
        return String.format(L.locale(), meters < 10000 ? "%.1f km" : "%.0f km", meters / 1000f);
    }

    // ---- media -------------------------------------------------------------------------------

    private View buildMediaPanel(Context c) {
        LinearLayout col = Ui.column(c);
        col.setPadding(dp(28), dp(20), dp(36), dp(22));

        // sorgenti: un pulsante per ciascuna (Radio, USB, Bluetooth, Online, + CarPlay/Android Auto se collegato)
        srcRow = Ui.row(c);
        srcRow.setBackground(Ui.round(t.overlay, dp(16)));
        srcRow.setPadding(dp(6), dp(6), dp(6), dp(6));
        // guida a destra con la spia luci: la riga sorgenti parte più a destra
        col.addView(srcRow, Ui.margins(new LayoutParams(-2, tp(48) + dp(12)), rhd && sysIndicator ? dp(SYSTEM_LIGHT_INSET) : 0, 0, 0, 0));

        // corpo: a sinistra copertina + comandi + preferiti, a destra (solo radio) l'elenco delle stazioni trovate
        LinearLayout body = Ui.row(c);
        col.addView(body, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(14), 0, 0));
        LinearLayout left = Ui.column(c);
        body.addView(left, Ui.lpWeight(0, -1, 1));

        LinearLayout main = Ui.row(c);
        main.setGravity(Gravity.CENTER_VERTICAL);
        left.addView(main, Ui.margins(Ui.lpWeight(-1, 0, 1), dp(8), 0, 0, 0));

        FrameLayout art = new FrameLayout(c);
        art.setBackground(Ui.pressable(c, t.chip, 26, t));
        art.setClipToOutline(true);
        art.setClickable(true);
        art.setContentDescription(L.s(R.string.open_source_app));
        art.setOnClickListener(v -> openCurrentSourceApp());
        artIcon = Ui.icon(c, R.drawable.ic_music, t.sub, 84);
        art.addView(artIcon, new FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER));
        artLogo = new ImageView(c);
        artLogo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        artLogo.setPadding(dp(20), dp(20), dp(20), dp(20));
        art.addView(artLogo, new FrameLayout.LayoutParams(-1, -1));
        artRadio = Ui.column(c);
        artRadio.setGravity(Gravity.CENTER);
        artBand = Ui.text(c, "FM", 20, t.sub, Ui.MEDIUM);
        artBand.setLetterSpacing(0.12f);
        artBand.setGravity(Gravity.CENTER);
        artFreq = Ui.oneLine(Ui.text(c, "", 54, t.text, Ui.LIGHT));
        artFreq.setGravity(Gravity.CENTER);
        artUnit = Ui.oneLine(Ui.text(c, "MHz", 19, t.sub, Ui.REGULAR));
        artUnit.setGravity(Gravity.CENTER);
        artRadio.addView(artBand, new LayoutParams(-1, -2));
        artRadio.addView(artFreq, Ui.margins(new LayoutParams(-1, -2), 0, dp(2), 0, dp(2)));
        artRadio.addView(artUnit, new LayoutParams(-1, -2));
        artRadio.setPadding(dp(12), 0, dp(12), 0);
        art.addView(artRadio, new FrameLayout.LayoutParams(-1, -1));
        // con i comandi più grandi la copertina si restringe un po' per lasciare spazio ai pulsanti
        int artSize = dp(Math.round(208 - 110 * (Ui.K - 1)));
        main.addView(art, new LayoutParams(artSize, artSize));

        LinearLayout info = Ui.column(c);
        main.addView(info, Ui.margins(Ui.lpWeight(0, -2, 1), dp(28), 0, 0, 0));

        mMeta = Ui.oneLine(Ui.text(c, "", 17, t.sub, Ui.MEDIUM));
        mMeta.setLetterSpacing(0.06f);
        info.addView(mMeta, new LayoutParams(-1, -2));
        mTitle = Ui.oneLine(Ui.text(c, "", 40, t.text, Ui.MEDIUM));
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
        ctr.addView(Ui.iconButton(c, t, R.drawable.ic_prev, t.text, t.chip, 64, 28, 32, L.s(R.string.previous), v -> media.skip(false)));
        FrameLayout play = Ui.iconButton(c, t, R.drawable.ic_pause, t.panel, t.text, 84, 36, 42, L.s(R.string.play_pause), v -> media.playPause());
        playIcon = (ImageView) play.getChildAt(0);
        int gap = dp(Ui.K > 1 ? 12 : 16);
        ctr.addView(play, Ui.margins(Ui.lp(tp(84), tp(84)), gap, 0, gap, 0));
        ctr.addView(Ui.iconButton(c, t, R.drawable.ic_next, t.text, t.chip, 64, 28, 32, L.s(R.string.next), v -> media.skip(true)));
        bandRow = Ui.row(c);
        bandRow.setBackground(Ui.round(t.chip, dp(16)));
        bandRow.setPadding(dp(5), dp(5), dp(5), dp(5));
        bandChips = new TextView[2];
        String[] bands = {"FM", "DAB"};
        int[] bandIds = {MediaClient.BAND_FM, MediaClient.BAND_DAB};
        for (int i = 0; i < 2; i++) {
            final int band = bandIds[i];
            TextView b = Ui.text(c, bands[i], ts(18), t.sub, Ui.MEDIUM);
            b.setGravity(Gravity.CENTER);
            b.setMinWidth(tp(64));
            b.setPadding(tp(16), 0, tp(16), 0);
            b.setClickable(true);
            b.setContentDescription(L.s(R.string.src_radio) + " " + bands[i]);
            b.setOnClickListener(v -> {
                pendingBand = band;
                pendingBandUntil = SystemClock.uptimeMillis() + 4000;
                styleBands(band);
                media.switchBand(band);
            });
            bandRow.addView(b, Ui.margins(Ui.lp(-2, tp(54)), i == 0 ? 0 : dp(4), 0, 0, 0));
            bandChips[i] = b;
        }
        ctr.addView(bandRow, Ui.margins(Ui.lp(-2, tp(64)), dp(Ui.K > 1 ? 16 : 24), 0, 0, 0));
        info.addView(ctr, Ui.margins(new LayoutParams(-1, -2), 0, dp(20), 0, 0));

        // preferiti radio con i loghi (FM e DAB)
        android.widget.HorizontalScrollView hs = new android.widget.HorizontalScrollView(c);
        hs.setHorizontalScrollBarEnabled(false);
        hs.setOverScrollMode(OVER_SCROLL_NEVER);
        favRow = Ui.row(c);
        hs.addView(favRow);
        favScroll = hs;
        left.addView(hs, Ui.margins(new LayoutParams(-1, dp(122)), dp(4), dp(10), 0, 0));

        // elenco delle stazioni trovate dalla radio (banda attuale)
        stationPanel = Ui.column(c);
        stationPanel.setBackground(Ui.round(t.overlay, dp(20)));
        stationPanel.setPadding(dp(8), dp(14), dp(8), dp(8));
        LinearLayout sh = Ui.row(c);
        sh.setGravity(Gravity.CENTER_VERTICAL);
        sh.setPadding(dp(12), 0, dp(10), dp(6));
        stationTitle = Ui.text(c, "", 14, t.sub, Ui.MEDIUM);
        stationTitle.setLetterSpacing(0.08f);
        sh.addView(stationTitle, Ui.lpWeight(0, -2, 1));
        stationCount = Ui.text(c, "", 14, t.sub, Ui.REGULAR);
        sh.addView(stationCount);
        stationPanel.addView(sh, new LayoutParams(-1, -2));
        android.widget.ScrollView ss = new android.widget.ScrollView(c);
        ss.setVerticalScrollBarEnabled(false);
        ss.setOverScrollMode(OVER_SCROLL_NEVER);
        stationList = Ui.column(c);
        ss.addView(stationList, new FrameLayout.LayoutParams(-1, -2));
        stationScroll = ss;
        stationPanel.addView(ss, Ui.lpWeight(-1, 0, 1));
        stationEmpty = Ui.column(c);
        stationEmpty.setGravity(Gravity.CENTER);
        stationEmpty.setPadding(dp(18), 0, dp(18), 0);
        TextView et = Ui.text(c, L.s(R.string.stations_empty), 16, t.sub, Ui.REGULAR);
        et.setGravity(Gravity.CENTER);
        stationEmpty.addView(et);
        TextView eb = Ui.text(c, L.s(R.string.open_radio_app), ts(17), t.text, Ui.MEDIUM);
        eb.setGravity(Gravity.CENTER);
        eb.setPadding(tp(22), 0, tp(22), 0);
        eb.setBackground(Ui.pressable(c, t.chip, 14, t));
        eb.setClickable(true);
        eb.setOnClickListener(v -> host.launch("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity"));
        stationEmpty.addView(eb, Ui.margins(new LayoutParams(-2, tp(52)), 0, dp(16), 0, 0));
        stationPanel.addView(stationEmpty, Ui.lpWeight(-1, 0, 1));
        body.addView(stationPanel, Ui.margins(new LayoutParams(dp(330), -1), dp(18), 0, 0, 0));
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
            case MediaClient.SRC_RADIO: return L.s(R.string.src_radio);
            case MediaClient.SRC_USB: return "USB";
            case MediaClient.SRC_BT: return "Bluetooth";
            case MediaClient.SRC_ONLINE: return L.s(R.string.src_online);
            case MediaClient.SRC_CARPLAY: return "CarPlay";
            case MediaClient.SRC_AAUTO: return "Android Auto";
            default: return L.s(R.string.media_generic);
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
                TextView b = Ui.text(c, sourceLabel(src), ts(17), t.sub, Ui.MEDIUM);
                b.setGravity(Gravity.CENTER_VERTICAL);
                b.setPadding(tp(compact ? 14 : 18), 0, tp(compact ? 16 : 22), 0);
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
            boolean compact = srcRow.getChildCount() > 4;
            b.setText(compact && !on ? "" : sourceLabel(src));
            b.setCompoundDrawablePadding(compact && !on ? 0 : dp(10));
            android.graphics.drawable.Drawable d = getContext().getDrawable(sourceIcon(src)).mutate();
            d.setTint(on ? t.panel : t.sub);
            d.setBounds(0, 0, tp(22), tp(22));
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
        if (w.length == 1) return w[0].length() <= 5 ? w[0].toUpperCase(L.locale()) : w[0].substring(0, 4).toUpperCase(L.locale());
        return (w[0].substring(0, 1) + w[1].substring(0, 1)).toUpperCase(L.locale());
    }

    /** Riga dell'elenco stazioni: logo (o iniziali) + nome + frequenza; tocca per sintonizzare. */
    private View stationRow(Context c, MediaClient.Station st, boolean current) {
        LinearLayout row = Ui.row(c);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(12), 0);
        row.setBackground(current ? Ui.round(t.chip, dp(14)) : Ui.pressable(c, 0, 14, t));
        row.setClickable(true);
        row.setContentDescription(st.label());
        row.setOnClickListener(v -> media.tune(st));
        FrameLayout box = new FrameLayout(c);
        box.setBackground(Ui.round(current ? t.overlay : t.chip, dp(10)));
        box.setClipToOutline(true);
        if (st.logo != null) {
            ImageView iv = new ImageView(c);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            iv.setImageBitmap(st.logo);
            iv.setPadding(dp(4), dp(4), dp(4), dp(4));
            box.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        } else {
            TextView ini = Ui.text(c, initials(st), ts(13), t.text, Ui.MEDIUM);
            ini.setGravity(Gravity.CENTER);
            box.addView(ini, new FrameLayout.LayoutParams(-1, -1));
        }
        row.addView(box, new LayoutParams(tp(44), tp(44)));
        LinearLayout tx = Ui.column(c);
        tx.addView(Ui.oneLine(Ui.text(c, st.label(), ts(18), t.text, current ? Ui.MEDIUM : Ui.REGULAR)));
        String sub = st.band == MediaClient.BAND_DAB ? "DAB+" : String.format(L.locale(), "FM %.1f MHz", st.khz / 1000f);
        tx.addView(Ui.oneLine(Ui.text(c, sub, ts(14), t.sub, Ui.REGULAR)));
        row.addView(tx, Ui.margins(Ui.lpWeight(0, -2, 1), dp(12), 0, 0, 0));
        if (current) row.addView(Ui.icon(c, R.drawable.ic_wave, t.text, 20), new LayoutParams(dp(20), dp(20)));
        return row;
    }

    private void showStations(MediaClient.State m) {
        boolean dab = m.radioBand == MediaClient.BAND_DAB;
        stationTitle.setText(L.f(R.string.stations_title, dab ? "DAB" : "FM").toUpperCase(L.locale()));
        stationCount.setText(m.stations.isEmpty() ? "" : String.valueOf(m.stations.size()));
        StringBuilder sig = new StringBuilder(m.stationKey).append('#').append(m.radioBand);
        for (MediaClient.Station st : m.stations) sig.append('|').append(st.key).append(st.logo != null ? '+' : '-');
        boolean empty = m.stations.isEmpty();
        stationScroll.setVisibility(empty ? GONE : VISIBLE);
        stationEmpty.setVisibility(empty ? VISIBLE : GONE);
        if (sig.toString().equals(stationSignature)) return;
        boolean firstForBand = m.radioBand != stationBand;
        stationBand = m.radioBand;
        stationSignature = sig.toString();
        stationList.removeAllViews();
        Context c = getContext();
        int currentIndex = -1;
        for (int i = 0; i < m.stations.size(); i++) {
            MediaClient.Station st = m.stations.get(i);
            boolean cur = st.key.equals(m.stationKey);
            if (cur) currentIndex = i;
            stationList.addView(stationRow(c, st, cur), Ui.margins(new LayoutParams(-1, tp(62)), 0, 0, 0, dp(2)));
        }
        // porta in vista la stazione in onda
        if (currentIndex >= 0) {
            final int idx = currentIndex;
            stationScroll.post(() -> {
                View v = stationList.getChildAt(idx);
                if (v == null) return;
                int y = v.getTop() - (stationScroll.getHeight() - v.getHeight()) / 2;
                if (firstForBand) stationScroll.scrollTo(0, Math.max(0, y));
                else if (v.getTop() < stationScroll.getScrollY() || v.getBottom() > stationScroll.getScrollY() + stationScroll.getHeight())
                    stationScroll.smoothScrollTo(0, Math.max(0, y));
            });
        }
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
            mMeta.setText(sourceLabel(m.source).toUpperCase(L.locale()));
        }
        bandRow.setVisibility(radio ? VISIBLE : GONE);
        stationPanel.setVisibility(radio ? VISIBLE : GONE);
        if (radio) showStations(m);
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
        miniSub.setText(sourceLabel(m.source) + (m.subtitle.isEmpty() ? "" : " · " + m.subtitle));
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
            phoneTitle.setText(m.deviceName.isEmpty() ? L.s(R.string.phone_connected) : m.deviceName);
            phoneSub.setText(L.f(R.string.projection_ready, m.deviceType == MediaClient.DEV_AAUTO ? "Android Auto" : "CarPlay"));
        }
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return (s / 60) + ":" + String.format(L.locale(), "%02d", s % 60);
    }

    // ---- CarPlay / Android Auto --------------------------------------------------------------

    private View buildPhonePanel(Context c) {
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER);
        col.setPadding(dp(40), dp(40), dp(40), dp(40));
        col.addView(Ui.icon(c, R.drawable.ic_smartphone, t.sub, 84));
        phoneTitle = Ui.text(c, L.s(R.string.phone_connected), 30, t.text, Ui.MEDIUM);
        phoneTitle.setGravity(Gravity.CENTER);
        col.addView(phoneTitle, Ui.margins(new LayoutParams(-2, -2), 0, dp(18), 0, 0));
        phoneSub = Ui.text(c, "", 20, t.sub, Ui.REGULAR);
        phoneSub.setGravity(Gravity.CENTER);
        col.addView(phoneSub, Ui.margins(new LayoutParams(-2, -2), 0, dp(12), 0, 0));
        TextView open = Ui.text(c, L.s(R.string.open), ts(20), t.onAccent, Ui.MEDIUM);
        open.setGravity(Gravity.CENTER);
        open.setPadding(tp(40), 0, tp(40), 0);
        open.setMinHeight(tp(60));
        open.setBackground(Ui.pressable(c, t.accent, 30 * Ui.K, t));
        open.setClickable(true);
        open.setOnClickListener(v -> media.openProjection());
        col.addView(open, Ui.margins(new LayoutParams(-2, tp(60)), 0, dp(26), 0, 0));
        return col;
    }

    // =========================================================================================
    // Barra in basso
    // =========================================================================================

    private View buildBottomBar(Context c) {
        LinearLayout bar = Ui.row(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackground(Ui.round(t.panel, dp(24)));
        bar.setPadding(dp(14), 0, dp(14), 0);

        // temperatura: pulsanti grandi, facili da prendere durante la guida
        LinearLayout tempBox = Ui.row(c);
        tempBox.setGravity(Gravity.CENTER_VERTICAL);
        tempBox.setBackground(Ui.round(t.chip, dp(20)));
        tempBox.addView(Ui.iconButton(c, t, R.drawable.ic_chev_left, t.text, 0, 68, 30, 20, L.s(R.string.temp_down),
                v -> changeTemp(-1)));
        tempTv = Ui.text(c, "—", ts(40), t.text, Ui.REGULAR);
        tempTv.setGravity(Gravity.CENTER);
        tempBox.addView(tempTv, new LayoutParams(tp(112), -2));
        tempBox.addView(Ui.iconButton(c, t, R.drawable.ic_chev_right, t.text, 0, 68, 30, 20, L.s(R.string.temp_up),
                v -> changeTemp(1)));
        bar.addView(tempBox, new LayoutParams(-2, tp(76)));

        // ventola: ‹ livello ›, tocca l'icona = app Clima, tieni premuto = accendi/spegni il clima
        LinearLayout fanGroup = Ui.row(c);
        fanGroup.setGravity(Gravity.CENTER_VERTICAL);
        fanGroup.setBackground(Ui.round(t.chip, dp(20)));
        fanGroup.addView(Ui.iconButton(c, t, R.drawable.ic_chev_left, t.text, 0, 68, 30, 20, L.s(R.string.fan_down),
                v -> changeFan(-1)));
        LinearLayout fanBox = Ui.row(c);
        fanBox.setGravity(Gravity.CENTER);
        ImageView fanIc = Ui.icon(c, R.drawable.ic_fan, t.accent, 30);
        fanBox.addView(fanIc, new LayoutParams(tp(30), tp(30)));
        fanTv = Ui.text(c, "—", ts(34), t.text, Ui.REGULAR);
        fanTv.setGravity(Gravity.CENTER);
        fanBox.addView(fanTv, Ui.margins(new LayoutParams(-2, -2), dp(10), 0, 0, 0));
        fanBox.setMinimumWidth(tp(118));
        fanBox.setClickable(true);
        fanBox.setContentDescription(L.s(R.string.climate));
        fanBox.setBackground(Ui.pressable(c, 0, 16, t));
        fanBox.setOnClickListener(v -> host.launch("com.saicmotor.hmi.hvac", "com.saicmotor.hmi.hvac.HvacActivity"));
        fanBox.setOnLongClickListener(v -> {
            vehicle.setHvacPower(vehicle.last().hvacPower != 1);
            return true;
        });
        fanGroup.addView(fanBox, new LayoutParams(-2, tp(68)));
        fanGroup.addView(Ui.iconButton(c, t, R.drawable.ic_chev_right, t.text, 0, 68, 30, 20, L.s(R.string.fan_up),
                v -> changeFan(1)));
        bar.addView(fanGroup, Ui.margins(new LayoutParams(-2, tp(76)), dp(10), 0, 0, 0));

        // A/C e AUTO (lo sbrinamento ha il tasto fisico)
        acBtn = toggleText(c, "A/C", L.s(R.string.ac_desc), v -> vehicle.toggleAc());
        bar.addView(acBtn, Ui.margins(new LayoutParams(tp(76), tp(76)), dp(10), 0, 0, 0));
        autoBtn = toggleText(c, "AUTO", L.s(R.string.auto_desc), v -> vehicle.toggleAuto());
        bar.addView(autoBtn, Ui.margins(new LayoutParams(tp(76), tp(76)), dp(8), 0, 0, 0));
        // riscaldamento: sedile sinistro, volante, sedile destro
        // in un gruppo unico, così con la guida a destra il sedile sinistro resta a sinistra
        LinearLayout heat = Ui.row(c);
        seatLeftBtn = heatButton(c, R.drawable.ic_seat_heat, false, L.s(R.string.seat_left), v -> vehicle.pressSeatLeft());
        heat.addView(seatLeftBtn, new LayoutParams(tp(76), tp(76)));
        wheelBtn = toggleIcon(c, R.drawable.ic_wheel_heat, L.s(R.string.wheel_heat), v -> vehicle.toggleWheelHeat());
        heat.addView(wheelBtn, Ui.margins(new LayoutParams(tp(76), tp(76)), dp(8), 0, 0, 0));
        seatRightBtn = heatButton(c, R.drawable.ic_seat_heat, true, L.s(R.string.seat_right), v -> vehicle.pressSeatRight());
        heat.addView(seatRightBtn, Ui.margins(new LayoutParams(tp(76), tp(76)), dp(8), 0, 0, 0));
        bar.addView(heat, Ui.margins(new LayoutParams(-2, -2), dp(8), 0, 0, 0));

        // mini-player compatto (unico elemento elastico; con i comandi molto grandi lascia il posto ai pulsanti,
        // i comandi della musica restano nel pannello Media)
        boolean showMini = Ui.K < 1.25f;
        if (showMini) bar.addView(Ui.divider(c, t.track, 48));
        LinearLayout mini = Ui.row(c);
        mini.setGravity(Gravity.CENTER_VERTICAL);
        mini.setPadding(dp(6), 0, dp(4), 0);
        miniArt = Ui.icon(c, R.drawable.ic_radio, t.sub, 26);
        FrameLayout artBox = new FrameLayout(c);
        artBox.setBackground(Ui.round(t.chip, tp(12)));
        artBox.addView(miniArt, new FrameLayout.LayoutParams(tp(26), tp(26), Gravity.CENTER));
        mini.addView(artBox, new LayoutParams(tp(48), tp(48)));
        LinearLayout mt = Ui.column(c);
        miniTitle = Ui.oneLine(Ui.text(c, "", ts(17), t.text, Ui.MEDIUM));
        miniSub = Ui.oneLine(Ui.text(c, "", ts(14), t.sub, Ui.REGULAR));
        mt.addView(miniTitle);
        mt.addView(miniSub, Ui.margins(new LayoutParams(-2, -2), 0, dp(2), 0, 0));
        mini.addView(mt, Ui.margins(Ui.lpWeight(0, -2, 1), dp(12), 0, dp(4), 0));
        mini.setClickable(true);
        mini.setOnClickListener(v -> selectTab(TAB_MEDIA, true));
        FrameLayout mp = Ui.iconButton(c, t, R.drawable.ic_pause, t.text, 0, 52, 26, 26, L.s(R.string.play_pause), v -> media.playPause());
        miniPlayIcon = (ImageView) mp.getChildAt(0);
        mini.addView(mp);
        mini.addView(Ui.iconButton(c, t, R.drawable.ic_next, t.text, 0, 52, 24, 26, L.s(R.string.next), v -> media.skip(true)));
        if (showMini) bar.addView(mini, Ui.lpWeight(0, -1, 1));
        else bar.addView(Ui.spacer(c));

        bar.addView(Ui.divider(c, t.track, 48));

        // fendinebbia (sulla MG4 si accendono solo dallo schermo)
        frontFogBtn = toggleIcon(c, R.drawable.ic_fog_front, L.s(R.string.fog_front), v -> vehicle.toggleFrontFog());
        frontFogBtn.setVisibility(GONE);
        bar.addView(frontFogBtn, Ui.margins(new LayoutParams(tp(68), tp(68)), 0, 0, dp(8), 0));
        rearFogBtn = toggleIcon(c, R.drawable.ic_fog_rear, L.s(R.string.fog_rear), v -> vehicle.toggleRearFog());
        bar.addView(rearFogBtn, new LayoutParams(tp(68), tp(68)));

        bar.addView(Ui.divider(c, t.track, 48));

        bar.addView(shortcut(c, R.drawable.ic_phone, L.s(R.string.app_phone), v -> host.launch("com.saicmotor.hmi.btcall", "com.saicmotor.hmi.btcall.BtCallActivity")));
        bar.addView(shortcut(c, R.drawable.ic_radio, L.s(R.string.src_radio), v -> host.launch("com.saicmotor.hmi.radio", "com.saicmotor.hmi.radio.app.RadioHomeActivity")));
        bar.addView(shortcut(c, R.drawable.ic_cam360, L.s(R.string.app_camera), v -> host.launch("com.saicmotor.hmi.aroundview", "com.saicmotor.hmi.aroundview.AVMActivity")));
        bar.addView(shortcut(c, R.drawable.ic_gear, L.s(R.string.app_settings), v -> host.launch("com.saicmotor.hmi.systemsettings", "com.saicmotor.hmi.systemsettings.SettingsActivity")));
        // le app si aprono scorrendo la home verso sinistra: niente pulsante dedicato
        if (rhd) mirrorRow(bar);
        return bar;
    }

    /**
     * Guida a destra: ordine degli elementi invertito (la temperatura finisce all'estremo destro, vicino al
     * guidatore), mentre dentro ogni elemento tutto resta da sinistra a destra (frecce ‹ › comprese).
     */
    private static void mirrorRow(LinearLayout row) {
        row.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        for (int i = 0; i < row.getChildCount(); i++) {
            View v = row.getChildAt(i);
            v.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            if (v.getLayoutParams() instanceof MarginLayoutParams) {
                MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
                lp.setMarginStart(lp.leftMargin);
                lp.setMarginEnd(lp.rightMargin);
                v.setLayoutParams(lp);
            }
        }
    }

    /** Blocco TPMS: titolo piccolo (Anteriori / Posteriori) + "2,5 · 2,5 bar" (sinistra · destra). */
    private LinearLayout tpmsBlock(Context c, String title) {
        LinearLayout b = Ui.column(c);
        b.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView tt = Ui.text(c, title.toUpperCase(L.locale()), 12, t.sub, Ui.MEDIUM);
        tt.setLetterSpacing(0.08f);
        b.addView(tt);
        TextView v = Ui.oneLine(Ui.text(c, "", 19, t.text, Ui.MEDIUM));
        v.setGravity(Gravity.CENTER);
        b.addView(v, Ui.margins(new LayoutParams(-2, -2), 0, dp(2), 0, 0));
        return b;
    }

    private void showTpms(float[] bar) {
        boolean any = false;
        for (float f : bar) if (!Float.isNaN(f)) any = true;
        tpmsRow.setVisibility(prefs.showTpms() && any ? VISIBLE : GONE);
        if (!any) return;
        setTpms(tpmsFront, bar[0], bar[1]);
        setTpms(tpmsRear, bar[2], bar[3]);
    }

    private void setTpms(LinearLayout block, float left, float right) {
        TextView v = (TextView) block.getChildAt(1);
        String l = Float.isNaN(left) ? "—" : String.format(L.locale(), "%.1f", left);
        String r = Float.isNaN(right) ? "—" : String.format(L.locale(), "%.1f", right);
        v.setText(l + " · " + r + " bar");
        // pressione bassa (sotto 2,0 bar): in ambra
        boolean low = (!Float.isNaN(left) && left < 2.0f) || (!Float.isNaN(right) && right < 2.0f);
        v.setTextColor(low ? 0xFFFFB020 : t.text);
    }

    /** Pulsante sedile riscaldato: icona (specchiata per il lato destro) + tre tacche per il livello. */
    private FrameLayout heatButton(Context c, int icon, boolean mirror, String desc, View.OnClickListener l) {
        FrameLayout f = new FrameLayout(c);
        f.setContentDescription(desc);
        f.setClickable(true);
        f.setOnClickListener(l);
        ImageView iv = Ui.icon(c, icon, t.text, 32);
        if (mirror) iv.setScaleX(-1f);
        f.addView(iv, new FrameLayout.LayoutParams(tp(32), tp(32), Gravity.CENTER_HORIZONTAL | Gravity.TOP));
        ((FrameLayout.LayoutParams) iv.getLayoutParams()).topMargin = tp(14);
        LinearLayout dots = Ui.row(c);
        for (int i = 0; i < 3; i++) {
            View d = new View(c);
            d.setBackground(Ui.round(t.track, dp(3)));
            dots.addView(d, Ui.margins(new LayoutParams(tp(12), tp(5)), i == 0 ? 0 : tp(4), 0, 0, 0));
        }
        FrameLayout.LayoutParams dl = new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        dl.bottomMargin = tp(14);
        f.addView(dots, dl);
        styleHeat(f, -1);
        return f;
    }

    /** Livello 0..3: tacche arancioni accese, icona arancione se acceso; -1 = non disponibile (attenuato). */
    private void styleHeat(FrameLayout f, int level) {
        Integer prev = (Integer) f.getTag();
        if (prev != null && prev == level) return;
        f.setTag(level);
        f.setAlpha(level < 0 ? 0.45f : 1f);
        f.setBackground(Ui.pressable(getContext(), t.chip, 20 * Ui.K, t));
        int heat = 0xFFFF7A3D;
        ((ImageView) f.getChildAt(0)).setColorFilter(level > 0 ? heat : t.text);
        LinearLayout dots = (LinearLayout) f.getChildAt(1);
        for (int i = 0; i < 3; i++) dots.getChildAt(i).setBackground(Ui.round(i < level ? heat : t.track, dp(3)));
    }

    /** Pulsante a commutazione con testo (A/C, AUTO): acceso = colori invertiti. */
    private TextView toggleText(Context c, String label, String desc, View.OnClickListener l) {
        TextView b = Ui.text(c, label, ts(label.length() > 3 ? 17 : 20), t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setContentDescription(desc);
        b.setClickable(true);
        b.setOnClickListener(l);
        styleToggle(b, -1);
        return b;
    }

    private FrameLayout toggleIcon(Context c, int icon, String desc, View.OnClickListener l) {
        FrameLayout f = Ui.iconButton(c, t, icon, t.text, t.chip, 76, 32, 20, desc, l);
        styleToggle(f, -1);
        return f;
    }

    /** state: 1 acceso, 0 spento, -1 sconosciuto/non disponibile (attenuato). onColor = colore dell'icona da acceso. */
    private void styleToggle(View v, int state) {
        styleToggle(v, state, 0);
    }

    private void styleToggle(View v, int state, int onColor) {
        boolean on = state == 1;
        Integer prev = (Integer) v.getTag();
        if (prev != null && prev == state) return;
        v.setTag(state);
        v.setAlpha(state < 0 ? 0.45f : 1f);
        if (onColor != 0) {
            // fendinebbia: icona colorata come la spia del quadro (verde anteriori, ambra posteriori)
            v.setBackground(Ui.pressable(getContext(), t.chip, 20 * Ui.K, t));
            ((ImageView) ((FrameLayout) v).getChildAt(0)).setColorFilter(on ? onColor : t.text);
            return;
        }
        v.setBackground(on ? Ui.round(t.text, tp(20)) : Ui.pressable(getContext(), t.chip, 20 * Ui.K, t));
        if (v instanceof TextView) ((TextView) v).setTextColor(on ? t.panel : t.text);
        else ((ImageView) ((FrameLayout) v).getChildAt(0)).setColorFilter(on ? t.panel : t.text);
    }

    private View shortcut(Context c, int icon, String desc, View.OnClickListener l) {
        FrameLayout f = Ui.iconButton(c, t, icon, t.text, 0, 64, 30, 18, desc, l);
        f.setLayoutParams(Ui.margins(Ui.lp(tp(64), tp(64)), dp(6), 0, 0, 0));
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
