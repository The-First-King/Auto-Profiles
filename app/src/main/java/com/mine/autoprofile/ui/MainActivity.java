package com.mine.autoprofile.ui;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import com.mine.autoprofile.R;
import com.mine.autoprofile.services.TriggerMonitorService;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start background monitoring service when app opens
        Intent serviceIntent = new Intent(this, TriggerMonitorService.class);
        startService(serviceIntent);
    }
}
