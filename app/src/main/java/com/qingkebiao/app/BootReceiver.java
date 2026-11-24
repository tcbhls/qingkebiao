package com.qingkebiao.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机或覆盖安装后恢复定时调度并重绘桌面小部件 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Scheduler.schedule(context);
        WidgetProvider.updateAllAsync(context);
    }
}
