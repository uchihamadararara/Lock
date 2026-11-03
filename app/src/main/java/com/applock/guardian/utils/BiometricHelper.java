package com.applock.guardian.utils;

import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.util.concurrent.Executor;

/**
 * BiometricHelper — uses Android's system biometric APIs.
 * This uses the SAME enrolled fingerprint/face data from phone settings.
 * No separate enrollment needed.
 */
public class BiometricHelper {

    public interface AuthCallback {
        void onSuccess();
        void onError(String message);
        void onFailed();
    }

    /**
     * Check if biometric hardware + enrolled data is available
     */
    public static boolean isBiometricAvailable(Context context) {
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG |
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Check if face biometric is available (Android 10+)
     */
    public static boolean isFaceAvailable(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false;
        BiometricManager biometricManager = BiometricManager.from(context);
        int result = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_WEAK
        );
        return result == BiometricManager.BIOMETRIC_SUCCESS;
    }

    /**
     * Prompt for biometric auth — uses system enrolled fingerprint/face.
     * No UI button needed — call this method and the system handles the prompt.
     *
     * @param activity     The calling activity
     * @param title        Title shown (we hide this in our custom UI)
     * @param allowFace    Whether to allow face unlock too
     * @param callback     Result callback
     */
    public static void authenticate(
            FragmentActivity activity,
            String title,
            boolean allowFace,
            AuthCallback callback
    ) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_CANCELED &&
                                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            callback.onError(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        callback.onFailed();
                    }
                });

        BiometricPrompt.PromptInfo.Builder builder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle("")
                .setDescription("")
                .setNegativeButtonText("Cancel");

        // Allow weak biometrics (face) if requested
        if (allowFace) {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG |
                    BiometricManager.Authenticators.BIOMETRIC_WEAK
            );
        } else {
            builder.setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG
            );
        }

        try {
            biometricPrompt.authenticate(builder.build());
        } catch (Exception e) {
            callback.onError("Biometric auth failed: " + e.getMessage());
        }
    }

    /**
     * Silent authentication — triggers immediately without showing Android's
     * default bottom sheet UI. Good for lock screen where we have custom UI.
     */
    public static void authenticateSilent(
            FragmentActivity activity,
            boolean allowFace,
            AuthCallback callback
    ) {
        authenticate(activity, "Unlock", allowFace, callback);
    }
}
