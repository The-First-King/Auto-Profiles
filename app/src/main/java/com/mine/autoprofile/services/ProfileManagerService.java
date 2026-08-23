package com.mine.autoprofile.services;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;
import java.lang.reflect.Method;

public class ProfileManagerService extends Service {
    private static final String TAG = "ProfileManagerService";

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public static void switchProfile(Context context, String profileName) {
        try {
            Class<?> profileManagerClass = Class.forName("lineageos.app.ProfileManager");
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            Object profileManagerInstance = getInstanceMethod.invoke(null, context);

            if (profileManagerInstance != null) {
                // Compatible with LineageOS profile string configurations via reflection
                Method setActiveProfileMethod = profileManagerClass.getMethod("setActiveProfile", String.class);
                setActiveProfileMethod.invoke(profileManagerInstance, profileName);
                Log.d(TAG, "Successfully switched LineageOS profile to: " + profileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "LineageOS ProfileManager reflection failed. Are you running LineageOS?", e);
        }
    }
}
