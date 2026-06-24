package com.piotrekwitkowski.libraryhce;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import java.util.concurrent.Executor;

import com.piotrekwitkowski.libraryhce.domain.Authenticator;

public class BiometricAuthenticator implements Authenticator {

    // AuthCallback is now defined in Authenticator interface

    private final AppCompatActivity activity;
    private final Executor executor;

    public BiometricAuthenticator(AppCompatActivity activity) {
        this.activity = activity;
        this.executor = ContextCompat.getMainExecutor(activity);
    }

    @Override
    public void authenticate(String title, String subtitle, String negativeButtonText, int allowedAuthenticators, Authenticator.AuthCallback callback) {
        BiometricManager biometricManager = BiometricManager.from(activity);
        switch (biometricManager.canAuthenticate(allowedAuthenticators)) {
            case BiometricManager.BIOMETRIC_SUCCESS:
                break;
            case BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE:
            case BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE:
            case BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED:
                callback.onFailed("Biometrics unavailable or not enrolled.");
                return;
        }

        BiometricPrompt biometricPrompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                            callback.onCancelled();
                        } else {
                            callback.onFailed(errString.toString());
                        }
                    }

                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        callback.onSuccess();
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        callback.onFailed("Authentication failed");
                    }
                });

        BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle);

        if (negativeButtonText != null) {
            promptBuilder.setNegativeButtonText(negativeButtonText);
        } else {
            promptBuilder.setAllowedAuthenticators(allowedAuthenticators);
        }

        try {
            biometricPrompt.authenticate(promptBuilder.build());
        } catch (Exception e) {
            callback.onFailed(e.getMessage());
        }
    }
}
