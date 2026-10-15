package com.mg4.launcher.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Conferma a tutto schermo nello stile del launcher: titolo, spiegazione, "Annulla" e pulsante principale. */
public final class Confirm {
    private Confirm() {}

    public static void show(FrameLayout root, Theme t, String title, String msg, String cancel, String ok, Runnable onOk) {
        Context c = root.getContext();
        FrameLayout dim = new FrameLayout(c);
        dim.setBackgroundColor(t.day ? 0x66000000 : 0xB3000000);
        dim.setClickable(true);
        LinearLayout card = Ui.column(c);
        card.setBackground(Ui.round(t.panel, Ui.dp(c, 28)));
        card.setPadding(Ui.dp(c, 40), Ui.dp(c, 34), Ui.dp(c, 40), Ui.dp(c, 30));
        card.setClickable(true);
        card.addView(Ui.text(c, title, 30, t.text, Ui.MEDIUM));
        TextView m = Ui.text(c, msg, 20, t.sub, Ui.REGULAR);
        m.setLineSpacing(0, 1.15f);
        card.addView(m, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 16), 0, 0));
        LinearLayout btns = Ui.row(c);
        btns.setGravity(Gravity.END);
        btns.addView(button(c, t, cancel, false, v -> ((ViewGroup) dim.getParent()).removeView(dim)), Ui.lp(-2, Ui.tp(c, 60)));
        btns.addView(button(c, t, ok, true, v -> {
            ((ViewGroup) dim.getParent()).removeView(dim);
            onOk.run();
        }), Ui.margins(Ui.lp(-2, Ui.tp(c, 60)), Ui.dp(c, 12), 0, 0, 0));
        card.addView(btns, Ui.margins(new LinearLayout.LayoutParams(-1, -2), 0, Ui.dp(c, 28), 0, 0));
        dim.addView(card, new FrameLayout.LayoutParams(Ui.dp(c, 980), -2, Gravity.CENTER));
        root.addView(dim, new FrameLayout.LayoutParams(-1, -1));
    }

    private static TextView button(Context c, Theme t, String text, boolean primary, View.OnClickListener l) {
        TextView b = Ui.text(c, text, Ui.ts(20), primary ? t.panel : t.text, Ui.MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setPadding(Ui.tp(c, 28), 0, Ui.tp(c, 28), 0);
        b.setBackground(Ui.pressable(c, primary ? t.text : t.chip, 16 * Ui.K, t));
        b.setClickable(true);
        b.setOnClickListener(l);
        return b;
    }
}
