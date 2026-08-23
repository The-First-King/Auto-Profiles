package com.mine.autoprofile.ui;

import android.os.Bundle;
import android.widget.CompoundButton;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

import com.mine.autoprofile.R;

public class MainActivity extends AppCompatActivity {

    private SwitchCompat masterSwitch;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupListeners();
    }

    private void initializeViews() {
        masterSwitch = findViewById(R.id.master_switch);
    }

    private void setupListeners() {
        masterSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                onMasterSwitchToggled(isChecked);
            }
        });
    }

    private void onMasterSwitchToggled(boolean enabled) {
        if (enabled) {
            // Start profile monitoring service
            // startService(new Intent(this, ProfileManagerService.class));
        } else {
            // Stop profile monitoring service
            // stopService(new Intent(this, ProfileManagerService.class));
        }
    }
}
