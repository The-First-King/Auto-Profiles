package com.mine.autoprofile.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.mine.autoprofile.R;
import com.mine.autoprofile.database.AppDatabase;
import com.mine.autoprofile.models.Rule;
import com.mine.autoprofile.models.Trigger;
import com.mine.autoprofile.utils.AlarmHelper;
import com.mine.autoprofile.utils.ProfileSwitcher;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ScheduleActivity extends AppCompatActivity {

    private String startTime = "08:00";
    private String endTime = "17:00";
    private long profileId;

    // Edit mode: set when an existing rule is being modified (-1 = create mode)
    private long editRuleId = -1;
    private long editTriggerId = -1;

    // chk ids indexed by Java Calendar DAY_OF_WEEK (1=Sun ... 7=Sat)
    private static final int[] DAY_CHECKBOX_IDS = {
            R.id.chk_sun, R.id.chk_mon, R.id.chk_tue, R.id.chk_wed,
            R.id.chk_thu, R.id.chk_fri, R.id.chk_sat
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        profileId = getIntent().getLongExtra("PROFILE_ID", -1);
        editRuleId = getIntent().getLongExtra("RULE_ID", -1);
        editTriggerId = getIntent().getLongExtra("TRIGGER_ID", -1);

        Button btnStart = findViewById(R.id.btn_start_time);
        Button btnEnd = findViewById(R.id.btn_end_time);
        Button btnSave = findViewById(R.id.btn_save_rule);

        btnStart.setOnClickListener(v -> showTimePicker(true, btnStart));
        btnEnd.setOnClickListener(v -> showTimePicker(false, btnEnd));
        btnSave.setOnClickListener(v -> saveRule());

        if (isEditMode()) {
            btnSave.setText("Update Rule");
            prefillFromTrigger(getIntent().getStringExtra("TRIGGER_VALUE"), btnStart, btnEnd);
        }
    }

    private boolean isEditMode() {
        return editRuleId != -1 && editTriggerId != -1;
    }

    /** Parses "HH:MM-HH:MM|d,d,..." and restores pickers/checkboxes from it. */
    private void prefillFromTrigger(String triggerValue, Button btnStart, Button btnEnd) {
        if (triggerValue == null) return;
        try {
            String[] parts = triggerValue.split("\\|");
            String[] times = parts[0].split("-");
            startTime = times[0].trim();
            if (times.length > 1) endTime = times[1].trim();
            btnStart.setText("Start: " + startTime);
            btnEnd.setText("End: " + endTime);

            if (parts.length > 1) {
                for (String dayStr : parts[1].split(",")) {
                    int day = Integer.parseInt(dayStr.trim()); // 1..7
                    if (day >= 1 && day <= 7) {
                        CheckBox chk = findViewById(DAY_CHECKBOX_IDS[day - 1]);
                        if (chk != null) chk.setChecked(true);
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Could not restore old schedule values", Toast.LENGTH_SHORT).show();
        }
    }

    private void showTimePicker(boolean isStart, Button button) {
        String current = isStart ? startTime : endTime;
        int hour = 8, minute = 0;
        try {
            String[] hm = current.split(":");
            hour = Integer.parseInt(hm[0]);
            minute = Integer.parseInt(hm[1]);
        } catch (Exception ignored) { }

        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, min) -> {
            String time = String.format("%02d:%02d", hourOfDay, min);
            if (isStart) startTime = time; else endTime = time;
            button.setText((isStart ? "Start: " : "End: ") + time);
        }, hour, minute, true);
        dialog.show();
    }

    private void saveRule() {
        if (profileId == -1) {
            Toast.makeText(this, "Error: No Profile ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gather checked days (1=Sun ... 7=Sat, matching Java Calendar)
        List<String> days = new ArrayList<>();
        for (int i = 0; i < DAY_CHECKBOX_IDS.length; i++) {
            CheckBox chk = findViewById(DAY_CHECKBOX_IDS[i]);
            if (chk != null && chk.isChecked()) days.add(String.valueOf(i + 1));
        }

        if (days.isEmpty()) {
            Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show();
            return;
        }

        String triggerValue = startTime + "-" + endTime + "|" + String.join(",", days);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            if (isEditMode()) {
                // --- EDIT: update the existing trigger, keep the same rule id ---
                // 1. Drop the old alarms; if the old window is applied right now,
                //    restore the previous profile so no orphan state is left behind.
                AlarmHelper.cancelAlarms(this, editRuleId);
                ProfileSwitcher.revertIfActive(this, editRuleId);

                // 2. Persist the new schedule
                Trigger trigger = new Trigger("TIME", triggerValue);
                trigger.setId(editTriggerId);
                db.triggerDao().update(trigger);

                // 3. Register alarms for the new schedule (fires immediately if
                //    the new window covers the current time)
                AlarmHelper.scheduleAlarm(this, editRuleId, profileId, triggerValue);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Rule Updated!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {
                // --- CREATE: original flow ---
                Trigger trigger = new Trigger("TIME", triggerValue);
                long triggerId = db.triggerDao().insert(trigger);

                Rule rule = new Rule(profileId, triggerId, true);
                long ruleId = db.ruleDao().insert(rule);

                AlarmHelper.scheduleAlarm(ScheduleActivity.this, ruleId, profileId, triggerValue);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Rule Saved & Alarm Set!", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
}
