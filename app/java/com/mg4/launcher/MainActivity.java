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
import com.mg4.launcher.svc.VehicleClient;
import com.mg4.launcher.ui.AppsView;
import com.mg4.launcher.ui.HomeView;
import com.mg4.launcher.ui.Prefs;
import com.mg4.launcher.ui.SettingsView;
import com.mg4.launcher.ui.Theme;

/** Home del launcher: costruisce l'interfaccia e collega i servizi dell'auto. */
public final class MainActivity extends Activity implements HomeView.Host, AppsView.Host, SettingsView.Host {

    /** Spazio occupato dal dock MG (a sinistra) e dalla barra di stato (in alto) se la finestra è a schermo intero. */
    private static final int DOCK_WIDTH = 142, STATUS_HEIGHT = 80;

    private Prefs prefs;
    private AppCatalog catalog;
    private VehicleClient vehicle;
    private MediaClient media;
    private NavClient nav;

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
        lastUiMode = getResources().getConfiguration().uiMode;

        root = new FrameLayout(this);
        root.setOnApplyWindowInsetsListener(this::applyInsets);
        root.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol || b - t != ob - ot) v.requestApplyInsets();
        });
        setContentView(root);
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
    }

    /** Se la finestra copre tutto lo schermo lascia libero lo spazio di dock e barra di stato. */
    private WindowInsets applyInsets(View v, WindowInsets in) {
        int w = v.getWidth() > 0 ? v.getWidth() : getResources().getDisplayMetrics().widthPixels;
        int h = v.getHeight() > 0 ? v.getHeight() : getResources().getDisplayMetrics().heightPixels;
        int left = Math.max(in.getSystemWindowInsetLeft(), w >= 1880 ? DOCK_WIDTH : 0);
        int top = Math.max(in.getSystemWindowInsetTop(), h >= 700 ? STATUS_HEIGHT : 0);
        int right = in.getSystemWindowInsetRight(), bottom = in.getSystemWindowInsetBottom();
        v.setPadding(left, top, right, bottom);
        Diag.put("finestra", w + "×" + h + " · margini " + left + "," + top + "," + right + "," + bottom);
        return in.consumeSystemWindowInsets();
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
            apps = new AppsView(this, theme, catalog, this);
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
