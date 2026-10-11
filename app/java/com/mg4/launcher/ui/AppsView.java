package com.mg4.launcher.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.mg4.launcher.R;
import com.mg4.launcher.apps.AppCatalog;
import com.mg4.launcher.apps.IconStyler;

import java.util.List;

/** Griglia di tutte le app: sezione "Auto" (icone disegnate) e "Installate da te" (icone ridisegnate in automatico). */
public final class AppsView extends LinearLayout {
    private static final int COLUMNS = 9;

    public interface Host {
        void closeApps();

        void openSettings();

        String navPackageHint();
    }

    private final Theme t;
    private final AppCatalog catalog;
    private final Host host;
    private final LinearLayout content;
    private final Handler main = new Handler(Looper.getMainLooper());

    public AppsView(Context c, Theme t, AppCatalog catalog, Host host) {
        super(c);
        this.t = t;
        this.catalog = catalog;
        this.host = host;
        setOrientation(VERTICAL);
        setBackgroundColor(t.bg);
        int pad = dp(20);
        setPadding(pad, pad, pad, pad);

        LinearLayout head = Ui.row(c);
        head.setPadding(dp(8), dp(4), dp(8), dp(4));
        head.addView(Ui.iconButton(c, t, R.drawable.ic_chev_left, t.text, t.panel, 60, 28, 18, "Torna alla home",
                v -> host.closeApps()));
        head.addView(Ui.text(c, "App", 32, t.text, Ui.MEDIUM), Ui.margins(Ui.lp(-2, -2), dp(16), 0, 0, 0));
        head.addView(Ui.spacer(c));
        head.addView(Ui.iconButton(c, t, R.drawable.ic_palette, t.text, t.panel, 60, 28, 18, "Impostazioni del launcher",
                v -> host.openSettings()));
        addView(head);

        ScrollView sv = new ScrollView(c);
        sv.setBackground(Ui.round(t.panel, dp(24)));
        sv.setClipToOutline(true);
        sv.setVerticalScrollBarEnabled(false);
        content = Ui.column(c);
        content.setPadding(dp(32), dp(20), dp(32), dp(24));
        sv.addView(content, new FrameLayout.LayoutParams(-1, -2));
        addView(sv, Ui.margins(Ui.lpWeight(-1, 0, 1), 0, dp(16), 0, 0));
        reload();
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    /** Ricarica l'elenco (chiamato anche quando si installa o si rimuove un'app). */
    public void reload() {
        Context c = getContext();
        content.removeAllViews();
        content.addView(section(c, "AUTO", null));
        addGrid(c, catalog.carApps(host.navPackageHint()));
        List<AppCatalog.App> mine = catalog.userApps();
        content.addView(section(c, "INSTALLATE DA TE",
                mine.isEmpty() ? "le app che installi compaiono qui da sole" : "compaiono qui da sole, con l'icona nello stesso stile"),
                Ui.margins(new LayoutParams(-1, -2), 0, dp(10), 0, 0));
        if (!mine.isEmpty()) addGrid(c, mine);
    }

    private View section(Context c, String title, String note) {
        LinearLayout r = Ui.row(c);
        r.setGravity(Gravity.BOTTOM);
        TextView tv = Ui.text(c, title, 15, t.sub, Ui.MEDIUM);
        tv.setLetterSpacing(0.07f);
        r.addView(tv);
        if (note != null) r.addView(Ui.text(c, note, 14, t.sub, Ui.REGULAR), Ui.margins(Ui.lp(-2, -2), dp(12), 0, 0, 0));
        r.setPadding(0, 0, 0, dp(6));
        return r;
    }

    private void addGrid(Context c, List<AppCatalog.App> apps) {
        LinearLayout row = null;
        for (int i = 0; i < apps.size(); i++) {
            if (i % COLUMNS == 0) {
                row = Ui.row(c);
                content.addView(row, new LayoutParams(-1, dp(124)));
            }
            row.addView(tile(c, apps.get(i)), Ui.lpWeight(0, -1, 1));
        }
        if (row != null) {
            for (int k = apps.size() % COLUMNS; k != 0 && k < COLUMNS; k++) row.addView(new View(c), Ui.lpWeight(0, -1, 1));
        }
    }

    private View tile(Context c, AppCatalog.App app) {
        LinearLayout col = Ui.column(c);
        col.setGravity(Gravity.CENTER);
        col.setBackground(Ui.pressable(c, 0, 20, t));
        col.setClickable(true);
        col.setContentDescription(app.label);
        col.setOnClickListener(v -> {
            if (catalog.launch(app)) host.closeApps();
        });

        FrameLayout box = new FrameLayout(c);
        int boxBg = app.highlight ? t.accent : t.chip;
        int fg = app.highlight ? t.onAccent : t.text;
        box.setBackground(Ui.round(boxBg, dp(22)));
        ImageView iv = new ImageView(c);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        if (app.iconRes != 0) {
            iv.setImageResource(app.iconRes);
            iv.setColorFilter(fg);
            box.addView(iv, new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER));
        } else {
            box.addView(iv, new FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER));
            // icona ridisegnata in background (può richiedere qualche millisecondo per app)
            new Thread(() -> {
                Drawable d = catalog.originalIcon(app);
                Bitmap b = IconStyler.style(app.packageName + "/" + app.label, d, fg);
                boolean glyph = IconStyler.isGlyph(b);
                main.post(() -> {
                    iv.setImageBitmap(b);
                    if (!glyph) {
                        box.setBackground(null);
                        iv.setLayoutParams(new FrameLayout.LayoutParams(dp(72), dp(72), Gravity.CENTER));
                    }
                });
            }, "mg4-icon").start();
        }
        col.addView(box, new LayoutParams(dp(72), dp(72)));
        TextView label = Ui.oneLine(Ui.text(c, app.label, 16, t.text, Ui.MEDIUM));
        label.setGravity(Gravity.CENTER);
        col.addView(label, Ui.margins(new LayoutParams(-1, -2), dp(4), dp(10), dp(4), 0));
        return col;
    }
}
