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
import android.widget.FrameLayout;

import com.mg4.launcher.apps.AppCatalog;
import com.mg4.launcher.apps.IconStyler;
import com.mg4.launcher.svc.Diag;
import com.mg4.launcher.svc.MediaClient;
import com.mg4.launcher.svc.NavClient;
import com.mg4.launcher.svc.Updater;
import com.mg4.launcher.svc.VehicleClient;
import com.mg4.launcher.ui.AppsView;
import com.mg4.launcher.ui.HomeView;
import com.mg4.launcher.ui.Prefs;
import com.mg4.launcher.ui.SettingsView;
import com.mg4.launcher.ui.Theme;

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

    private FrameLayout root;
    private Theme theme;
    private HomeView home;
    private AppsView apps;
    private SettingsView settings;
    private int lastUiMode;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = new Prefs(this);
        catalog = new AppCatalog(this);
        vehicle = new VehicleClient(this);
        media = new MediaClient(this);
        nav = new NavClient(this);
        updater = new Updater(this);
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
        });
        media.setListener(s -> {
            if (home != null) home.onMedia(s);
        });
        nav.setListener(s -> {
            if (home != null) home.onNav(s);
        });
        vehicle.start();
        media.start();
        nav.start();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addAction(Intent.ACTION_PACKAGE_REPLACED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        // all'avvio dell'auto le app utente compaiono solo dopo lo sblocco dell'utente Android
        registerReceiver(packages, new IntentFilter(Intent.ACTION_USER_UNLOCKED));
    }

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
        root.setBackgroundColor(theme.bg);
        root.removeAllViews();
        home = new HomeView(this, theme, prefs, this, vehicle, media, nav);
        root.addView(home, new FrameLayout.LayoutParams(-1, -1));
        apps = null;
        settings = null;
        home.onVehicle(vehicle.last());
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
        media.resume();
        nav.resume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        vehicle.pause();
        media.pause();
        nav.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        vehicle.stop();
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
        else if (apps != null) closeApps();
        // altrimenti il launcher resta dov'è
    }

    @Override
    public void onConfigurationChanged(Configuration cfg) {
        super.onConfigurationChanged(cfg);
        if (cfg.uiMode != lastUiMode) {
            lastUiMode = cfg.uiMode;
            if (Prefs.THEME_AUTO.equals(prefs.themeMode())) {
                IconStyler.clearCache();
                build();
            }
        }
    }

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
        if (apps == null) {
            apps = new AppsView(this, theme, catalog, prefs, this);
            root.addView(apps, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    @Override
    public void openSettings() {
        if (settings == null) {
            settings = new SettingsView(this, theme, prefs, this);
            root.addView(settings, new FrameLayout.LayoutParams(-1, -1));
        }
    }

    @Override
    public void launch(String pkg, String cls) {
        catalog.launchComponent(pkg, cls);
    }

    @Override
    public void launchNavigator() {
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

    // --- AppsView.Host ------------------------------------------------------------------------

    @Override
    public void closeApps() {
        if (apps != null) {
            root.removeView(apps);
            apps = null;
        }
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
            boolean appsOpen = apps != null;
            IconStyler.clearCache();
            build();
            if (appsOpen) openApps();
            openSettings();
        } else if (home != null) {
            home.applyCarLook();
        }
    }

    @Override
    public Updater updater() {
        return updater;
    }

    @Override
    public void openOriginalHome() {
        try {
            startActivity(new Intent(Intent.ACTION_MAIN)
                    .setComponent(new ComponentName("com.saicmotor.hmi.launcher", "com.saicmotor.hmi.launcher.ui.MainActivity"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            Diag.error("home originale", e.toString());
        }
    }
}
