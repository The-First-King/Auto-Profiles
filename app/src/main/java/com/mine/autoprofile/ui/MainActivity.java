package com.mine.autoprofile.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mine.autoprofile.R;
import com.mine.autoprofile.services.TriggerMonitorService;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private Object mProfileManagerInstance = null;
    private Class<?> mProfileManagerClass = null;
    private Class<?> mProfileClass = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start background monitoring service when app opens
        Intent serviceIntent = new Intent(this, TriggerMonitorService.class);
        startService(serviceIntent);

        // Initialize the LineageOS Profile Manager via Reflection & DexClassLoader
        initProfileManager();

        // Find the FAB and set its click listener
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showProfileSelectionDialog());
    }

    private void initProfileManager() {
        try {
            // 1. Try standard reflection (works on older LineageOS versions)
            mProfileManagerClass = Class.forName("lineageos.app.ProfileManager");
            mProfileClass = Class.forName("lineageos.app.Profile");
        } catch (ClassNotFoundException e1) {
            try {
                // 2. Fallback: Force-load the jar directly (bypasses Android 12 restrictions)
                String libPath = "/system/framework/org.lineageos.platform.jar";
                dalvik.system.DexClassLoader classLoader = new dalvik.system.DexClassLoader(
                        libPath, getCodeCacheDir().getAbsolutePath(), null, getClass().getClassLoader());

                mProfileManagerClass = classLoader.loadClass("lineageos.app.ProfileManager");
                mProfileClass = classLoader.loadClass("lineageos.app.Profile");
            } catch (Exception e2) {
                Log.e("AutoProfile", "DexClassLoader also failed to find the LineageOS jar.", e2);
                mProfileManagerInstance = null;
                return; // Stop here if both methods fail
            }
        }

        try {
            // 3. If the classes were found, initialize the ProfileManager
            Method getInstanceMethod = mProfileManagerClass.getMethod("getInstance", Context.class);
            mProfileManagerInstance = getInstanceMethod.invoke(null, this);
            Log.i("AutoProfile", "Successfully initialized LineageOS ProfileManager!");
        } catch (Exception e) {
            Log.e("AutoProfile", "Failed to invoke ProfileManager.getInstance().", e);
            mProfileManagerInstance = null;
        }
    }

    private void showProfileSelectionDialog() {
        if (mProfileManagerInstance == null) {
            Toast.makeText(this, "LineageOS Profiles API is not available on this device.", Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Check if profiles are enabled at the OS level
            Method isProfilesEnabledMethod = mProfileManagerClass.getMethod("isProfilesEnabled");
            boolean isEnabled = (Boolean) isProfilesEnabledMethod.invoke(mProfileManagerInstance);

            if (!isEnabled) {
                Toast.makeText(this, "LineageOS Profiles are not enabled.", Toast.LENGTH_LONG).show();
                return;
            }

            // Fetch the array of existing profiles
            Method getProfilesMethod = mProfileManagerClass.getMethod("getProfiles");
            Object[] profiles = (Object[]) getProfilesMethod.invoke(mProfileManagerInstance);

            if (profiles == null || profiles.length == 0) {
                Toast.makeText(this, "No LineageOS profiles found.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Extract names to display in the list
            Method getNameMethod = mProfileClass.getMethod("getName");
            String[] profileNames = new String[profiles.length];
            for (int i = 0; i < profiles.length; i++) {
                profileNames[i] = (String) getNameMethod.invoke(profiles[i]);
            }

            // Display the list in a dialog
            new AlertDialog.Builder(this)
                    .setTitle("Select LineageOS Profile")
                    .setItems(profileNames, (dialog, which) -> {
                        String selectedProfileName = profileNames[which];
                        // TODO: Navigate to a "Create Rule" screen passing the selected profile UUID/Name
                        Toast.makeText(this, "Selected: " + selectedProfileName, Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (Exception e) {
            Log.e("AutoProfile", "Error accessing LineageOS profiles", e);
            Toast.makeText(this, "Failed to load profiles. Check logs.", Toast.LENGTH_SHORT).show();
        }
    }
}
