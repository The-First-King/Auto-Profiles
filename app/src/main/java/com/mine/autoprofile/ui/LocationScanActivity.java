package com.mine.autoprofile.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
import android.telephony.CellIdentityGsm;
import android.telephony.CellIdentityLte;
import android.telephony.CellIdentityWcdma;
import android.telephony.CellInfoGsm;
import android.telephony.CellInfoLte;
import android.telephony.CellInfoWcdma;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.mine.autoprofile.R;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.Trigger;
import com.mine.autoprofile.services.TriggerMonitorService;
import com.mine.autoprofile.utils.CellUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * DIAGNOSTIC VERSION - Use this to debug cell detection issues.
 * This version includes extensive logging to identify exactly why cells aren't detected.
 *
 * Replace LocationScanActivity.java temporarily with this version, then check logcat.
 */
public class LocationScanActivity extends AppCompatActivity {

    private static final int REQ_LOCATION = 71;
    private static final long SCAN_INTERVAL_MS = 5000;
    private static final int MAX_NAME_LENGTH = 80;
    private static final String TAG = "AutoProfile";

    private long profileId;
    private long editRuleId = -1;
    private long editTriggerId = -1;
    private String existingName = null;

    private final Set<String> foundCells = new LinkedHashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private TelephonyManager telephonyManager;
    private boolean scanning = false;
    private int scanAttempts = 0;

    private TextView tvStatus, tvCellsHeader, tvCells;
    private Button btnComplete;

    private final Runnable scanTick = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
            scanAttempts++;
            Log.d(TAG, ">>> SCAN ATTEMPT #" + scanAttempts);
            requestScan();
            handler.postDelayed(this, SCAN_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_location_scan);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        profileId = getIntent().getLongExtra("PROFILE_ID", -1);
        editRuleId = getIntent().getLongExtra("RULE_ID", -1);
        editTriggerId = getIntent().getLongExtra("TRIGGER_ID", -1);
        existingName = getIntent().getStringExtra("RULE_NAME");

        tvStatus = findViewById(R.id.tv_scan_status);
        tvCellsHeader = findViewById(R.id.tv_cells_header);
        tvCells = findViewById(R.id.tv_cells);
        btnComplete = findViewById(R.id.btn_complete);
        btnComplete.setOnClickListener(v -> promptForNameAndSave());

        telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);

        // DIAGNOSTIC: Log device info
        Log.d(TAG, "=== LocationScanActivity.onCreate() ===");
        Log.d(TAG, "Device API Level: " + Build.VERSION.SDK_INT);
        Log.d(TAG, "TelephonyManager: " + (telephonyManager == null ? "NULL!" : "OK"));

        if (telephonyManager != null) {
            try {
                String imsi = telephonyManager.getSubscriberId();
                Log.d(TAG, "IMSI (SIM): " + (imsi == null ? "NULL - NO SIM!" : "OK - " + imsi.substring(0, Math.min(6, imsi.length())) + "..."));
            } catch (Exception e) {
                Log.e(TAG, "Cannot read IMSI", e);
            }
            try {
                String operator = telephonyManager.getNetworkOperatorName();
                Log.d(TAG, "Operator: " + (operator == null ? "NULL" : operator));
            } catch (Exception e) {
                Log.e(TAG, "Cannot read operator", e);
            }
        }

        if (isEditMode()) {
            ((TextView) findViewById(R.id.tv_scan_title)).setText("Update Location Rule");
            foundCells.addAll(CellUtils.fromTriggerValue(
                    getIntent().getStringExtra("TRIGGER_VALUE")));
            renderCells();
        }
    }

    private boolean isEditMode() {
        return editRuleId != -1 && editTriggerId != -1;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Checking permissions...");
        if (hasLocationPermission()) {
            Log.d(TAG, "onStart: All permissions OK, starting scan");
            startScanning();
        } else {
            Log.w(TAG, "onStart: Permissions missing, requesting...");
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE
                    }, REQ_LOCATION);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopScanning();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            Log.d(TAG, "onRequestPermissionsResult: Granted=" + (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED));
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Log.e(TAG, "Permissions denied!");
                Toast.makeText(this,
                        "Location, phone, and network permissions are required to scan cell towers",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean hasLocationPermission() {
        boolean fine = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        boolean phone = ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED;
        boolean network = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED;

        Log.d(TAG, "Permission check: Fine=" + fine + " Phone=" + phone + " Network=" + network);
        return fine && phone && network;
    }

    // ------------------------------------------------------------- scanning

    private void startScanning() {
        if (scanning) return;
        scanning = true;
        scanAttempts = 0;
        tvStatus.setText("Scanning in progress...");
        Log.d(TAG, "*** SCANNING STARTED ***");
        handler.post(scanTick);
    }

    private void stopScanning() {
        scanning = false;
        handler.removeCallbacks(scanTick);
        Log.d(TAG, "*** SCANNING STOPPED (total attempts: " + scanAttempts + ") ***");
    }

    @SuppressWarnings("MissingPermission")
    private void requestScan() {
        if (telephonyManager == null) {
            Log.e(TAG, "[SCAN #" + scanAttempts + "] FATAL: TelephonyManager is NULL");
            return;
        }
        if (!hasLocationPermission()) {
            Log.e(TAG, "[SCAN #" + scanAttempts + "] Permissions not granted");
            return;
        }

        try {
            Log.d(TAG, "[SCAN #" + scanAttempts + "] API Level: " + Build.VERSION.SDK_INT +
                   " (using " + (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ? "requestCellInfoUpdate" : "getAllCellInfo") + ")");

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "[SCAN #" + scanAttempts + "] Calling requestCellInfoUpdate()...");
                telephonyManager.requestCellInfoUpdate(getMainExecutor(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                Log.d(TAG, "[SCAN #" + scanAttempts + "] requestCellInfoUpdate callback received");
                                if (cellInfo == null) {
                                    Log.w(TAG, "[SCAN #" + scanAttempts + "] Callback: cellInfo is NULL");
                                } else {
                                    Log.d(TAG, "[SCAN #" + scanAttempts + "] Callback: " + cellInfo.size() + " cells received");
                                    logCellDetails(cellInfo);
                                }
                                addCells(cellInfo);
                            }
                        });
            } else {
                Log.d(TAG, "[SCAN #" + scanAttempts + "] Calling getAllCellInfo()...");
                List<CellInfo> cells = telephonyManager.getAllCellInfo();
                if (cells == null) {
                    Log.w(TAG, "[SCAN #" + scanAttempts + "] getAllCellInfo() returned NULL");
                } else {
                    Log.d(TAG, "[SCAN #" + scanAttempts + "] getAllCellInfo() returned " + cells.size() + " cells");
                    logCellDetails(cells);
                }
                addCells(cells);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "[SCAN #" + scanAttempts + "] SecurityException - runtime permissions may not be granted", e);
        } catch (Exception e) {
            Log.e(TAG, "[SCAN #" + scanAttempts + "] Exception in requestScan()", e);
            e.printStackTrace();
        }
    }

    private void logCellDetails(List<CellInfo> cellInfo) {
        if (cellInfo == null || cellInfo.isEmpty()) return;

        for (int i = 0; i < Math.min(3, cellInfo.size()); i++) {
            CellInfo info = cellInfo.get(i);
            try {
                if (info instanceof CellInfoGsm) {
                    CellIdentityGsm id = ((CellInfoGsm) info).getCellIdentity();
                    Log.d(TAG, "  [" + i + "] GSM: LAC=" + id.getLac() + " CID=" + id.getCid() +
                           " MCC=" + id.getMccString() + " MNC=" + id.getMncString() + " Registered=" + info.isRegistered());
                } else if (info instanceof CellInfoWcdma) {
                    CellIdentityWcdma id = ((CellInfoWcdma) info).getCellIdentity();
                    Log.d(TAG, "  [" + i + "] WCDMA: LAC=" + id.getLac() + " CID=" + id.getCid() +
                           " MCC=" + id.getMccString() + " MNC=" + id.getMncString() + " Registered=" + info.isRegistered());
                } else if (info instanceof CellInfoLte) {
                    CellIdentityLte id = ((CellInfoLte) info).getCellIdentity();
                    Log.d(TAG, "  [" + i + "] LTE: TAC=" + id.getTac() + " CI=" + id.getCi() +
                           " MCC=" + id.getMccString() + " MNC=" + id.getMncString() + " Registered=" + info.isRegistered());
                } else {
                    Log.d(TAG, "  [" + i + "] " + info.getClass().getSimpleName() + " Registered=" + info.isRegistered());
                }
            } catch (Exception e) {
                Log.e(TAG, "  [" + i + "] Error reading cell details", e);
            }
        }
    }

    private void addCells(List<CellInfo> cellInfo) {
        int before = foundCells.size();
        Set<String> newCells = CellUtils.cellKeys(cellInfo);
        Log.d(TAG, "addCells(): Input had " + (cellInfo == null ? "null" : cellInfo.size()) + " cells, " +
               "CellUtils.cellKeys() extracted " + newCells.size() + " valid keys");

        foundCells.addAll(newCells);
        Log.d(TAG, "addCells(): Total cells now: " + before + " → " + foundCells.size());

        if (foundCells.size() != before || before == 0) {
            renderCells();
        }
    }

    private void renderCells() {
        tvCellsHeader.setText("Cells found (" + foundCells.size() + "):");
        StringBuilder b = new StringBuilder();
        int i = 1;
        for (String key : foundCells) {
            if (b.length() > 0) b.append("\n");
            b.append("Cell ").append(i++).append(": ").append(CellUtils.shortLabel(key));
        }
        tvCells.setText(b.toString());
        btnComplete.setEnabled(!foundCells.isEmpty());
        Log.d(TAG, "renderCells(): UI updated, Complete button " + (btnComplete.isEnabled() ? "ENABLED" : "DISABLED"));
    }

    // ---------------------------------------------------------------- save

    private void promptForNameAndSave() {
        if (foundCells.isEmpty()) {
            Toast.makeText(this, "No cells found. Please scan again.", Toast.LENGTH_SHORT).show();
            return;
        }

        EditText input = new EditText(this);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(MAX_NAME_LENGTH)});
        input.setHint("e.g. Office, 3rd floor - silent during meetings");
        if (existingName != null) {
            input.setText(existingName);
            input.setSelection(existingName.length());
        }

        new AlertDialog.Builder(this)
                .setTitle("Name this location rule")
                .setMessage("Give the rule a name so you can recognize the location later (up to "
                        + MAX_NAME_LENGTH + " characters).")
                .setView(input)
                .setPositiveButton("Save", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) {
                        Toast.makeText(this, "Please enter a name", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    saveRule(name);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveRule(String name) {
        String triggerValue = CellUtils.toTriggerValue(foundCells);
        Log.d(TAG, "Saving rule '" + name + "' with " + foundCells.size() + " cells");

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            if (isEditMode()) {
                Trigger trigger = new Trigger("CELL", triggerValue);
                trigger.setId(editTriggerId);
                db.triggerDao().update(trigger);

                Rule rule = new Rule(profileId, editTriggerId,
                        getIntent().getBooleanExtra("RULE_ENABLED", true));
                rule.setId(editRuleId);
                rule.setName(name);
                db.ruleDao().update(rule);
            } else {
                Trigger trigger = new Trigger("CELL", triggerValue);
                long triggerId = db.triggerDao().insert(trigger);

                Rule rule = new Rule(profileId, triggerId, true);
                rule.setName(name);
                db.ruleDao().insert(rule);
            }

            runOnUiThread(() -> {
                if (com.mine.autoprofile.utils.ProfileSwitcher.isMasterEnabled(this)) {
                    startService(new android.content.Intent(this, TriggerMonitorService.class));
                }
                Toast.makeText(this, isEditMode() ? "Rule Updated!" : "Location rule saved!",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
