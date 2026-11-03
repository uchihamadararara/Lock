package com.applock.guardian.activities;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.applock.guardian.R;
import com.applock.guardian.receivers.DeviceAdminReceiver;
import com.applock.guardian.utils.AppPrefs;
import com.applock.guardian.utils.BiometricHelper;

public class SetupActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private int currentStep = 0;

    // Step views
    private TextView tvStepTitle, tvStepDesc;
    private Button btnAction, btnSkip;

    private static final String[][] STEPS = {
        {"Welcome to App Lock Guardian",
         "Protect your apps and phone with fingerprint & face unlock.\n\nNo extra passwords needed — uses your phone's existing fingerprint and face data.",
         "Get Started", ""},
        {"Usage Access",
         "Required to detect which app you open, so we can show the lock screen.\n\nTap Grant → find 'App Lock Guardian' → enable it.",
         "Grant Permission", ""},
        {"Accessibility Service",
         "Required to detect app switching and show lock screen instantly.\n\nTap Enable → find 'App Lock Guardian' → turn it ON.",
         "Enable Service", ""},
        {"Draw Over Apps",
         "Required to show the lock screen on top of other apps.",
         "Allow Permission", ""},
        {"Device Admin (Recommended)",
         "Prevents someone from uninstalling this app without unlocking.\n\nYou can skip this, but the app can be uninstalled easily without it.",
         "Activate Admin", "Skip"},
        {"All Done!",
         "App Lock Guardian is set up.\n\nBy default, no apps are locked. Go to the main screen and select which apps to protect.",
         "Go to App", ""}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);
        prefs = new AppPrefs(this);

        tvStepTitle = findViewById(R.id.tv_step_title);
        tvStepDesc = findViewById(R.id.tv_step_desc);
        btnAction = findViewById(R.id.btn_action);
        btnSkip = findViewById(R.id.btn_skip);

        showStep(0);

        btnAction.setOnClickListener(v -> handleAction());
        btnSkip.setOnClickListener(v -> nextStep());
    }

    private void showStep(int step) {
        currentStep = step;
        tvStepTitle.setText(STEPS[step][0]);
        tvStepDesc.setText(STEPS[step][1]);
        btnAction.setText(STEPS[step][2]);

        String skipText = STEPS[step][3];
        btnSkip.setText(skipText.isEmpty() ? "Next" : skipText);
        if (step == 0 || step == 5) {
            btnSkip.setVisibility(android.view.View.GONE);
        } else {
            btnSkip.setVisibility(android.view.View.VISIBLE);
        }
    }

    private void handleAction() {
        switch (currentStep) {
            case 0: nextStep(); break;
            case 1: openUsageStats(); break;
            case 2: openAccessibility(); break;
            case 3: openOverlay(); break;
            case 4: openDeviceAdmin(); break;
            case 5: finishSetup(); break;
        }
    }

    private void openUsageStats() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    private void openAccessibility() {
        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
    }

    private void openOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else {
            nextStep();
        }
    }

    private void openDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                new ComponentName(this, DeviceAdminReceiver.class));
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Prevents uninstall without authentication.");
        startActivity(intent);
    }

    private void finishSetup() {
        prefs.setSetupDone(true);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void nextStep() {
        if (currentStep < STEPS.length - 1) {
            showStep(currentStep + 1);
        } else {
            finishSetup();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // Auto-advance if permission was granted
        if (currentStep == 1 && isUsageGranted()) nextStep();
        if (currentStep == 2 && isAccessibilityEnabled()) nextStep();
        if (currentStep == 3 && isOverlayGranted()) nextStep();
    }

    private boolean isUsageGranted() {
        try {
            android.app.AppOpsManager appOps =
                    (android.app.AppOpsManager) getSystemService(APP_OPS_SERVICE);
            int mode = appOps.checkOpNoThrow(
                    android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(), getPackageName());
            return mode == android.app.AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    private boolean isAccessibilityEnabled() {
        try {
            String services = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private boolean isOverlayGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }
}
