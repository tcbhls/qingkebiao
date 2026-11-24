package com.qingkebiao.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RefreshReceiver extends BroadcastReceiver {

    public static final String ACTION_REFRESH = "com.qingkebiao.app.ACTION_REFRESH";

    @Override
    public void onReceive(final Context context, Intent intent) {
        final PendingResult pr = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (SchoolProfile.isCasAuto(context)
                            && !Prefs.user(context).isEmpty()
                            && !SchoolProfile.customUrl(context).isEmpty()) {
                        Repo.refresh(context);
                    }
                } catch (Throwable ignored) {
                } finally {
                    try {
                        WidgetProvider.updateAll(context);
                    } catch (Throwable ignored) {
                    }
                    pr.finish();
                }
            }
        }).start();
    }
}
