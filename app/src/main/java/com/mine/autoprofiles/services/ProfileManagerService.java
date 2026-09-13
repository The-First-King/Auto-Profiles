package com.mine.autoprofiles.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import com.mine.autoprofiles.utils.ProfileSwitcher;

public class ProfileManagerService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Kept for callers like CellTowerReceiver. Delegates to ProfileSwitcher so all
     * profile switching uses the working setActiveProfile(UUID) path with full logging.
     */
    public static void switchProfile(Context context, String profileName) {
        ProfileSwitcher.switchTo(context, profileName);
    }
}
