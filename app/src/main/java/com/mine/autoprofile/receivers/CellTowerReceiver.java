package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CellTowerReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.intent.action.SERVICE_STATE".equals(intent.getAction())) {
            // TODO: Extract cell tower information
            // TODO: Check if any rules match this cell tower
            // TODO: Activate matching profile if needed
        }
    }
}
