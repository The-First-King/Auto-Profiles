package com.mine.autoprofiles.ui;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.mine.autoprofiles.R;
import com.mine.autoprofiles.database.AppDatabase;
import com.mine.autoprofiles.models.Rule;
import com.mine.autoprofiles.models.Schedule;
import com.mine.autoprofiles.models.Trigger;
import com.mine.autoprofiles.utils.AlarmHelper;
import com.mine.autoprofiles.utils.ProfileSwitcher;
import com.mine.autoprofiles.utils.ScheduleCalculator;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ScheduleActivity extends AppCompatActivity {

    private long profileId;
    private long editRuleId = -1;
    private long editTriggerId = -1;

    private LocalTime startTime = LocalTime.of(8, 0);
    private LocalTime endTime = LocalTime.of(17, 0);
    private LocalDate anchorDate = LocalDate.now();

    private LocalDate onceStartDate = LocalDate.now();
    private LocalTime onceStartTime = LocalTime.of(8, 0);
    private LocalDate onceEndDate = LocalDate.now();
    private LocalTime onceEndTime = LocalTime.of(17, 0);

    private LocalDate untilDate = LocalDate.now().plusMonths(1);

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("EEE, MMM d yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    // ISO order Mon..Sun to match DayOfWeek 1..7
    private static final int[] DAY_CHECKBOX_IDS = {
            R.id.chk_mon, R.id.chk_tue, R.id.chk_wed, R.id.chk_thu,
            R.id.chk_fri, R.id.chk_sat, R.id.chk_sun
    };

    private static final Schedule.Freq[] FREQS = {
            Schedule.Freq.DAILY, Schedule.Freq.WEEKLY, Schedule.Freq.MONTHLY, Schedule.Freq.YEARLY
    };
    private static final String[] FREQ_LABELS = {"Daily", "Weekly", "Monthly", "Yearly"};
    private static final String[] FREQ_UNITS = {"day(s)", "week(s)", "month(s)", "year(s)"};

    private RadioButton rbOnce, rbRecurring, rbForever, rbUntil, rbCount;
    private View sectionOnce, sectionRecur, daysContainer;
    private Button btnOnceStartDate, btnOnceStartTime, btnOnceEndDate, btnOnceEndTime;
    private Button btnStartTime, btnEndTime, btnAnchorDate, btnUntilDate;
    private Spinner spinnerFreq;
    private EditText etInterval, etCount;
    private TextView tvIntervalUnit, tvFreqInfo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule);

        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }

        profileId = getIntent().getLongExtra("PROFILE_ID", -1);
        editRuleId = getIntent().getLongExtra("RULE_ID", -1);
        editTriggerId = getIntent().getLongExtra("TRIGGER_ID", -1);

        bindViews();
        setupListeners();

        Button btnSave = findViewById(R.id.btn_save_rule);
        btnSave.setOnClickListener(v -> saveRule());

        if (isEditMode()) {
            btnSave.setText("Update Rule");
            Schedule existing = Schedule.parse(getIntent().getStringExtra("TRIGGER_VALUE"));
            if (existing != null) prefill(existing);
        }

        refreshAllLabels();
        refreshSectionVisibility();
    }

    private boolean isEditMode() {
        return editRuleId != -1 && editTriggerId != -1;
    }

    private void bindViews() {
        rbOnce = findViewById(R.id.rb_once);
        rbRecurring = findViewById(R.id.rb_recurring);
        rbForever = findViewById(R.id.rb_forever);
        rbUntil = findViewById(R.id.rb_until);
        rbCount = findViewById(R.id.rb_count);
        sectionOnce = findViewById(R.id.section_once);
        sectionRecur = findViewById(R.id.section_recur);
        daysContainer = findViewById(R.id.days_container);
        btnOnceStartDate = findViewById(R.id.btn_once_start_date);
        btnOnceStartTime = findViewById(R.id.btn_once_start_time);
        btnOnceEndDate = findViewById(R.id.btn_once_end_date);
        btnOnceEndTime = findViewById(R.id.btn_once_end_time);
        btnStartTime = findViewById(R.id.btn_start_time);
        btnEndTime = findViewById(R.id.btn_end_time);
        btnAnchorDate = findViewById(R.id.btn_anchor_date);
        btnUntilDate = findViewById(R.id.btn_until_date);
        spinnerFreq = findViewById(R.id.spinner_freq);
        etInterval = findViewById(R.id.et_interval);
        etCount = findViewById(R.id.et_count);
        tvIntervalUnit = findViewById(R.id.tv_interval_unit);
        tvFreqInfo = findViewById(R.id.tv_freq_info);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, FREQ_LABELS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFreq.setAdapter(adapter);
    }

    private void setupListeners() {
        RadioGroup rgType = findViewById(R.id.rg_event_type);
        rgType.setOnCheckedChangeListener((g, id) -> refreshSectionVisibility());

        attachRangeClamp(etInterval, 99);
        attachRangeClamp(etCount, 730);

        // One-time pickers
        btnOnceStartDate.setOnClickListener(v -> pickDate(onceStartDate, d -> { onceStartDate = d; refreshAllLabels(); }));
        btnOnceStartTime.setOnClickListener(v -> pickTime(onceStartTime, t -> { onceStartTime = t; refreshAllLabels(); }));
        btnOnceEndDate.setOnClickListener(v -> pickDate(onceEndDate, d -> { onceEndDate = d; refreshAllLabels(); }));
        btnOnceEndTime.setOnClickListener(v -> pickTime(onceEndTime, t -> { onceEndTime = t; refreshAllLabels(); }));

        // Recurring pickers
        btnStartTime.setOnClickListener(v -> pickTime(startTime, t -> { startTime = t; refreshAllLabels(); }));
        btnEndTime.setOnClickListener(v -> pickTime(endTime, t -> { endTime = t; refreshAllLabels(); }));
        btnAnchorDate.setOnClickListener(v -> pickDate(anchorDate, d -> { anchorDate = d; refreshAllLabels(); }));
        btnUntilDate.setOnClickListener(v -> pickDate(untilDate, d -> { untilDate = d; refreshAllLabels(); }));

        spinnerFreq.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                refreshSectionVisibility();
                refreshAllLabels();
            }
            @Override public void onNothingSelected(AdapterView<?> p) { }
        });

        rbForever.setOnClickListener(v -> { rbUntil.setChecked(false); rbCount.setChecked(false); refreshTermEnabled(); });
        rbUntil.setOnClickListener(v -> { rbForever.setChecked(false); rbCount.setChecked(false); refreshTermEnabled(); });
        rbCount.setOnClickListener(v -> { rbForever.setChecked(false); rbUntil.setChecked(false); refreshTermEnabled(); });
    }


    private void attachRangeClamp(EditText field, int max) {
        field.addTextChangedListener(new android.text.TextWatcher() {
            private boolean updating = false;
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void afterTextChanged(android.text.Editable e) {
                if (updating) return;
                String t = e.toString().trim();
                if (t.isEmpty()) return;
                String fixed = null;
                try {
                    int v = Integer.parseInt(t);
                    if (v < 1) fixed = "1";
                    else if (v > max) fixed = String.valueOf(max);
                } catch (NumberFormatException ex) {
                    fixed = "1";
                }
                if (fixed != null) {
                    updating = true;
                    field.setText(fixed);
                    field.setSelection(fixed.length());
                    updating = false;
                }
            }
        });
    }


    private interface DateConsumer { void accept(LocalDate d); }
    private interface TimeConsumer { void accept(LocalTime t); }

    private void pickDate(LocalDate current, DateConsumer onPicked) {
        new DatePickerDialog(this,
                (view, y, m, d) -> onPicked.accept(LocalDate.of(y, m + 1, d)),
                current.getYear(), current.getMonthValue() - 1, current.getDayOfMonth()).show();
    }

    private void pickTime(LocalTime current, TimeConsumer onPicked) {
        new TimePickerDialog(this,
                (view, h, m) -> onPicked.accept(LocalTime.of(h, m)),
                current.getHour(), current.getMinute(), true).show();
    }

    private Schedule.Freq selectedFreq() {
        return FREQS[spinnerFreq.getSelectedItemPosition()];
    }

    private void refreshSectionVisibility() {
        boolean once = rbOnce.isChecked();
        sectionOnce.setVisibility(once ? View.VISIBLE : View.GONE);
        sectionRecur.setVisibility(once ? View.GONE : View.VISIBLE);
        if (!once) {
            Schedule.Freq f = selectedFreq();
            daysContainer.setVisibility(f == Schedule.Freq.WEEKLY ? View.VISIBLE : View.GONE);
            tvFreqInfo.setVisibility(
                    (f == Schedule.Freq.MONTHLY || f == Schedule.Freq.YEARLY) ? View.VISIBLE : View.GONE);
        }
        refreshTermEnabled();
    }

    private void refreshTermEnabled() {
        btnUntilDate.setEnabled(rbUntil.isChecked());
        etCount.setEnabled(rbCount.isChecked());
    }

    private void refreshAllLabels() {
        btnOnceStartDate.setText(onceStartDate.format(DATE_FMT));
        btnOnceStartTime.setText(onceStartTime.format(TIME_FMT));
        btnOnceEndDate.setText(onceEndDate.format(DATE_FMT));
        btnOnceEndTime.setText(onceEndTime.format(TIME_FMT));

        btnStartTime.setText("Start: " + startTime.format(TIME_FMT));
        btnEndTime.setText("End: " + endTime.format(TIME_FMT));
        btnAnchorDate.setText("Starts on: " + anchorDate.format(DATE_FMT));
        btnUntilDate.setText(untilDate.format(DATE_FMT));

        int pos = spinnerFreq.getSelectedItemPosition();
        tvIntervalUnit.setText(FREQ_UNITS[Math.max(0, pos)]);

        Schedule.Freq f = selectedFreq();
        if (f == Schedule.Freq.MONTHLY) {
            tvFreqInfo.setText("On day " + anchorDate.getDayOfMonth() + " of the month");
        } else if (f == Schedule.Freq.YEARLY) {
            tvFreqInfo.setText("Every year on " + anchorDate.format(
                    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())));
        }
    }


    private void prefill(Schedule s) {
        if (s.mode == Schedule.Mode.ONCE) {
            rbOnce.setChecked(true);
            rbRecurring.setChecked(false);
            onceStartDate = s.onceStart.toLocalDate();
            onceStartTime = s.onceStart.toLocalTime();
            onceEndDate = s.onceEnd.toLocalDate();
            onceEndTime = s.onceEnd.toLocalTime();
            return;
        }

        rbRecurring.setChecked(true);
        rbOnce.setChecked(false);
        startTime = s.startTime;
        endTime = s.endTime;
        anchorDate = s.anchor;
        for (int i = 0; i < FREQS.length; i++) {
            if (FREQS[i] == s.freq) spinnerFreq.setSelection(i);
        }
        etInterval.setText(String.valueOf(s.interval));
        for (DayOfWeek d : s.days) {
            CheckBox chk = findViewById(DAY_CHECKBOX_IDS[d.getValue() - 1]);
            if (chk != null) chk.setChecked(true);
        }
        rbForever.setChecked(s.term == Schedule.Term.NEVER);
        rbUntil.setChecked(s.term == Schedule.Term.UNTIL);
        rbCount.setChecked(s.term == Schedule.Term.COUNT);
        if (s.term == Schedule.Term.UNTIL) untilDate = s.until;
        if (s.term == Schedule.Term.COUNT) etCount.setText(String.valueOf(s.count));
    }
    

    private Schedule buildScheduleFromForm() {
        Schedule s = new Schedule();

        if (rbOnce.isChecked()) {
            s.mode = Schedule.Mode.ONCE;
            s.onceStart = LocalDateTime.of(onceStartDate, onceStartTime);
            s.onceEnd = LocalDateTime.of(onceEndDate, onceEndTime);
            // Convenience: same-day interval that crosses midnight -> end next day
            if (!s.onceEnd.isAfter(s.onceStart) && onceEndDate.equals(onceStartDate)) {
                s.onceEnd = s.onceEnd.plusDays(1);
            }
            if (!s.onceEnd.isAfter(s.onceStart)) {
                Toast.makeText(this, "End must be after start", Toast.LENGTH_SHORT).show();
                return null;
            }
            if (s.onceEnd.isBefore(LocalDateTime.now())) {
                Toast.makeText(this, "This event is already in the past", Toast.LENGTH_SHORT).show();
                return null;
            }
            return s;
        }

        s.mode = Schedule.Mode.RECUR;
        s.startTime = startTime;
        s.endTime = endTime;
        s.anchor = anchorDate;
        s.freq = selectedFreq();

        try {
            String raw = etInterval.getText().toString().trim();
            s.interval = raw.isEmpty() ? 1
                    : Math.min(99, Math.max(1, Integer.parseInt(raw)));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Enter a valid repeat interval", Toast.LENGTH_SHORT).show();
            return null;
        }

        if (s.freq == Schedule.Freq.WEEKLY) {
            for (int i = 0; i < DAY_CHECKBOX_IDS.length; i++) {
                CheckBox chk = findViewById(DAY_CHECKBOX_IDS[i]);
                if (chk != null && chk.isChecked()) s.days.add(DayOfWeek.of(i + 1));
            }
            if (s.days.isEmpty()) {
                Toast.makeText(this, "Please select at least one day", Toast.LENGTH_SHORT).show();
                return null;
            }
        }

        if (rbUntil.isChecked()) {
            s.term = Schedule.Term.UNTIL;
            s.until = untilDate;
            if (untilDate.isBefore(anchorDate)) {
                Toast.makeText(this, "End date is before the start date", Toast.LENGTH_SHORT).show();
                return null;
            }
        } else if (rbCount.isChecked()) {
            s.term = Schedule.Term.COUNT;
            try {
                String raw = etCount.getText().toString().trim();
                s.count = raw.isEmpty() ? 1
                        : Math.min(730, Math.max(1, Integer.parseInt(raw)));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Enter a valid number of occurrences", Toast.LENGTH_SHORT).show();
                return null;
            }
        } else {
            s.term = Schedule.Term.NEVER;
        }

        // Reject schedules that can never fire again (e.g. until-date fully in the past)
        long[] w = ScheduleCalculator.windows(s, System.currentTimeMillis());
        if (w[0] == -1 && w[2] == -1) {
            Toast.makeText(this, "This schedule has no upcoming occurrences", Toast.LENGTH_LONG).show();
            return null;
        }
        return s;
    }

    private void saveRule() {
        if (profileId == -1) {
            Toast.makeText(this, "Error: No Profile ID", Toast.LENGTH_SHORT).show();
            return;
        }

        Schedule schedule = buildScheduleFromForm();
        if (schedule == null) return; // validation failed, toast already shown

        String triggerValue = schedule.toJson();

        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);

            if (isEditMode()) {

                AlarmHelper.cancelAlarms(this, editRuleId);
                ProfileSwitcher.revertIfActive(this, editRuleId);

                Trigger trigger = new Trigger("TIME", triggerValue);
                trigger.setId(editTriggerId);
                db.triggerDao().update(trigger);

                AlarmHelper.scheduleAlarm(this, editRuleId, profileId, triggerValue);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Rule updated", Toast.LENGTH_SHORT).show();
                    finish();
                });
            } else {

                Trigger trigger = new Trigger("TIME", triggerValue);
                long triggerId = db.triggerDao().insert(trigger);

                Rule rule = new Rule(profileId, triggerId, true);
                long ruleId = db.ruleDao().insert(rule);

                AlarmHelper.scheduleAlarm(ScheduleActivity.this, ruleId, profileId, triggerValue);

                runOnUiThread(() -> {
                    Toast.makeText(this, "Rule saved & alarm set", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }
}
