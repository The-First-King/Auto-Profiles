package com.mine.autoprofile.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.mine.autoprofile.R;
import com.mine.autoprofile.services.TriggerMonitorService;

// Import LineageOS specific classes
import lineageos.app.Profile;
import lineageos.app.ProfileManager;

public class MainActivity extends AppCompatActivity {

    private ProfileManager mProfileManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Start background monitoring service when app opens
        Intent serviceIntent = new Intent(this, TriggerMonitorService.class);
        startService(serviceIntent);

        // Initialize the LineageOS Profile Manager
        mProfileManager = ProfileManager.getInstance(this);

        // Find the FAB and set its click listener
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(v -> showProfileSelectionDialog());
    }

    private void showProfileSelectionDialog() {
        // Check if profiles are enabled on the device at the OS level
        if (mProfileManager == null || !mProfileManager.isProfilesEnabled()) {
            Toast.makeText(this, "LineageOS Profiles are not enabled or unavailable.", Toast.LENGTH_LONG).show();
            return;
        }

        // Fetch the array of existing profiles
        Profile[] profiles = mProfileManager.getProfiles();
        if (profiles == null || profiles.length == 0) {
            Toast.makeText(this, "No LineageOS profiles found.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Extract names to display in the list
        String[] profileNames = new String[profiles.length];
        for (int i = 0; i < profiles.length; i++) {
            profileNames[i] = profiles[i].getName();
        }

        // Display the list in a dialog
        new AlertDialog.Builder(this)
                .setTitle("Select LineageOS Profile")
                .setItems(profileNames, (dialog, which) -> {
                    // This gets triggered when the user taps a profile in the list
                    String selectedProfileName = profileNames[which];
                    Profile selectedProfile = profiles[which];
                    
                    // TODO: Navigate to a "Create Rule" screen passing the selected profile UUID/Name
                    Toast.makeText(this, "Selected: " + selectedProfileName, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
