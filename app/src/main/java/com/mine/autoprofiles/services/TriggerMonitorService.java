package com.mine.autoprofiles.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.mine.autoprofiles.R;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.FullRule;
import com.mine.autoprofiles.utils.CellUtils;
import com.mine.autoprofiles.utils.ProfileSwitcher;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TriggerMonitorService extends Service {
    private static final String CHANNEL_ID = "AutoProfilesServiceChannel";
    private static final int NOTIFICATION_ID = 1337;
    private static final String TAG = "AutoProfile";
    private static final long REFRESH_INTERVAL_MS = 60_000;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelephonyManager telephonyManager;
    private boolean listening = false;

    // Kept as fields so the exact registered instances can be unregistered
    private TelephonyCallback telephonyCallback;
    private PhoneStateListener phoneStateListener;

    private final Runnable periodicRefresh = new Runnable() {
        @Override
        public void run() {
            requestFreshCellInfo();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Auto Profiles")
                .setContentText("Monitoring triggers in background...")
                .setSmallIcon(R.drawable.ic_service_notification)
                .build();
        startForeground(NOTIFICATION_ID, notification);
        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureListening();
        requestFreshCellInfo();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacks(periodicRefresh);
        stopListening();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            && ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** System-wide Location master switch; cell ids are hidden while it is off. */
    private boolean isSystemLocationEnabled() {
        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) return true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return lm.isLocationEnabled();
        }
        int mode = Settings.Secure.getInt(getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF);
        return mode != Settings.Secure.LOCATION_MODE_OFF;
    }

    @SuppressWarnings({"MissingPermission", "deprecation"})
    private void ensureListening() {
        if (listening) return;
        if (telephonyManager == null) return;
        if (!hasLocationPermission()) {
            Log.w(TAG, "Cell monitoring inactive: location permission not granted yet");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                telephonyCallback = new CellChangeCallback();
                telephonyManager.registerTelephonyCallback(getMainExecutor(), telephonyCallback);
            } else {
                phoneStateListener = new PhoneStateListener() {
                    @Override
                    public void onCellInfoChanged(List<CellInfo> cellInfo) {
                        evaluate(cellInfo);
                    }
                };
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CELL_INFO);
            }
            listening = true;
            // Cell callbacks can be sparse while stationary; refresh periodically too
            handler.removeCallbacks(periodicRefresh);
            handler.postDelayed(periodicRefresh, REFRESH_INTERVAL_MS);
            Log.i(TAG, "Cell monitoring started");
        } catch (Exception e) {
            Log.e(TAG, "Failed to start cell monitoring", e);
        }
    }

    @SuppressWarnings("deprecation")
    private void stopListening() {
        if (!listening || telephonyManager == null) return;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
                telephonyManager.unregisterTelephonyCallback(telephonyCallback);
            } else if (phoneStateListener != null) {
                telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_NONE);
            }
        } catch (Exception ignored) { }
        listening = false;
    }

    private class CellChangeCallback extends TelephonyCallback
            implements TelephonyCallback.CellInfoListener {
        @Override
        public void onCellInfoChanged(@NonNull List<CellInfo> cellInfo) {
            evaluate(cellInfo);
        }
    }

    @SuppressWarnings("MissingPermission")
    private void requestFreshCellInfo() {
        if (telephonyManager == null || !hasLocationPermission()) return;
        if (!isSystemLocationEnabled()) {
            Log.w(TAG, "System Location is off: cell IDs unavailable, skipping refresh.");
            return;
        }
        try {
            List<CellInfo> cells = telephonyManager.getAllCellInfo();
            if (cells != null && !cells.isEmpty()) {
                Log.d(TAG, "getAllCellInfo() returned " + cells.size() + " cells");
                evaluate(cells);
                return;
            }
            Log.d(TAG, "getAllCellInfo() returned "
                    + (cells == null ? "null" : "empty list"));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.requestCellInfoUpdate(getMainExecutor(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                Log.d(TAG, "requestCellInfoUpdate returned "
                                        + (cellInfo == null ? "null"
                                        : cellInfo.size() + " cells"));
                                evaluate(cellInfo);
                            }
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Cell refresh failed", e);
        }
    }


    private void evaluate(List<CellInfo> cellInfo) {
        // Null check BEFORE using cellInfo
        if (cellInfo == null) {
            Log.d(TAG, "evaluate: cellInfo is null, skipping evaluation");
            return;
        }

        if (!isSystemLocationEnabled()) {
            Log.w(TAG, "evaluate: system Location off, skipping evaluation");
            return;
        }

        final Set<String> inRange = CellUtils.cellKeys(cellInfo);
        Log.d(TAG, "evaluate: " + inRange.size() + " cells in range");

        executor.execute(() -> {
            if (!ProfileSwitcher.isMasterEnabled(this)) return;

            SharedPreferences prefs =
                    getSharedPreferences(ProfileSwitcher.PREF_NAME, Context.MODE_PRIVATE);
            AppDatabase db = AppDatabase.getInstance(this);

            for (FullRule fullRule : db.ruleDao().getAllRulesWithDetails()) {
                if (fullRule.rule == null || fullRule.trigger == null) continue;
                if (!"CELL".equals(fullRule.trigger.getType())) continue;

                long ruleId = fullRule.rule.getId();
                String activeKey = ProfileSwitcher.CELL_ACTIVE_KEY_PREFIX + ruleId;
                boolean wasActive = prefs.getBoolean(activeKey, false);

                if (!fullRule.rule.isEnabled()) {
                    // Disabled rules never hold a location; MainActivity already
                    // reverted the profile when the rule was switched off.
                    continue;
                }

                Set<String> ruleCells = CellUtils.fromTriggerValue(fullRule.trigger.getValue());
                boolean nowActive = !Collections.disjoint(ruleCells, inRange);

                if (nowActive && !wasActive) {
                    Log.i(TAG, "Rule " + ruleId + " (" + fullRule.rule.getName()
                            + "): location ENTERED");
                    prefs.edit().putBoolean(activeKey, true).apply();
                    String revertKey = ProfileSwitcher.REVERT_KEY_PREFIX + ruleId;
                    if (!prefs.contains(revertKey)) {
                        String current = ProfileSwitcher.getActiveProfileName(this);
                        if (current != null) {
                            prefs.edit().putString(revertKey, current).apply();
                        }
                    }
                    if (fullRule.profile != null && fullRule.profile.getName() != null) {
                        boolean ok = ProfileSwitcher.switchTo(this, fullRule.profile.getName());
                        Log.i(TAG, "ENTER switch to '" + fullRule.profile.getName()
                                + "' verified=" + ok);
                    }
                } else if (!nowActive && wasActive) {
                    Log.i(TAG, "Rule " + ruleId + " (" + fullRule.rule.getName()
                            + "): location LEFT");
                    ProfileSwitcher.revertIfActive(this, ruleId);
                }
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Auto Profiles Background Service",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
