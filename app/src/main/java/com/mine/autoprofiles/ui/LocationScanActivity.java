package com.mine.autoprofiles.ui;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.CellInfo;
import android.telephony.TelephonyManager;
import android.text.InputFilter;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.mine.autoprofiles.R;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.Rule;
import com.mine.autoprofiles.models.Trigger;
import com.mine.autoprofiles.services.TriggerMonitorService;
import com.mine.autoprofiles.utils.CellUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;

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
            // Preload the cells the rule already knows; scanning adds to them
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
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.READ_PHONE_STATE,
                            Manifest.permission.ACCESS_NETWORK_STATE
                    }, REQ_LOCATION);
            return;
        }
        if (!isSystemLocationEnabled()) {
            promptToEnableLocation();
            return;
        }
        startScanning();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopScanning();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (isSystemLocationEnabled()) {
                    startScanning();
                } else {
                    promptToEnableLocation();
                }
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

    /**
     * The system-wide Location master switch. When it is off, Android silently
     * returns empty cell lists to every app (cell ids count as location data).
     */
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

    private void promptToEnableLocation() {
        tvStatus.setText("Location is off");
        new AlertDialog.Builder(this)
                .setTitle("Location is off")
                .setMessage("Android only reveals cell tower identities while system Location "
                        + "is on. GPS is not used by this app - enable Location to scan.")
                .setPositiveButton("Enable", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                .setNegativeButton("Cancel", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }


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
            List<CellInfo> cells = telephonyManager.getAllCellInfo();
            if (cells != null && !cells.isEmpty()) {
                Log.d(TAG, "getAllCellInfo() returned " + cells.size() + " cells");
                addCells(cells);
                return;
            }
            Log.d(TAG, "getAllCellInfo() returned "
                    + (cells == null ? "null" : "empty list"));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                telephonyManager.requestCellInfoUpdate(getMainExecutor(),
                        new TelephonyManager.CellInfoCallback() {
                            @Override
                            public void onCellInfo(@NonNull List<CellInfo> cellInfo) {
                                Log.d(TAG, "requestCellInfoUpdate callback: "
                                        + (cellInfo == null ? "null"
                                        : cellInfo.size() + " cells"));
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
        foundCells.addAll(CellUtils.cellKeys(cellInfo));
        if (foundCells.size() != before || before == 0) {
            Log.d(TAG, "Cells updated: " + before + " -> " + foundCells.size());
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

        FrameLayout container = new FrameLayout(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int margin = (int) (20 * getResources().getDisplayMetrics().density);
        lp.leftMargin = margin;
        lp.rightMargin = margin;
        input.setLayoutParams(lp);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Name this location rule")
                .setMessage("Give the rule a name so you can recognize the location later (up to "
                        + MAX_NAME_LENGTH + " characters).")
                .setView(container)
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
        Log.d(TAG, "Saving location rule '" + name + "' with " + foundCells.size() + " cells");

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
                if (com.mine.autoprofiles.utils.ProfileSwitcher.isMasterEnabled(this)) {
                    startService(new Intent(this, TriggerMonitorService.class));
                }
                Toast.makeText(this, isEditMode() ? "Rule updated" : "Location rule saved",
                        Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }
}
