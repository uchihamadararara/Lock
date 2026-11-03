package com.applock.guardian.services;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.applock.guardian.AppLockApplication;
import com.applock.guardian.R;
import com.applock.guardian.activities.AppLockActivity;
import com.applock.guardian.activities.MainActivity;
import com.applock.guardian.utils.AppPrefs;

/**
 * AppMonitorService — runs in foreground, polls every 250ms for foreground app.
 * When a locked app comes to foreground → shows AppLockActivity on top.
 *
 * Uses UsageStatsManager (requires PACKAGE_USAGE_STATS permission).
 */
public class AppMonitorService extends Service {

    private static final int NOTIFICATION_ID = 1001;
    private static final long POLL_INTERVAL_MS = 250;

    private AppPrefs prefs;
    private Handler handler = new Handler();
    private String lastLockedPackage = "";
    private boolean isRunning = false;

    private Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (isRunning) {
                checkForegroundApp();
                handler.postDelayed(this, POLL_INTERVAL_MS);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = new AppPrefs(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        isRunning = true;
        handler.post(pollRunnable);
        return START_STICKY; // restart if killed
    }

    private void checkForegroundApp() {
        String foregroundPkg = getForegroundPackage();
        if (foregroundPkg == null || foregroundPkg.isEmpty()) return;

        // Skip our own app
        if (foregroundPkg.equals(getPackageName())) {
            lastLockedPackage = "";
            return;
        }

        // Check if this app is locked
        if (prefs.isAppLocked(foregroundPkg) && !foregroundPkg.equals(lastLockedPackage)) {
            lastLockedPackage = foregroundPkg;
            showAppLock(foregroundPkg);
        } else if (!prefs.isAppLocked(foregroundPkg)) {
            lastLockedPackage = "";
        }
    }

    private String getForegroundPackage() {
        try {
            UsageStatsManager usm = (UsageStatsManager) getSystemService(
                    Context.USAGE_STATS_SERVICE);
            if (usm == null) return null;

            long time = System.currentTimeMillis();
            UsageEvents events = usm.queryEvents(time - 3000, time);
            UsageEvents.Event lastEvent = new UsageEvents.Event();
            String currentPkg = null;

            while (events.hasNextEvent()) {
                UsageEvents.Event event = new UsageEvents.Event();
                events.getNextEvent(event);
                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    currentPkg = event.getPackageName();
                }
            }
            return currentPkg;
        } catch (Exception e) {
            return null;
        }
    }

    private void showAppLock(String packageName) {
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
                        Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }

    private Notification buildNotification() {
        Intent mainIntent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, AppLockApplication.CHANNEL_ID)
                .setContentTitle("App Lock Guardian")
                .setContentText("Protecting your apps")
                .setSmallIcon(R.drawable.ic_shield)
                .setContentIntent(pi)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setSilent(true)
                .build();
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        handler.removeCallbacks(pollRunnable);

        // Restart self if destroyed
        Intent restart = new Intent(this, AppMonitorService.class);
        startService(restart);
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // Restart when task is removed
        Intent restart = new Intent(this, AppMonitorService.class);
        startService(restart);
    }
}
