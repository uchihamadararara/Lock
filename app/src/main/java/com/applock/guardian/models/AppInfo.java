package com.applock.guardian.models;

import android.graphics.drawable.Drawable;

public class AppInfo {
    private String appName;
    private String packageName;
    private Drawable icon;
    private boolean isLocked;
    private boolean isSystemApp;

    public AppInfo(String appName, String packageName, Drawable icon,
                   boolean isLocked, boolean isSystemApp) {
        this.appName = appName;
        this.packageName = packageName;
        this.icon = icon;
        this.isLocked = isLocked;
        this.isSystemApp = isSystemApp;
    }

    public String getAppName() { return appName; }
    public String getPackageName() { return packageName; }
    public Drawable getIcon() { return icon; }
    public boolean isLocked() { return isLocked; }
    public boolean isSystemApp() { return isSystemApp; }

    public void setLocked(boolean locked) { isLocked = locked; }
}
