package com.mine.autoprofile.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.CellInfo;
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
 * GSM/LTE Location Scanner
 *
 * FIXED VERSION: Uses getAllCellInfo() as primary method
 * On some devices (LineageOS 19.1 + Android 12), requestCellInfoUpdate() doesn't work
 * but getAllCellInfo() does. This version tries getAllCellInfo() first.
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

    private TextView tvStatus, tvCellsHeader, tvCells;
    private Button btnComplete;

    private final Runnable scanTick = new Runnable() {
        @Override
        public void run() {
            if (!scanning) return;
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
        if (hasLocationPermission()) {
            startScanning();
        } else {
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
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanning();
            } else {
                Toast.makeText(this,
                        "Location, phone, and network permissions are required to scan cell towers",
                        Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
                == PackageManager.PERMISSION_GRANTED
            && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_NETWORK_STATE)
                == PackageManager.PERMISSION_GRANTED;
    }

    // ------------------------------------------------------------- scanning

    private void startScanning() {
        if (scanning) return;
        scanning = true;
        tvStatus.setText("Scanning in progress...");
        Log.d(TAG, "Started cell tower scanning");
        handler.post(scanTick);
    }

    private void stopScanning() {
        scanning = false;
        handler.removeCallbacks(scanTick);
        Log.d(TAG, "Stopped cell tower scanning");
    }

    @SuppressWarnings("MissingPermission")
    private void requestScan() {
        if (telephonyManager == null || !hasLocationPermission()) {
            Log.w(TAG, "requestScan: TM null or permission missing");
            return;
        }

        try {
            // PRIORITY 1: Try getAllCellInfo() - works better on some devices (LineageOS 19.1)
            Log.d(TAG, "Attempting getAllCellInfo()...");
            List<CellInfo> cells = telephonyManager.getAllCellInfo();

            if (cells != null && !cells.isEmpty()) {
                Log.d(TAG, "getAllCellInfo() SUCCESS: " + cells.size() + " cells found");
                addCells(cells);
                return;
            } else {
                Log.d(TAG, "getAllCellInfo() returned " +
                    (cells == null ? "NULL" : "empty list (0 cells)"));
            }

            // PRIORITY 2: Fallback to requestCellInfoUpdate on API 29+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Log.d(TAG, "Fallback: Attempting requestCellInfoUpdate()...");
                telephonyManager.requestCellInfoUpdate(getMainExecutor(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                if (cellInfo == null) {
                                    Log.d(TAG, "requestCellInfoUpdate callback: NULL");
                                } else {
                                    Log.d(TAG, "requestCellInfoUpdate callback: " +
                                        cellInfo.size() + " cells");
                                }
                                addCells(cellInfo);
                            }
                        });
            }

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException in cell scan", e);
        } catch (Exception e) {
            Log.e(TAG, "Exception in cell scan", e);
        }
    }

    private void addCells(List<CellInfo> cellInfo) {
        int before = foundCells.size();
        Set<String> newCells = CellUtils.cellKeys(cellInfo);
        foundCells.addAll(newCells);

        if (foundCells.size() != before || before == 0) {
            Log.d(TAG, "Cells updated: " + before + " → " + foundCells.size());
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
        Log.d(TAG, "Saving location rule: '" + name + "' with " + foundCells.size() + " cells");

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
