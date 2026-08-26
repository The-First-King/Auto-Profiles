package com.mine.autoprofile.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Profile;
import java.lang.reflect.Method;
import java.util.concurrent.Executors;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        long profileId = intent.getLongExtra("PROFILE_ID", -1);
        
        if (profileId == -1) return;

        Log.i("AutoProfile", "Schedule triggered! Applying Profile ID: " + profileId);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            Profile profile = db.profileDao().getProfileById(profileId);
            
            if (profile != null) {
                applyLineageProfile(context, profile.getName());
            }
        });
    }

    private void applyLineageProfile(Context context, String profileName) {
        try {
            // Force-load the jar directly as we did in MainActivity
            String libPath = "/system/framework/org.lineageos.platform.jar";
            dalvik.system.DexClassLoader classLoader = new dalvik.system.DexClassLoader(
                    libPath, context.getCodeCacheDir().getAbsolutePath(), null, context.getClass().getClassLoader());

            Class<?> profileManagerClass = classLoader.loadClass("lineageos.app.ProfileManager");
            Method getInstanceMethod = profileManagerClass.getMethod("getInstance", Context.class);
            Object profileManagerInstance = getInstanceMethod.invoke(null, context);

            if (profileManagerInstance != null) {
                Method setActiveProfileMethod = profileManagerClass.getMethod("setActiveProfile", String.class);
                setActiveProfileMethod.invoke(profileManagerInstance, profileName);
                
                Log.i("AutoProfile", "Successfully applied profile: " + profileName);
            }
        } catch (Exception e) {
            Log.e("AutoProfile", "Failed to apply profile via Alarm", e);
        }
    }
}
