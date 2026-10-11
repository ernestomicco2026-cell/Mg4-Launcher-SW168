package com.mg4.launcher.svc;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;

/** Collegamento a un servizio di sistema con riconnessione automatica (servizio riavviato o non ancora pronto). */
public final class ServiceLink implements ServiceConnection, IBinder.DeathRecipient {
    private static final String TAG = "MG4L.Link";
    private static final long RETRY_MS = 3000;

    private final Context context;
    private final Intent intent;
    private final String name;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private volatile IBinder binder;
    private boolean wanted;
    private Runnable onConnected;

    public ServiceLink(Context context, String name, Intent intent) {
        this.context = context.getApplicationContext();
        this.name = name;
        this.intent = intent;
    }

    public static Intent byAction(String pkg, String action) {
        return new Intent(action).setPackage(pkg);
    }

    public static Intent byComponent(String pkg, String cls, String action) {
        Intent i = new Intent().setComponent(new ComponentName(pkg, cls));
        if (action != null) i.setAction(action);
        return i;
    }

    public void setOnConnected(Runnable r) {
        onConnected = r;
    }

    public IBinder binder() {
        IBinder b = binder;
        return (b != null && b.isBinderAlive()) ? b : null;
    }

    public boolean isConnected() {
        return binder() != null;
    }

    public void start() {
        wanted = true;
        bind();
    }

    public void stop() {
        wanted = false;
        handler.removeCallbacksAndMessages(null);
        try {
            context.unbindService(this);
        } catch (Exception ignored) {
        }
        binder = null;
    }

    private void bind() {
        if (!wanted) return;
        boolean ok;
        try {
            ok = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
        } catch (Exception e) {
            ok = false;
            Diag.error(name, "bindService: " + e);
        }
        Diag.put(name + ".bind", ok ? "ok" : "fallito");
        if (!ok) handler.postDelayed(this::bind, RETRY_MS * 3);
    }

    @Override
    public void onServiceConnected(ComponentName cn, IBinder service) {
        binder = service;
        Diag.put(name + ".stato", "collegato");
        try {
            service.linkToDeath(this, 0);
        } catch (RemoteException e) {
            Log.w(TAG, name + " linkToDeath", e);
        }
        if (onConnected != null) onConnected.run();
    }

    @Override
    public void onServiceDisconnected(ComponentName cn) {
        binder = null;
        Diag.put(name + ".stato", "scollegato");
    }

    @Override
    public void binderDied() {
        binder = null;
        Diag.put(name + ".stato", "servizio riavviato");
        handler.post(() -> {
            try {
                context.unbindService(this);
            } catch (Exception ignored) {
            }
            handler.postDelayed(this::bind, RETRY_MS);
        });
    }
}
