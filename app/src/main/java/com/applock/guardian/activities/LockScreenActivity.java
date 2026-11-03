package com.applock.guardian.activities;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.TextView;

import androidx.fragment.app.FragmentActivity;

import com.applock.guardian.R;
import com.applock.guardian.utils.AppPrefs;
import com.applock.guardian.utils.BiometricHelper;
import com.applock.guardian.utils.FaceDetectHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

public class LockScreenActivity extends FragmentActivity {

    private AppPrefs prefs;
    private FaceDetectHelper faceDetectHelper;
    private boolean isAuthenticating = false;
    private boolean unlocked = false;

    // Views
    private TextView tvTime, tvDate, tvSwipeHint;
    private View fingerprintRipple, fingerprintArea, rootLayout;
    private TextureView hiddenCameraView;

    private Timer clockTimer;
    private Handler uiHandler = new Handler();

    // Broadcast receiver to handle screen off while showing lock screen
    private BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // do nothing — we stay on top
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ── Window flags — show on top of keyguard ───────────────
        Window window = getWindow();
        window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_FULLSCREEN
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        }

        setContentView(R.layout.activity_lock_screen);
        prefs = new AppPrefs(this);

        initViews();
        setupSystemUI();
        startClock();

        // Register screen receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        // Start auth after short delay (let UI render)
        uiHandler.postDelayed(this::startAuth, 400);
    }

    private void initViews() {
        tvTime = findViewById(R.id.tv_time);
        tvDate = findViewById(R.id.tv_date);
        tvSwipeHint = findViewById(R.id.tv_swipe_hint);
        fingerprintRipple = findViewById(R.id.fingerprint_ripple);
        fingerprintArea = findViewById(R.id.fingerprint_area);
        rootLayout = findViewById(R.id.root_layout);
        hiddenCameraView = findViewById(R.id.hidden_camera_view);

        // Fingerprint area — touch triggers ripple + biometric
        fingerprintArea.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showFingerprintRipple();
                if (!isAuthenticating && !unlocked) {
                    triggerFingerprintAuth();
                }
            }
            return true;
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupSystemUI() {
        // Full immersive — hide status bar and nav bar
        View decorView = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = decorView.getWindowInsetsController();
            if (controller != null) {
                controller.hide(android.view.WindowInsets.Type.statusBars() |
                        android.view.WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            decorView.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    // ── Clock ─────────────────────────────────────────────────────
    private void startClock() {
        updateClock();
        clockTimer = new Timer();
        clockTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                uiHandler.post(() -> updateClock());
            }
        }, 1000, 1000);
    }

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        boolean is24 = DateFormat.is24HourFormat(this);
        String timePattern = is24 ? "HH:mm" : "h:mm";
        String datePattern = "EEEE, d MMMM";
        tvTime.setText(new SimpleDateFormat(timePattern, Locale.getDefault()).format(cal.getTime()));
        tvDate.setText(new SimpleDateFormat(datePattern, Locale.getDefault()).format(cal.getTime()));
    }

    // ── Auth ──────────────────────────────────────────────────────
    private void startAuth() {
        if (unlocked) return;
        AppPrefs prefs = new AppPrefs(this);

        if (prefs.isFaceEnabled()) {
            startFaceDetection();
        }
        if (prefs.isFingerprintEnabled()) {
            // Fingerprint is triggered on touch (see fingerprintArea touch listener)
            // But also auto-trigger the biometric prompt
            triggerFingerprintAuth();
        }
    }

    private void triggerFingerprintAuth() {
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
                            showShakeAnimation();
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
                        // Face found — use system biometric to verify
                        // (this triggers the actual biometric auth using enrolled face data)
                        runOnUiThread(() -> {
                            if (!unlocked) {
                                triggerFingerprintAuth(); // triggers face if enrolled
                            }
                        });
                    }
                    @Override
                    public void onNoFace() { }
                    @Override
                    public void onError(String message) { }
                });
        faceDetectHelper.startDetection();
    }

    private void doUnlock() {
        if (unlocked) return;
        unlocked = true;

        if (faceDetectHelper != null) faceDetectHelper.stopDetection();

        // Success animation then finish
        Animation slideUp = AnimationUtils.loadAnimation(this, R.anim.slide_up_exit);
        slideUp.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                finish();
                overridePendingTransition(0, 0);
            }
        });
        rootLayout.startAnimation(slideUp);
        vibrateSuccess();
    }

    // ── Fingerprint Ripple Animation ──────────────────────────────
    private void showFingerprintRipple() {
        fingerprintRipple.setVisibility(View.VISIBLE);
        fingerprintRipple.setAlpha(0.6f);

        ScaleAnimation scaleAnim = new ScaleAnimation(
                0.5f, 2.5f, 0.5f, 2.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        scaleAnim.setDuration(600);

        AlphaAnimation alphaAnim = new AlphaAnimation(0.6f, 0f);
        alphaAnim.setDuration(600);
        alphaAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override public void onAnimationStart(Animation a) {}
            @Override public void onAnimationRepeat(Animation a) {}
            @Override
            public void onAnimationEnd(Animation a) {
                fingerprintRipple.setVisibility(View.INVISIBLE);
            }
        });

        fingerprintRipple.startAnimation(scaleAnim);
        fingerprintRipple.startAnimation(alphaAnim);

        vibrate(50);
    }

    // ── Haptic feedback ───────────────────────────────────────────
    private void vibrate(long ms) {
        if (!prefs.isHapticFeedback()) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
        } else {
            vibrator.vibrate(ms);
        }
    }

    private void vibrateSuccess() {
        if (!prefs.isHapticFeedback()) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 80, 50, 80}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 80, 50, 80}, -1);
        }
    }

    private void vibrateError() {
        if (!prefs.isHapticFeedback()) return;
        Vibrator vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (vibrator == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0, 100, 100, 100}, -1));
        } else {
            vibrator.vibrate(new long[]{0, 100, 100, 100}, -1);
        }
    }

    private void showShakeAnimation() {
        Animation shake = AnimationUtils.loadAnimation(this, R.anim.shake);
        fingerprintArea.startAnimation(shake);
        vibrateError();
    }

    // ── Lifecycle ─────────────────────────────────────────────────
    @Override
    protected void onResume() {
        super.onResume();
        setupSystemUI();
        if (!unlocked && !isAuthenticating) {
            uiHandler.postDelayed(this::startAuth, 300);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        isAuthenticating = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (clockTimer != null) clockTimer.cancel();
        if (faceDetectHelper != null) faceDetectHelper.stopDetection();
        try { unregisterReceiver(screenReceiver); } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // Do NOT allow back — lock screen stays
    }
}
