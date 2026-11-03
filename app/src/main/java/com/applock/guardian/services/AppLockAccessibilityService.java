package com.applock.guardian.services;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;

import com.applock.guardian.activities.AppLockActivity;
import com.applock.guardian.utils.AppPrefs;

/**
 * AppLockAccessibilityService — gets notified when any window changes.
 * This is faster and more reliable than polling UsageStats.
 * Both methods work together for maximum compatibility.
 */
public class AppLockAccessibilityService extends AccessibilityService {

    private AppPrefs prefs;
    private String lastLockedPackage = "";

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        prefs = new AppPrefs(this);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;

        CharSequence pkgSeq = event.getPackageName();
        if (pkgSeq == null) return;

        String packageName = pkgSeq.toString();

        // Skip our own app
        if (packageName.equals(getPackageName())) {
            lastLockedPackage = "";
            return;
        }

        // If the incoming app is locked and we haven't just locked it
        if (prefs != null && prefs.isAppLocked(packageName) &&
                !packageName.equals(lastLockedPackage)) {
            lastLockedPackage = packageName;
            showLockScreen(packageName);
        } else if (!prefs.isAppLocked(packageName)) {
            lastLockedPackage = "";
        }
    }

    private void showLockScreen(String packageName) {
        String appName = packageName;
        try {
            appName = getPackageManager()
                    .getApplicationLabel(getPackageManager().getApplicationInfo(packageName, 0))
                    .toString();
        } catch (Exception ignored) {}

        Intent intent = new Intent(this, AppLockActivity.class);
        intent.putExtra(AppLockActivity.EXTRA_PACKAGE, packageName);
        intent.putExtra(AppLockActivity.EXTRA_APP_NAME, appName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                        Intent.FLAG_ACTIVITY_SINGLE_TOP |
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        startActivity(intent);
    }

    @Override
    public void onInterrupt() {
        // Required — do nothing
    }
}
