package com.applock.guardian.activities;

import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.applock.guardian.R;
import com.applock.guardian.adapters.AppListAdapter;
import com.applock.guardian.models.AppInfo;
import com.applock.guardian.receivers.DeviceAdminReceiver;
import com.applock.guardian.services.AppLockAccessibilityService;
import com.applock.guardian.services.AppMonitorService;
import com.applock.guardian.utils.AppPrefs;
import com.applock.guardian.utils.BiometricHelper;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private AppPrefs prefs;
    private AppListAdapter adapter;
    private List<AppInfo> appList = new ArrayList<>();

    // Permission status views
    private View statusAccessibility, statusUsageStats, statusDeviceAdmin, statusOverlay;
    private ImageView iconAccessibility, iconUsageStats, iconDeviceAdmin, iconOverlay;

    // Switches
    private Switch switchLockScreen, switchFingerprint, switchFace;

    // Card: apps
    private RecyclerView recyclerApps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new AppPrefs(this);

        // First run → setup
        if (!prefs.isSetupDone()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }

        initViews();
        loadApps();
        startMonitorService();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
        updateSwitches();
    }

    private void initViews() {
        // Permission cards
        statusAccessibility = findViewById(R.id.card_accessibility);
        statusUsageStats = findViewById(R.id.card_usage_stats);
        statusDeviceAdmin = findViewById(R.id.card_device_admin);
        statusOverlay = findViewById(R.id.card_overlay);

        iconAccessibility = findViewById(R.id.icon_accessibility);
        iconUsageStats = findViewById(R.id.icon_usage);
        iconDeviceAdmin = findViewById(R.id.icon_device_admin);
        iconOverlay = findViewById(R.id.icon_overlay);

        // Click handlers for permission cards
        statusAccessibility.setOnClickListener(v -> requestAccessibility());
        statusUsageStats.setOnClickListener(v -> requestUsageStats());
        statusDeviceAdmin.setOnClickListener(v -> requestDeviceAdmin());
        statusOverlay.setOnClickListener(v -> requestOverlay());

        // Switches
        switchLockScreen = findViewById(R.id.switch_lock_screen);
        switchFingerprint = findViewById(R.id.switch_fingerprint);
        switchFace = findViewById(R.id.switch_face);

        switchLockScreen.setChecked(prefs.isLockScreenEnabled());
        switchFingerprint.setChecked(prefs.isFingerprintEnabled());
        switchFace.setChecked(prefs.isFaceEnabled());

        switchLockScreen.setOnCheckedChangeListener((btn, checked) -> {
            prefs.setLockScreenEnabled(checked);
            Toast.makeText(this, checked ? "Lock Screen ON" : "Lock Screen OFF",
                    Toast.LENGTH_SHORT).show();
        });

        switchFingerprint.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !BiometricHelper.isBiometricAvailable(this)) {
                btn.setChecked(false);
                Toast.makeText(this, "No fingerprint enrolled in phone settings",
                        Toast.LENGTH_LONG).show();
                return;
            }
            prefs.setFingerprintEnabled(checked);
        });

        switchFace.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !BiometricHelper.isFaceAvailable(this)) {
                btn.setChecked(false);
                Toast.makeText(this, "No face data enrolled in phone settings",
                        Toast.LENGTH_LONG).show();
                return;
            }
            prefs.setFaceEnabled(checked);
        });

        // RecyclerView
        recyclerApps = findViewById(R.id.recycler_apps);
        recyclerApps.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AppListAdapter(appList, prefs, this);
        recyclerApps.setAdapter(adapter);

        // Settings button
        findViewById(R.id.btn_settings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void updatePermissionStatus() {
        boolean accessOk = isAccessibilityEnabled();
        boolean usageOk = isUsageStatsGranted();
        boolean adminOk = isDeviceAdminActive();
        boolean overlayOk = isOverlayGranted();

        updateStatusIcon(iconAccessibility, accessOk);
        updateStatusIcon(iconUsageStats, usageOk);
        updateStatusIcon(iconDeviceAdmin, adminOk);
        updateStatusIcon(iconOverlay, overlayOk);
    }

    private void updateStatusIcon(ImageView icon, boolean ok) {
        icon.setImageResource(ok ? R.drawable.ic_check_circle : R.drawable.ic_warning);
        icon.setColorFilter(ok ?
                getColor(R.color.green_ok) :
                getColor(R.color.orange_warn));
    }

    private void updateSwitches() {
        switchLockScreen.setChecked(prefs.isLockScreenEnabled());
        switchFingerprint.setChecked(prefs.isFingerprintEnabled());
        switchFace.setChecked(prefs.isFaceEnabled());
    }

    private void loadApps() {
        new Thread(() -> {
            List<AppInfo> loaded = getInstalledApps();
            runOnUiThread(() -> {
                appList.clear();
                appList.addAll(loaded);
                adapter.notifyDataSetChanged();
            });
        }).start();
    }

    private List<AppInfo> getInstalledApps() {
        List<AppInfo> list = new ArrayList<>();
        List<android.content.pm.PackageInfo> packages =
                getPackageManager().getInstalledPackages(0);

        for (android.content.pm.PackageInfo pkg : packages) {
            // Skip system apps and this app itself
            if (pkg.packageName.equals(getPackageName())) continue;
            if ((pkg.applicationInfo.flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) {
                // Only include system apps that have a launcher icon
                Intent launcher = new Intent(Intent.ACTION_MAIN);
                launcher.addCategory(Intent.CATEGORY_LAUNCHER);
                launcher.setPackage(pkg.packageName);
                if (getPackageManager().queryIntentActivities(launcher, 0).isEmpty()) continue;
            }

            try {
                String name = getPackageManager()
                        .getApplicationLabel(pkg.applicationInfo).toString();
                android.graphics.drawable.Drawable icon =
                        getPackageManager().getApplicationIcon(pkg.applicationInfo);
                boolean locked = prefs.isAppLocked(pkg.packageName);
                boolean isSystem = (pkg.applicationInfo.flags &
                        android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;

                list.add(new AppInfo(name, pkg.packageName, icon, locked, isSystem));
            } catch (Exception ignored) {}
        }

        // Sort: locked first, then alphabetical
        list.sort((a, b) -> {
            if (a.isLocked() != b.isLocked()) return a.isLocked() ? -1 : 1;
            return a.getAppName().compareToIgnoreCase(b.getAppName());
        });
        return list;
    }

    private void startMonitorService() {
        Intent intent = new Intent(this, AppMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    // ── Permission checks ─────────────────────────────────────────
    private boolean isAccessibilityEnabled() {
        try {
            int enabled = Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED);
            if (enabled == 0) return false;
            String services = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return services != null && services.contains(getPackageName());
        } catch (Exception e) { return false; }
    }

    private boolean isUsageStatsGranted() {
        try {
            AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
            if (appOps == null) return false;
            int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) { return false; }
    }

    private boolean isDeviceAdminActive() {
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(DEVICE_POLICY_SERVICE);
        ComponentName cn = new ComponentName(this, DeviceAdminReceiver.class);
        return dpm != null && dpm.isAdminActive(cn);
    }

    private boolean isOverlayGranted() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
                Settings.canDrawOverlays(this);
    }

    // ── Permission requests ───────────────────────────────────────
    private void requestAccessibility() {
        new AlertDialog.Builder(this)
                .setTitle("Enable Accessibility")
                .setMessage("Enable 'App Lock Guardian' in Accessibility Settings to detect when apps are opened.")
                .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    private void requestUsageStats() {
        new AlertDialog.Builder(this)
                .setTitle("Usage Access")
                .setMessage("Grant Usage Access permission so we can detect which app is in foreground.")
                .setPositiveButton("Open Settings", (d, w) ->
                        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)))
                .setNegativeButton("Later", null)
                .show();
    }

    private void requestDeviceAdmin() {
        Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
        intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                new ComponentName(this, DeviceAdminReceiver.class));
        intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Required to prevent uninstall and lock device screen.");
        startActivity(intent);
    }

    private void requestOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }
}
