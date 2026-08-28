package com.mine.autoprofile.ui;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mine.autoprofile.R;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.FullRule;
import com.mine.autoprofile.models.Profile;
import com.mine.autoprofile.services.TriggerMonitorService;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Object mProfileManagerInstance = null;
    private Class<?> mProfileManagerClass = null;
    private Class<?> mProfileClass = null;

    private RuleAdapter ruleAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start background monitoring service when app opens
        Intent serviceIntent = new Intent(this, TriggerMonitorService.class);
        startService(serviceIntent);

        // Initialize the LineageOS Profile Manager via Reflection & DexClassLoader
        initProfileManager();

        // On Android 14 (targetSdk 34) SCHEDULE_EXACT_ALARM is DENIED by default.
        // Without it, schedule rules never fire precisely (or, previously, at all).
        ensureExactAlarmPermission();

        // Initialize the RecyclerView for displaying saved rules
        setupRecyclerView();

        // Find the FAB and set its click listener
        FloatingActionButton fab = findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(v -> showProfileSelectionDialog());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh the list of rules every time we return to this screen
        loadRulesFromDatabase();
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            
            // Pass the interface listener we created in RuleAdapter
            ruleAdapter = new RuleAdapter(new RuleAdapter.OnRuleClickListener() {
                @Override
                public void onToggleRule(FullRule rule, boolean isChecked) {
                    Toast.makeText(MainActivity.this, "Toggled: " + isChecked, Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onEditRule(FullRule rule) {
                    Toast.makeText(MainActivity.this, "Edit coming soon", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onDeleteRule(FullRule rule) {
                    Toast.makeText(MainActivity.this, "Delete coming soon", Toast.LENGTH_SHORT).show();
                }
            });
            recyclerView.setAdapter(ruleAdapter);
        }
    }

    private void loadRulesFromDatabase() {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            List<FullRule> rules = db.ruleDao().getAllRulesWithDetails();
            runOnUiThread(() -> {
                if (ruleAdapter != null) {
                    ruleAdapter.setRules(rules);
                }
            });
        });
    }

    private void ensureExactAlarmPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;

        AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null || alarmManager.canScheduleExactAlarms()) return;

        new AlertDialog.Builder(this)
                .setTitle("Permission needed")
                .setMessage("Auto Profiles needs the \"Alarms & reminders\" permission to switch "
                        + "profiles at the exact scheduled time. Without it, switching may be "
                        + "delayed by several minutes.")
                .setPositiveButton("Open Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:" + getPackageName()));
                    startActivity(intent);
                })
                .setNegativeButton("Later", null)
                .show();
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
                        promptForTriggerType(selectedProfileName);
                    })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (Exception e) {
            Log.e("AutoProfile", "Error accessing LineageOS profiles", e);
            Toast.makeText(this, "Failed to load profiles. Check logs.", Toast.LENGTH_SHORT).show();
        }
    }

    private void promptForTriggerType(String profileName) {
        new AlertDialog.Builder(MainActivity.this)
            .setTitle("Create Rule")
            .setMessage("How should '" + profileName + "' be triggered?")
            .setPositiveButton("Schedule", (dialog, which) -> {
                
                // Fetch or Create the profile in our local Room DB before launching ScheduleActivity
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(MainActivity.this);
                    long profileId = -1;
                    
                    try {
                        List<Profile> existingProfiles = db.profileDao().getAllProfiles();
                        for (Profile p : existingProfiles) {
                            if (p.getName() != null && p.getName().equals(profileName)) {
                                profileId = p.getId();
                                break;
                            }
                        }
                        
                        // If it doesn't exist in our DB yet, create it with matching constructor parameters
                        if (profileId == -1) {
                            Profile newProfile = new Profile(profileName, true);
                            profileId = db.profileDao().insert(newProfile);
                        }
                    } catch (Exception e) {
                        Log.e("AutoProfile", "DB Error checking profile", e);
                    }

                    long finalProfileId = profileId;
                    runOnUiThread(() -> {
                        Intent intent = new Intent(MainActivity.this, ScheduleActivity.class);
                        intent.putExtra("PROFILE_ID", finalProfileId);
                        startActivity(intent);
                    });
                });
            })
            .setNegativeButton("Location (GSM)", (dialog, which) -> {
                Toast.makeText(MainActivity.this, "GSM Scanner coming soon!", Toast.LENGTH_SHORT).show();
            })
            .show();
    }
}
