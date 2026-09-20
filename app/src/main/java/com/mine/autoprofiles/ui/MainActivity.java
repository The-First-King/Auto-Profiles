package com.mine.autoprofiles.ui;

import android.Manifest;
import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mine.autoprofiles.R;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.FullRule;
import com.mine.autoprofiles.models.Profile;
import com.mine.autoprofiles.services.TriggerMonitorService;
import com.mine.autoprofiles.utils.AlarmHelper;
import com.mine.autoprofiles.utils.ProfileSwitcher;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private Object mProfileManagerInstance = null;
    private Class<?> mProfileManagerClass = null;
    private Class<?> mProfileClass = null;

    private RuleAdapter ruleAdapter;

    // Sequential permission flow

    private static final int STEP_RUNTIME_PERMS = 0;
    private static final int STEP_EXACT_ALARM = 1;
    private static final int STEP_BATTERY = 2;

    /** Next step to run in onResume after the user returns from Settings; -1 = none. */
    private int pendingPermissionStep = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // The layout has its own green title bar (app_bar_container). Hide the
        // system ActionBar so the title is never shown twice, regardless of
        // which theme the build ends up applying.
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        // Start background monitoring service when app opens.
        // The background monitor runs only while the master switch is ON.
        updateMonitorService(ProfileSwitcher.isMasterEnabled(this));

        // Initialize the LineageOS Profile Manager via reflection.
        initProfileManager();

        // Ask for everything the app needs, one prompt at a time.
        startPermissionStep(STEP_RUNTIME_PERMS);

        // Initialize the RecyclerView for displaying saved rules.
        setupRecyclerView();

        // Master toggle in the green app bar: soft kill switch for the whole app.
        setupMasterSwitch();

        // Find the FAB and set its click listener.
        FloatingActionButton fab = findViewById(R.id.fab);
        if (fab != null) {
            fab.setOnClickListener(v -> showProfileSelectionDialog());
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRulesFromDatabase();
        registerAllAlarms();

        // Continue the permission flow if a step was waiting for the user to
        // come back from a Settings screen.
        if (pendingPermissionStep != -1) {
            int step = pendingPermissionStep;
            pendingPermissionStep = -1;
            startPermissionStep(step);
        }
    }

    /** Runs one step of the permission flow; steps advance each other. */
    private void startPermissionStep(int step) {
        switch (step) {
            case STEP_RUNTIME_PERMS: {
                List<String> needed = new ArrayList<>();

                for (String perm : new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.READ_PHONE_STATE
                }) {
                    if (ContextCompat.checkSelfPermission(this, perm)
                            != PackageManager.PERMISSION_GRANTED) {
                        needed.add(perm);
                    }
                }

                if (Build.VERSION.SDK_INT >= 33
                        && ContextCompat.checkSelfPermission(
                        this,
                        "android.permission.POST_NOTIFICATIONS")
                        != PackageManager.PERMISSION_GRANTED) {
                    needed.add("android.permission.POST_NOTIFICATIONS");
                }

                if (!needed.isEmpty()) {
                    // Flow continues in onRequestPermissionsResult().
                    ActivityCompat.requestPermissions(
                            this,
                            needed.toArray(new String[0]),
                            REQ_RUNTIME_PERMS);
                    return;
                }

                startPermissionStep(STEP_EXACT_ALARM);
                break;
            }

            case STEP_EXACT_ALARM: {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    startPermissionStep(STEP_BATTERY);
                    return;
                }

                AlarmManager alarmManager =
                        (AlarmManager) getSystemService(Context.ALARM_SERVICE);

                if (alarmManager == null || alarmManager.canScheduleExactAlarms()) {
                    startPermissionStep(STEP_BATTERY);
                    return;
                }

                AlertDialog dialog = new AlertDialog.Builder(this)
                        .setTitle("Permission needed")
                        .setMessage(
                                "Auto Profiles needs the \"Alarms & reminders\" permission "
                                        + "to switch profiles at the exact scheduled time. "
                                        + "Without it, switching may be delayed by several minutes.")
                        .setPositiveButton("Open Settings", (d, which) -> {
                            Intent intent = new Intent(
                                    Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                    Uri.parse("package:" + getPackageName()));
                            startActivity(intent);
                        })
                        .setNegativeButton("Later", null)
                        .create();

                // Advance on any outcome: Settings, Later, or back button.
                dialog.setOnDismissListener(d ->
                        continueAfterDialog(STEP_BATTERY));
                dialog.show();
                break;
            }

            case STEP_BATTERY: {
                ensureBatteryExemption();
                break;
            }
        }
    }

    /**
     * Advances the flow after a dialog closes. If the user navigated away
     * (e.g. to a Settings screen), the next step is deferred to onResume so
     * its prompt does not appear on top of Settings.
     */
    private void continueAfterDialog(int nextStep) {
        if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)) {
            startPermissionStep(nextStep);
        } else {
            pendingPermissionStep = nextStep;
        }
    }

    /** Registers alarms for every enabled TIME rule. */
    private void registerAllAlarms() {
        if (!ProfileSwitcher.isMasterEnabled(this)) {
            return;
        }

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            for (FullRule fullRule : db.ruleDao().getAllRulesWithDetails()) {
                if (fullRule.rule != null
                        && fullRule.rule.isEnabled()
                        && fullRule.trigger != null
                        && "TIME".equals(fullRule.trigger.getType())) {
                    AlarmHelper.scheduleAlarm(
                            this,
                            fullRule.rule.getId(),
                            fullRule.rule.getProfileId(),
                            fullRule.trigger.getValue());
                }
            }
        });
    }

    private static final int REQ_RUNTIME_PERMS = 42;

    /** Re-entry point used when location permission is missing. */
    private void ensureRuntimePermissions() {
        startPermissionStep(STEP_RUNTIME_PERMS);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == REQ_RUNTIME_PERMS) {
            for (int i = 0; i < permissions.length; i++) {
                if (Manifest.permission.ACCESS_FINE_LOCATION.equals(permissions[i])) {
                    if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                        updateMonitorService(ProfileSwitcher.isMasterEnabled(this));
                    } else {
                        Toast.makeText(
                                this,
                                "Without location access, GSM location rules will not work.",
                                Toast.LENGTH_LONG).show();
                    }
                }
            }

            // Runtime permissions answered; move on to the next prompt.
            startPermissionStep(STEP_EXACT_ALARM);
        }
    }

    /** Starts/stops the foreground monitor and its status-bar notification. */
    private void updateMonitorService(boolean enabled) {
        Intent serviceIntent = new Intent(this, TriggerMonitorService.class);

        if (enabled) {
            startService(serviceIntent);
        } else {
            stopService(serviceIntent);
        }
    }

    private void setupMasterSwitch() {
        SwitchCompat masterSwitch = findViewById(R.id.master_switch);
        if (masterSwitch == null) {
            return;
        }

        masterSwitch.setChecked(ProfileSwitcher.isMasterEnabled(this));

        masterSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            ProfileSwitcher.setMasterEnabled(this, isChecked);

            // Bring the background monitor and its notification in line
            // with the switch.
            updateMonitorService(isChecked);

            if (isChecked) {
                registerAllAlarms();
                Toast.makeText(
                        this,
                        R.string.app_enabled,
                        Toast.LENGTH_SHORT).show();
            } else {
                // Kill switch: drop all alarms. If any rule is applied right
                // now, restore the previous profile so the phone is not left
                // stuck.
                Executors.newSingleThreadExecutor().execute(() -> {
                    AppDatabase db = AppDatabase.getInstance(this);

                    for (FullRule fullRule : db.ruleDao().getAllRulesWithDetails()) {
                        if (fullRule.rule == null) {
                            continue;
                        }

                        AlarmHelper.cancelAlarms(this, fullRule.rule.getId());
                        ProfileSwitcher.revertIfActive(
                                this,
                                fullRule.rule.getId());
                    }
                });

                Toast.makeText(
                        this,
                        R.string.app_disabled,
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        RecyclerView recyclerView = findViewById(R.id.recycler_view);

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));

            ruleAdapter = new RuleAdapter(new RuleAdapter.OnRuleClickListener() {
                @Override
                public void onToggleRule(FullRule rule, boolean isChecked) {
                    Executors.newSingleThreadExecutor().execute(() -> {
                        AppDatabase db =
                                AppDatabase.getInstance(MainActivity.this);

                        rule.rule.setEnabled(isChecked);
                        db.ruleDao().update(rule.rule);

                        if (isChecked) {
                            if (rule.trigger != null
                                    && "TIME".equals(rule.trigger.getType())) {
                                AlarmHelper.scheduleAlarm(
                                        MainActivity.this,
                                        rule.rule.getId(),
                                        rule.rule.getProfileId(),
                                        rule.trigger.getValue());
                            } else if (rule.trigger != null
                                    && "CELL".equals(rule.trigger.getType())
                                    && ProfileSwitcher.isMasterEnabled(
                                    MainActivity.this)) {
                                // Re-evaluate immediately: we might be at the
                                // location right now.
                                startService(new Intent(
                                        MainActivity.this,
                                        TriggerMonitorService.class));
                            }
                        } else {
                            AlarmHelper.cancelAlarms(
                                    MainActivity.this,
                                    rule.rule.getId());
                            ProfileSwitcher.revertIfActive(
                                    MainActivity.this,
                                    rule.rule.getId());
                        }

                        runOnUiThread(() -> Toast.makeText(
                                MainActivity.this,
                                isChecked ? "Rule enabled" : "Rule disabled",
                                Toast.LENGTH_SHORT).show());
                    });
                }

                @Override
                public void onEditRule(FullRule rule) {
                    if (rule.trigger == null) {
                        return;
                    }

                    Class<?> editor;

                    if ("TIME".equals(rule.trigger.getType())) {
                        editor = ScheduleActivity.class;
                    } else if ("CELL".equals(rule.trigger.getType())) {
                        editor = LocationScanActivity.class;
                    } else {
                        return;
                    }

                    Intent intent = new Intent(MainActivity.this, editor);
                    intent.putExtra("PROFILE_ID", rule.rule.getProfileId());
                    intent.putExtra("RULE_ID", rule.rule.getId());
                    intent.putExtra("TRIGGER_ID", rule.trigger.getId());
                    intent.putExtra("TRIGGER_VALUE", rule.trigger.getValue());
                    intent.putExtra("RULE_NAME", rule.rule.getName());
                    intent.putExtra("RULE_ENABLED", rule.rule.isEnabled());
                    startActivity(intent);
                }

                @Override
                public void onDeleteRule(FullRule rule) {
                    String profileName =
                            rule.profile != null
                                    ? rule.profile.getName()
                                    : "?";

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Delete rule")
                            .setMessage(
                                    "Delete this rule for profile '"
                                            + profileName + "'?")
                            .setPositiveButton(
                                    "Delete",
                                    (d, w) -> Executors
                                            .newSingleThreadExecutor()
                                            .execute(() -> {
                                                // Stop future alarms.
                                                AlarmHelper.cancelAlarms(
                                                        MainActivity.this,
                                                        rule.rule.getId());

                                                // Revert the profile if the
                                                // rule is active.
                                                ProfileSwitcher.revertIfActive(
                                                        MainActivity.this,
                                                        rule.rule.getId());

                                                // Remove the rule and trigger
                                                // from the database.
                                                AppDatabase db =
                                                        AppDatabase.getInstance(
                                                                MainActivity.this);
                                                db.ruleDao().deleteById(
                                                        rule.rule.getId());

                                                if (rule.trigger != null) {
                                                    db.triggerDao().deleteById(
                                                            rule.trigger.getId());
                                                }

                                                // Refresh the list.
                                                runOnUiThread(() -> {
                                                    Toast.makeText(
                                                            MainActivity.this,
                                                            "Rule deleted",
                                                            Toast.LENGTH_SHORT)
                                                            .show();
                                                    loadRulesFromDatabase();
                                                });
                                            }))
                            .setNegativeButton("Cancel", null)
                            .show();
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

                // Welcome/empty message only when there are no rules yet.
                View emptyState = findViewById(R.id.empty_state);

                if (emptyState != null) {
                    emptyState.setVisibility(
                            rules.isEmpty() ? View.VISIBLE : View.GONE);
                }
            });
        });
    }

    /**
     * Asks the system to exclude the app from battery optimization
     * ("Unrestricted"). Doze otherwise defers the monitor's periodic cell
     * refresh and can throttle telephony callbacks precisely when the phone
     * is stationary with the screen off.
     */
    private void ensureBatteryExemption() {
        PowerManager pm =
                (PowerManager) getSystemService(Context.POWER_SERVICE);

        if (pm == null
                || pm.isIgnoringBatteryOptimizations(getPackageName())) {
            return;
        }

        try {
            // System dialog: "Allow Auto Profiles to always run in background?"
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) {
            Log.e(
                    "AutoProfile",
                    "Battery exemption request failed, opening list instead",
                    e);

            try {
                startActivity(new Intent(
                        Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
                // No fallback activity is available.
            }
        }
    }

    /**
     * Initializes the LineageOS ProfileManager through normal class loading.
     *
     * F-Droid rejects DexClassLoader because it dynamically loads executable
     * code from a system path. On non-LineageOS devices, the classes are not
     * available and the feature is disabled gracefully.
     */
    private void initProfileManager() {
        try {
            mProfileManagerClass =
                    Class.forName("lineageos.app.ProfileManager");
            mProfileClass =
                    Class.forName("lineageos.app.Profile");
        } catch (ClassNotFoundException e) {
            Log.i(
                    "AutoProfile",
                    "LineageOS System Profiles API is not available "
                            + "on this device.");

            mProfileManagerClass = null;
            mProfileClass = null;
            mProfileManagerInstance = null;
            return;
        }

        try {
            Method getInstanceMethod = mProfileManagerClass.getMethod(
                    "getInstance",
                    Context.class);

            mProfileManagerInstance =
                    getInstanceMethod.invoke(null, this);

            if (mProfileManagerInstance == null) {
                Log.e(
                        "AutoProfile",
                        "ProfileManager.getInstance() returned null.");
                return;
            }

            Log.i(
                    "AutoProfile",
                    "Successfully initialized LineageOS ProfileManager!");
        } catch (Exception e) {
            Log.e(
                    "AutoProfile",
                    "Failed to invoke ProfileManager.getInstance().",
                    e);
            mProfileManagerInstance = null;
        }
    }

    private void showProfileSelectionDialog() {
        if (mProfileManagerInstance == null
                || mProfileManagerClass == null
                || mProfileClass == null) {
            Toast.makeText(
                    this,
                    "LineageOS System Profiles API is not available "
                            + "on this device",
                    Toast.LENGTH_LONG).show();
            return;
        }

        try {
            // Check if profiles are enabled at the OS level.
            Method isProfilesEnabledMethod =
                    mProfileManagerClass.getMethod("isProfilesEnabled");

            boolean isEnabled =
                    (Boolean) isProfilesEnabledMethod.invoke(
                            mProfileManagerInstance);

            if (!isEnabled) {
                Toast.makeText(
                        this,
                        "LineageOS System Profiles are not enabled",
                        Toast.LENGTH_LONG).show();
                return;
            }

            // Fetch the array of existing profiles.
            Method getProfilesMethod =
                    mProfileManagerClass.getMethod("getProfiles");

            Object[] profiles =
                    (Object[]) getProfilesMethod.invoke(
                            mProfileManagerInstance);

            if (profiles == null || profiles.length == 0) {
                Toast.makeText(
                        this,
                        "No LineageOS System profiles found",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Extract names to display in the list.
            Method getNameMethod = mProfileClass.getMethod("getName");
            String[] profileNames = new String[profiles.length];

            for (int i = 0; i < profiles.length; i++) {
                profileNames[i] =
                        (String) getNameMethod.invoke(profiles[i]);
            }

            // Display the list in a dialog.
            new AlertDialog.Builder(this)
                    .setTitle("Select the System Profile")
                    .setItems(
                            profileNames,
                            (dialog, which) -> {
                                String selectedProfileName =
                                        profileNames[which];
                                promptForTriggerType(selectedProfileName);
                            })
                    .setNegativeButton("Cancel", null)
                    .show();

        } catch (Exception e) {
            Log.e(
                    "AutoProfile",
                    "Error accessing LineageOS System Profiles",
                    e);

            Toast.makeText(
                    this,
                    "Failed to load profiles (check logs)",
                    Toast.LENGTH_SHORT).show();
        }
    }

    private void promptForTriggerType(String profileName) {
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Create Rule")
                .setMessage(
                        "How should '" + profileName + "' be triggered?")
                .setPositiveButton(
                        "Schedule",
                        (dialog, which) ->
                                resolveProfileIdThen(
                                        profileName,
                                        ScheduleActivity.class))
                .setNegativeButton(
                        "Location (GSM)",
                        (dialog, which) -> {
                            if (ContextCompat.checkSelfPermission(
                                    MainActivity.this,
                                    Manifest.permission.ACCESS_FINE_LOCATION)
                                    != PackageManager.PERMISSION_GRANTED) {
                                Toast.makeText(
                                        MainActivity.this,
                                        "Location permission is needed "
                                                + "to scan cell towers",
                                        Toast.LENGTH_LONG).show();
                                ensureRuntimePermissions();
                                return;
                            }

                            resolveProfileIdThen(
                                    profileName,
                                    LocationScanActivity.class);
                        })
                .show();
    }

    private void resolveProfileIdThen(
            String profileName,
            Class<?> activityClass) {
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(MainActivity.this);
            long profileId = -1;

            try {
                List<Profile> existingProfiles =
                        db.profileDao().getAllProfiles();

                for (Profile p : existingProfiles) {
                    if (p.getName() != null
                            && p.getName().equals(profileName)) {
                        profileId = p.getId();
                        break;
                    }
                }

                if (profileId == -1) {
                    Profile newProfile =
                            new Profile(profileName, true);
                    profileId = db.profileDao().insert(newProfile);
                }
            } catch (Exception e) {
                Log.e(
                        "AutoProfile",
                        "DB Error checking profile",
                        e);
            }

            long finalProfileId = profileId;

            runOnUiThread(() -> {
                Intent intent = new Intent(
                        MainActivity.this,
                        activityClass);
                intent.putExtra("PROFILE_ID", finalProfileId);
                startActivity(intent);
            });
        });
    }
}
