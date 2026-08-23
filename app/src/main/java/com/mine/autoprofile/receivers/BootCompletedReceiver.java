package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.mine.autoprofile.services.ProfileManagerService;

public class BootCompletedReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            Intent serviceIntent = new Intent(context, ProfileManagerService.class);
            context.startForegroundService(serviceIntent);
        }
    }
}
