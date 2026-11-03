package com.applock.guardian.utils;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public class AppPrefs {
    private static final String PREF_NAME = "AppLockGuardianPrefs";

    // Keys
    private static final String KEY_SETUP_DONE = "setup_done";
    private static final String KEY_LOCK_TYPE = "lock_type"; // "fingerprint", "face", "both"
    private static final String KEY_LOCKED_APPS = "locked_apps";
    private static final String KEY_LOCK_SCREEN_ENABLED = "lock_screen_enabled";
    private static final String KEY_FINGERPRINT_ENABLED = "fingerprint_enabled";
    private static final String KEY_FACE_ENABLED = "face_enabled";
    private static final String KEY_SHOW_CLOCK = "show_clock";
    private static final String KEY_WALLPAPER_BLUR = "wallpaper_blur";
    private static final String KEY_HAPTIC_FEEDBACK = "haptic_feedback";
    private static final String KEY_INTRUDER_SELFIE = "intruder_selfie";
    private static final String KEY_DEVICE_ADMIN_HINT_SHOWN = "device_admin_hint_shown";
    private static final String KEY_ACCESSIBILITY_HINT_SHOWN = "accessibility_hint_shown";

    private final SharedPreferences prefs;

    public AppPrefs(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // ─── Setup ───────────────────────────────────────────────
    public boolean isSetupDone() {
        return prefs.getBoolean(KEY_SETUP_DONE, false);
    }

    public void setSetupDone(boolean done) {
        prefs.edit().putBoolean(KEY_SETUP_DONE, done).apply();
    }

    // ─── Lock Type ───────────────────────────────────────────
    public String getLockType() {
        return prefs.getString(KEY_LOCK_TYPE, "fingerprint");
    }

    public void setLockType(String type) {
        prefs.edit().putString(KEY_LOCK_TYPE, type).apply();
    }

    // ─── Locked Apps ─────────────────────────────────────────
    public Set<String> getLockedApps() {
        return new HashSet<>(prefs.getStringSet(KEY_LOCKED_APPS, new HashSet<>()));
    }

    public void addLockedApp(String packageName) {
        Set<String> apps = getLockedApps();
        apps.add(packageName);
        prefs.edit().putStringSet(KEY_LOCKED_APPS, apps).apply();
    }

    public void removeLockedApp(String packageName) {
        Set<String> apps = getLockedApps();
        apps.remove(packageName);
        prefs.edit().putStringSet(KEY_LOCKED_APPS, apps).apply();
    }

    public boolean isAppLocked(String packageName) {
        return getLockedApps().contains(packageName);
    }

    public void setLockedApps(Set<String> apps) {
        prefs.edit().putStringSet(KEY_LOCKED_APPS, apps).apply();
    }

    // ─── Lock Screen ─────────────────────────────────────────
    public boolean isLockScreenEnabled() {
        return prefs.getBoolean(KEY_LOCK_SCREEN_ENABLED, false);
    }

    public void setLockScreenEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_LOCK_SCREEN_ENABLED, enabled).apply();
    }

    // ─── Biometrics ──────────────────────────────────────────
    public boolean isFingerprintEnabled() {
        return prefs.getBoolean(KEY_FINGERPRINT_ENABLED, true);
    }

    public void setFingerprintEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FINGERPRINT_ENABLED, enabled).apply();
    }

    public boolean isFaceEnabled() {
        return prefs.getBoolean(KEY_FACE_ENABLED, false);
    }

    public void setFaceEnabled(boolean enabled) {
        prefs.edit().putBoolean(KEY_FACE_ENABLED, enabled).apply();
    }

    // ─── UI Prefs ────────────────────────────────────────────
    public boolean isShowClock() {
        return prefs.getBoolean(KEY_SHOW_CLOCK, true);
    }

    public void setShowClock(boolean show) {
        prefs.edit().putBoolean(KEY_SHOW_CLOCK, show).apply();
    }

    public boolean isWallpaperBlur() {
        return prefs.getBoolean(KEY_WALLPAPER_BLUR, true);
    }

    public void setWallpaperBlur(boolean blur) {
        prefs.edit().putBoolean(KEY_WALLPAPER_BLUR, blur).apply();
    }

    public boolean isHapticFeedback() {
        return prefs.getBoolean(KEY_HAPTIC_FEEDBACK, true);
    }

    public void setHapticFeedback(boolean haptic) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK, haptic).apply();
    }

    public boolean isIntruderSelfie() {
        return prefs.getBoolean(KEY_INTRUDER_SELFIE, false);
    }

    public void setIntruderSelfie(boolean enabled) {
        prefs.edit().putBoolean(KEY_INTRUDER_SELFIE, enabled).apply();
    }

    // ─── Hint flags ──────────────────────────────────────────
    public boolean isDeviceAdminHintShown() {
        return prefs.getBoolean(KEY_DEVICE_ADMIN_HINT_SHOWN, false);
    }

    public void setDeviceAdminHintShown(boolean shown) {
        prefs.edit().putBoolean(KEY_DEVICE_ADMIN_HINT_SHOWN, shown).apply();
    }

    public boolean isAccessibilityHintShown() {
        return prefs.getBoolean(KEY_ACCESSIBILITY_HINT_SHOWN, false);
    }

    public void setAccessibilityHintShown(boolean shown) {
        prefs.edit().putBoolean(KEY_ACCESSIBILITY_HINT_SHOWN, shown).apply();
    }
}
