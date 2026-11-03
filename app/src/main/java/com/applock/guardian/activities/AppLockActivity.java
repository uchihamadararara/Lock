package com.applock.guardian.activities;

import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.applock.guardian.R;
import com.applock.guardian.utils.AppPrefs;
import com.applock.guardian.utils.BiometricHelper;
import com.applock.guardian.utils.FaceDetectHelper;

public class AppLockActivity extends FragmentActivity {

    public static final String EXTRA_PACKAGE = "extra_package";
    public static final String EXTRA_APP_NAME = "extra_app_name";

    private AppPrefs prefs;
    private FaceDetectHelper faceDetectHelper;
    private boolean isAuthenticating = false;
    private boolean unlocked = false;
    private String targetPackage;

    private Handler uiHandler = new Handler();

    // Views
    private ImageView ivAppIcon;
    private TextView tvAppName, tvLockHint;
    private View fingerprintArea, fingerprintRipple, rootLayout;
    private TextureView hiddenCameraView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Show over lock screen
        Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
        }

        setContentView(R.layout.activity_app_lock);
        prefs = new AppPrefs(this);

        targetPackage = getIntent().getStringExtra(EXTRA_PACKAGE);
        String appName = getIntent().getStringExtra(EXTRA_APP_NAME);
        if (appName == null) appName = "App";

        initViews(appName);
        setupSystemUI();
        loadAppIcon();

        uiHandler.postDelayed(this::startAuth, 300);
    }

    private void initViews(String appName) {
        rootLayout = findViewById(R.id.root_layout);
        ivAppIcon = findViewById(R.id.iv_app_icon);
        tvAppName = findViewById(R.id.tv_app_name);
        tvLockHint = findViewById(R.id.tv_lock_hint);
        fingerprintArea = findViewById(R.id.fingerprint_area);
        fingerprintRipple = findViewById(R.id.fingerprint_ripple);
        hiddenCameraView = findViewById(R.id.hidden_camera_view);

        tvAppName.setText(appName);

        // Fingerprint area touch
        fingerprintArea.setOnClickListener(v -> {
            showFingerprintRipple();
            if (!isAuthenticating && !unlocked) {
                triggerBiometricAuth();
            }
        });
    }

    private void loadAppIcon() {
        if (targetPackage == null) return;
        try {
            Drawable icon = getPackageManager().getApplicationIcon(targetPackage);
            ivAppIcon.setImageDrawable(icon);
        } catch (Exception ignored) {}
    }

    private void setupSystemUI() {
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = decorView.getWindowInsetsController();
            if (c != null) {
                c.hide(android.view.WindowInsets.Type.statusBars() |
                        android.view.WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            );
        }
    }

    private void startAuth() {
        if (unlocked) return;
        if (prefs.isFaceEnabled()) startFaceDetection();
        if (prefs.isFingerprintEnabled()) triggerBiometricAuth();
    }

    private void triggerBiometricAuth() {
        if (isAuthenticating || unlocked) return;
        isAuthenticating = true;

        BiometricHelper.authenticateSilent(this, prefs.isFaceEnabled(),
                new BiometricHelper.AuthCallback() {
                    @Override
                    public void onSuccess() {
                        runOnUiThread(() -> doUnlock());
                    }
                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            isAuthenticating = false;
                            showShake();
                        });
                    }
                    @Override
                    public void onFailed() {
                        runOnUiThread(() -> {
                            isAuthenticating = false;
                            vibrateError();
                        });
                    }
                });
    }

    private void startFaceDetection() {
        if (hiddenCameraView == null) return;
        faceDetectHelper = new FaceDetectHelper(this, hiddenCameraView,
                new FaceDetectHelper.FaceDetectCallback() {
                    @Override
                    public void onFaceDetected() {
                        runOnUiThread(() -> {
                            if (!unlocked) triggerBiometricAuth();
                        });
                    }
                    @Override public void onNoFace() {}
                    @Override public void onError(String msg) {}
                });
        faceDetectHelper.startDetection();
    }

    private void doUnlock() {
        if (unlocked) return;
        unlocked = true;
        isAuthenticating = false;
        if (faceDetectHelper != null) faceDetectHelper.stopDetection();

        Animation fade = AnimationUtils.loadAnimation(this, android.R.anim.fade_out);
        fade.setDuration(200);
        fade.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                finish();
                overridePendingTransition(0, 0);
            }
        });
        rootLayout.startAnimation(fade);
        vibrateSuccess();
    }

    private void showFingerprintRipple() {
        fingerprintRipple.setVisibility(View.VISIBLE);
        Animation anim = AnimationUtils.loadAnimation(this, R.anim.ripple_scale);
        anim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override public void onAnimationEnd(Animation a) {
                fingerprintRipple.setVisibility(View.INVISIBLE);
            }
        });
        fingerprintRipple.startAnimation(anim);
        vibrate(40);
    }

    private void showShake() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        fingerprintArea.startAnimation(shake);
        vibrateError();
    }

    private void vibrate(long ms) {
        if (!prefs.isHapticFeedback()) return;
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            v.vibrate(ms);
        }
    }

    private void vibrateSuccess() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 60, 40, 60}, -1));
        } else {
            v.vibrate(new long[]{0, 60, 40, 60}, -1);
        }
    }

    private void vibrateError() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(new long[]{0, 100, 80, 100}, -1));
        } else {
            v.vibrate(new long[]{0, 100, 80, 100}, -1);
        }
    }

    @Override
    public void onBackPressed() {
        // Go to home — don't allow back into the locked app
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        home.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(home);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (faceDetectHelper != null) faceDetectHelper.stopDetection();
    }
}
