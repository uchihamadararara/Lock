package com.applock.guardian.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.applock.guardian.R;
import com.applock.guardian.receivers.DeviceAdminReceiver;
import com.applock.guardian.utils.AppPrefs;

public class SettingsActivity extends AppCompatActivity {

    private AppPrefs prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        prefs = new AppPrefs(this);

        getSupportActionBar().setTitle("Settings");
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        setupToggles();
    }

    private void setupToggles() {
        Switch switchHaptic = findViewById(R.id.switch_haptic);
        Switch switchClock = findViewById(R.id.switch_clock);
        Switch switchBlur = findViewById(R.id.switch_blur);
        Switch switchIntruder = findViewById(R.id.switch_intruder);

        switchHaptic.setChecked(prefs.isHapticFeedback());
        switchClock.setChecked(prefs.isShowClock());
        switchBlur.setChecked(prefs.isWallpaperBlur());
        switchIntruder.setChecked(prefs.isIntruderSelfie());

        switchHaptic.setOnCheckedChangeListener((b, c) -> prefs.setHapticFeedback(c));
        switchClock.setOnCheckedChangeListener((b, c) -> prefs.setShowClock(c));
        switchBlur.setOnCheckedChangeListener((b, c) -> prefs.setWallpaperBlur(c));
        switchIntruder.setOnCheckedChangeListener((b, c) -> prefs.setIntruderSelfie(c));

        // Remove device admin
        findViewById(R.id.btn_remove_admin).setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Remove Device Admin?")
                    .setMessage("This will allow the app to be uninstalled without authentication.")
                    .setPositiveButton("Remove", (d, w) -> removeDeviceAdmin())
                    .setNegativeButton("Cancel", null)
                    .show();
        });
    }

    private void removeDeviceAdmin() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        if (dpm != null) {
            dpm.removeActiveAdmin(new ComponentName(this, DeviceAdminReceiver.class));
            Toast.makeText(this, "Device Admin removed", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}
