package com.mg4.launcher;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.mg4.launcher.apps.AppCatalog;
import com.mg4.launcher.apps.IconStyler;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.MediaClient;
import com.mg4.launcher.svc.NavClient;
import com.mg4.launcher.svc.Updater;
import com.mg4.launcher.svc.VehicleClient;
import com.mg4.launcher.ui.AppsView;
import com.mg4.launcher.ui.HomeView;
import com.mg4.launcher.ui.Pager;
import com.mg4.launcher.ui.Prefs;
import com.mg4.launcher.ui.SettingsView;
import com.mg4.launcher.ui.Theme;
import com.mg4.launcher.ui.Ui;

/** Home del launcher: costruisce l'interfaccia e collega i servizi dell'auto. */
public final class MainActivity extends Activity implements HomeView.Host, AppsView.Host, SettingsView.Host {

    /** Schermo intero: nasconde barra di stato e dock MG (la SystemUI SAIC segue i flag standard). */
    private static final int IMMERSIVE = View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;

    private Prefs prefs;
    private AppCatalog catalog;
    private VehicleClient vehicle;
    private MediaClient media;
    private NavClient nav;
    private Updater updater;
    private com.mg4.launcher.svc.LogUploader logs;

    private FrameLayout root;
    private Theme theme;
    private HomeView home;
    private AppsView apps;
    private Pager pager;
    private com.mg4.launcher.svc.ConnClient conn;
    private SettingsView settings;
    private int lastUiMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        L.init(this, prefs.language());
        catalog = new AppCatalog(this);
        vehicle = new VehicleClient(this);
        conn = new com.mg4.launcher.svc.ConnClient(this);
        conn.setListener(st -> {
            if (home != null) home.onConn(st);
        });
        media = new MediaClient(this);
        nav = new NavClient(this);
        updater = new Updater(this);
        logs = new com.mg4.launcher.svc.LogUploader(this);
        lastUiMode = getResources().getConfiguration().uiMode;

        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener(this::applyInsets);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.requestApplyInsets();
        });
        setContentView(root);
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(vis -> {
            // se il sistema rimostra le barre (es. dopo un popup) le rinasconde
            if ((vis & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) root.postDelayed(this::goFullscreen, 2500);
        });
        goFullscreen();
        build();

        vehicle.setListener(s -> {
            if (home != null) home.onVehicle(s);
            // lato di guida letto dall'auto solo dopo l'avvio del CarService: se cambia, si ricostruisce la home
            if (home != null && Prefs.DRIVE_AUTO.equals(prefs.driveSide())) {
                Boolean known = com.mg4.launcher.svc.CarFeatures.knownRhd(this);
                if (known != null && known != home.isRightHand()) {
                    boolean wasSettings = settings != null;
                    build();
                    if (wasSettings) openSettings();
                }
            }
        });
        media.setListener(s -> {
            if (home != null) home.onMedia(s);
        });
        nav.setListener(s -> {
            if (home != null) home.onNav(s);
        });
        vehicle.start();
        conn.start();
        media.start();
        nav.start();
        logs.start();
        // riconferma della home predefinita (un aggiornamento di sistema può averla azzerata)
        new Thread(() -> com.mg4.launcher.svc.HomeDefault.ensure(getApplicationContext()), "mg4-home").start();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        // all'avvio dell'auto le app utente compaiono solo dopo lo sblocco dell'utente Android
        registerReceiver(packages, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
        // orologio nel riquadro batteria: ogni minuto e al cambio di ora / fuso
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(clockTick, tf);
        root.postDelayed(saveDiag, 15000);
        // primo controllo aggiornamenti dopo l'avvio, quando la radio ha finito di caricare
        root.postDelayed(autoCheck, 90_000);
    }

    // ---- popup discreto per i nuovi aggiornamenti ----------------------------------------------

    private static final long CHECK_EVERY = 6 * 60 * 60 * 1000L;   // ogni 6 ore
    private static final long POPUP_SHOW = 20_000;                  // poi si nasconde da solo
    private View updPopup;

    private final Runnable autoCheck = new Runnable() {
        @Override
        public void run() {
            if (prefs.updatePopup()) updater.checkQuiet(prefs.updateRepo(), prefs.githubToken(), r -> showUpdatePopup(r));
            root.postDelayed(this, CHECK_EVERY);
        }
    };

    private final Runnable hidePopup = () -> hideUpdatePopup();

    private void showUpdatePopup(Updater.Release r) {
        if (r == null || updPopup != null || !prefs.updatePopup()) return;
        if (r.tag.equals(prefs.dismissedUpdate()) || updater.isBusy()) return;
        Theme t = theme;
        LinearLayout card = Ui.row(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(24), dp(14), dp(14), dp(14));
        android.graphics.drawable.GradientDrawable bgd = Ui.round(t.panel, dp(22));
        bgd.setStroke(dp(1), t.track);
        card.setBackground(bgd);
        card.setElevation(dp(14));
        card.setClickable(true);   // i tocchi sul popup non passano alla home sotto

        LinearLayout txt = Ui.column(this);
        txt.addView(Ui.text(this, L.s(R.string.upd_popup_title), 18, t.text, Ui.MEDIUM));
        txt.addView(Ui.text(this, L.f(R.string.upd_popup_text, r.tag), 15, t.sub, Ui.REGULAR));
        card.addView(txt, Ui.margins(Ui.lp(-2, -2), 0, 0, dp(28), 0));

        card.addView(popupButton(L.s(R.string.upd_later), t.chip, t.text, v -> {
            prefs.setDismissedUpdate(r.tag);
            hideUpdatePopup();
        }), Ui.margins(Ui.lp(-2, dp(48)), 0, 0, dp(10), 0));
        card.addView(popupButton(L.s(R.string.upd_now), t.accent, t.onAccent, v -> {
            hideUpdatePopup();
            SettingsView.openAt(SettingsView.SEC_SYSTEM);
            closeSettings();
            openSettings();
            updater.install(r, prefs.githubToken());
        }), Ui.lp(-2, dp(48)));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        lp.topMargin = dp(20);
        root.addView(card, lp);
        updPopup = card;
        card.setAlpha(0f);
        card.setTranslationY(-dp(30));
        card.animate().alpha(1f).translationY(0).setDuration(280).start();
        root.removeCallbacks(hidePopup);
        root.postDelayed(hidePopup, POPUP_SHOW);
    }

    private void hideUpdatePopup() {
        root.removeCallbacks(hidePopup);
        View v = updPopup;
        updPopup = null;
        if (v == null) return;
        v.animate().alpha(0f).translationY(-dp(30)).setDuration(220).withEndAction(() -> root.removeView(v)).start();
    }

    private TextView popupButton(String text, int bg, int fg, View.OnClickListener l) {
        TextView b = Ui.text(this, text, 16, fg, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setBackground(Ui.pressable(this, bg, 14, theme));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }

    /** Ogni 15 s salva la diagnostica su file: adb pull /sdcard/Android/data/com.mg4.launcher/files/diagnostica.txt */
    private final Runnable saveDiag = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> Diag.save(MainActivity.this), "mg4-diag").start();
            root.postDelayed(this, 15000);
        }
    };

    /**
     * Schermo intero: il contenuto occupa sempre tutti i 1920×720 px. Le barre di sistema, se ricompaiono
     * per un attimo (trascinando dal bordo), si sovrappongono senza spostare il layout.
     */
    private WindowInsets applyInsets(View v, WindowInsets in) {
        v.setPadding(0, 0, 0, 0);
        Diag.put("finestra", v.getWidth() + "×" + v.getHeight() + " · margini sistema " + in.getSystemWindowInsetLeft() + ","
                + in.getSystemWindowInsetTop() + "," + in.getSystemWindowInsetRight() + "," + in.getSystemWindowInsetBottom());
        return in.consumeSystemWindowInsets();
    }

    private void goFullscreen() {
        View d = getWindow().getDecorView();
        if (d.getSystemUiVisibility() != IMMERSIVE) d.setSystemUiVisibility(IMMERSIVE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        goFullscreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) goFullscreen();
    }

    private void build() {
        theme = Theme.resolve(this, prefs);
        Ui.K = prefs.uiScaleFactor();
        root.setBackgroundColor(theme.bg);
        root.removeAllViews();
        root.removeCallbacks(hidePopup);
        updPopup = null;
        int keepPage = pager != null ? pager.page() : 0;
        pager = new Pager(this);
        home = new HomeView(this, theme, prefs, this, vehicle, media, nav);
        apps = new AppsView(this, theme, catalog, prefs, this);
        pager.addView(home);
        pager.addView(apps);
        pager.setListener(p -> {
            if (p == 1) apps.reload();
        });
        root.addView(pager, new FrameLayout.LayoutParams(-1, -1));
        settings = null;
        pager.show(keepPage, false);
        home.onVehicle(vehicle.last());
        home.onConn(conn.last());
        home.onMedia(media.last());
        home.onNav(nav.last());
        getWindow().setStatusBarColor(theme.bg);
        root.requestApplyInsets();
    }

    @Override
    protected void onStart() {
        super.onStart();
        // il collegamento ai servizi resta attivo; qui si riprendono solo le letture periodiche
        vehicle.resume();
        conn.resume();
        if (home != null) home.updateClock();
        media.resume();
        nav.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        vehicle.pause();
        conn.pause();
        media.pause();
        nav.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        root.removeCallbacks(saveDiag);
        logs.stop();
        root.removeCallbacks(autoCheck);
        root.removeCallbacks(hidePopup);
        vehicle.stop();
        conn.stop();
        try {
            unregisterReceiver(clockTick);
        } catch (Exception ignored) {
        }
        media.stop();
        nav.stop();
        try {
            unregisterReceiver(packages);
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // tasto Home: torna sempre alla schermata principale
        closeSettings();
        closeApps();
    }

    @Override
    public void onBackPressed() {
        if (settings != null) closeSettings();
        else if (pager != null && pager.page() != 0) closeApps();
        // altrimenti il launcher resta dov'è
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        // lingua di sistema cambiata: con "Automatica" il launcher la segue subito
        if (L.init(this, prefs.language())) {
            catalog.refreshLabels();
            build();
            return;
        }
        if (cfg.uiMode != lastUiMode) {
            lastUiMode = cfg.uiMode;
            if (Prefs.THEME_AUTO.equals(prefs.themeMode())) {
                IconStyler.clearCache();
                build();
            }
        }
    }

    private final BroadcastReceiver clockTick = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            if (home != null) home.updateClock();
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            IconStyler.clearCache();
            if (apps != null) apps.reload();
        }
    };

    // --- HomeView.Host ------------------------------------------------------------------------

    @Override
    public void openApps() {
        if (pager != null) pager.show(1, true);
    }

    @Override
    public void openSettings() {
        if (settings == null) {
            settings = new SettingsView(this, theme, prefs, this);
            root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
            if (updPopup != null) updPopup.bringToFront();
        }
    }

    @Override
    public void launch(String pkg, String cls) {
        catalog.launchComponent(pkg, cls);
    }

    /** Come la home originale (EnergyView): ChargeManagementActivity con l'extra "module" = scheda da aprire. */
    @Override
    public void openEnergy(String module) {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .addCategory(Intent.CATEGORY_HOME)
                    .setComponent(new android.content.ComponentName("com.saicmotor.hmi.vehiclesettings",
                            "com.saicmotor.hmi.vehiclesettings.chargemanagement.ui.ChargeManagementActivity"))
                    .putExtra("module", module)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            com.mg4.launcher.svc.Diag.error("energia " + module, e.toString());
        }
    }

    @Override
    public void launchNavigator() {
        launchNavigator(prefs.navApp());
    }

    /** Apre il navigatore scelto: MG, quello del telefono (CarPlay/Android Auto) o un'app installata. */
    @Override
    public void launchNavigator(String which) {
        boolean phone = media.last().deviceType != 0;
        if (Prefs.NAV_PHONE.equals(which) || (Prefs.NAV_AUTO.equals(which) && phone)) {
            if (phone) {
                media.openProjection();
                media.openRemoteNavigation();
                return;
            }
        } else if (!Prefs.NAV_AUTO.equals(which) && !Prefs.NAV_MG.equals(which)) {
            Intent li = getPackageManager().getLaunchIntentForPackage(which);
            if (li != null) {
                try {
                    startActivity(li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    return;
                } catch (Exception e) {
                    Diag.error("navigatore.app", e.toString());
                }
            }
        }
        Intent i = catalog.navIntent(nav.last().mapPackage);
        if (i != null) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (Exception e) {
                Diag.error("navigatore.apri", e.toString());
            }
        }
        nav.openNavigator();
    }

    @Override
    public java.util.List<AppCatalog.App> otherNavApps() {
        return catalog.otherNavApps(nav.last().mapPackage);
    }

    // --- AppsView.Host ------------------------------------------------------------------------

    @Override
    public void closeApps() {
        if (pager != null) pager.show(0, true);
    }

    @Override
    public String navPackageHint() {
        return nav.last().mapPackage;
    }

    // --- SettingsView.Host --------------------------------------------------------------------

    @Override
    public void closeSettings() {
        if (settings != null) {
            root.removeView(settings);
            settings = null;
        }
    }

    @Override
    public void settingsChanged(boolean rebuild) {
        if (rebuild) {
            IconStyler.clearCache();
            build();
            openSettings();
        } else if (home != null) {
            home.applyCarLook();
            home.refreshNav();
            home.onVehicle(vehicle.last());
        }
    }

    @Override
    public void languageChanged() {
        L.init(this, prefs.language());
        catalog.refreshLabels();
        IconStyler.clearCache();
        build();
        openSettings();
    }

    @Override
    public Updater updater() {
        return updater;
    }

    @Override
    public com.mg4.launcher.svc.LogUploader logs() {
        return logs;
    }

    @Override
    public void openOriginalHome() {
        try {
            // aperta apposta: il controllo all'accensione per un po' non deve riportare il nostro launcher
            com.mg4.launcher.svc.BootWatcher.userOpenedOriginal(this);
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.saicmotor.hmi.launcher", "com.saicmotor.hmi.launcher.ui.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Diag.error("home originale", e.toString());
        }
    }
}
