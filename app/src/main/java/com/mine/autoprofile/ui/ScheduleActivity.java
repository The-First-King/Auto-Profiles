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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

public class ScheduleActivity extends AppCompatActivity {

    private String startTime = "08:00";
    private String endTime = "17:00";
    private long profileId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        profileId = getIntent().getLongExtra("PROFILE_ID", -1);

        Button btnStart = findViewById(R.id.btn_start_time);
        Button btnEnd = findViewById(R.id.btn_end_time);
        Button btnSave = findViewById(R.id.btn_save_rule);

        btnStart.setOnClickListener(v -> showTimePicker(true, btnStart));
        btnEnd.setOnClickListener(v -> showTimePicker(false, btnEnd));

        btnSave.setOnClickListener(v -> saveRule());
    }

    private void showTimePicker(boolean isStart, Button button) {
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String time = String.format("%02d:%02d", hourOfDay, minute);
            if (isStart) startTime = time; else endTime = time;
            button.setText((isStart ? "Start: " : "End: ") + time);
        }, 8, 0, true);
        dialog.show();
    }

    private void saveRule() {
        if (profileId == -1) {
            Toast.makeText(this, "Error: No Profile ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gather checked days (1=Sun, 2=Mon, ..., 7=Sat to match Java Calendar)
        List<String> days = new ArrayList<>();
        if (((CheckBox) findViewById(R.id.chk_sun)).isChecked()) days.add("1");
        if (((CheckBox) findViewById(R.id.chk_mon)).isChecked()) days.add("2");
        if (((CheckBox) findViewById(R.id.chk_tue)).isChecked()) days.add("3");
        if (((CheckBox) findViewById(R.id.chk_wed)).isChecked()) days.add("4");
        if (((CheckBox) findViewById(R.id.chk_thu)).isChecked()) days.add("5");
        if (((CheckBox) findViewById(R.id.chk_fri)).isChecked()) days.add("6");
        if (((CheckBox) findViewById(R.id.chk_sat)).isChecked()) days.add("7");

        String triggerValue = startTime + "-" + endTime + "|" + String.join(",", days);

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            
            // Insert Trigger and get generated ID
            Trigger trigger = new Trigger("TIME", triggerValue);
            long triggerId = db.triggerDao().insert(trigger);

            // Insert Rule mapping
            Rule rule = new Rule(profileId, triggerId, true);
            db.ruleDao().insert(rule);

            runOnUiThread(() -> {
                Toast.makeText(this, "Rule Saved!", Toast.LENGTH_SHORT).show();
                finish(); // Close activity and return to main screen
            });
        });
    }
}
