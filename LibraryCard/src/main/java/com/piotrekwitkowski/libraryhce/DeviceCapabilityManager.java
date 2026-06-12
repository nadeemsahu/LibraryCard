package com.piotrekwitkowski.libraryhce;

import android.content.Context;
import android.content.pm.PackageManager;
import android.nfc.NfcAdapter;
import android.os.Build;
import androidx.biometric.BiometricManager;

/**
 * Centralized, thread-safe manager for auditing and monitoring 
 * device capabilities (NFC hardware, HCE engine, biometrics, OS version).
 */
public class DeviceCapabilityManager {

    public static class AuditedCapabilities {
        public final boolean hasNfcHardware;
        public final boolean isNfcEnabled;
        public final boolean hasHceSupport;
        public final boolean isAndroidVersionSupported;
        public final boolean isBiometricOrDeviceCredentialAvailable;

        public AuditedCapabilities(boolean hasNfcHardware, boolean isNfcEnabled, 
                                   boolean hasHceSupport, boolean isAndroidVersionSupported, 
                                   boolean isBiometricOrDeviceCredentialAvailable) {
            this.hasNfcHardware = hasNfcHardware;
            this.isNfcEnabled = isNfcEnabled;
            this.hasHceSupport = hasHceSupport;
            this.isAndroidVersionSupported = isAndroidVersionSupported;
            this.isBiometricOrDeviceCredentialAvailable = isBiometricOrDeviceCredentialAvailable;
        }

        public boolean isWalletFullySupported() {
            return hasNfcHardware && hasHceSupport && isAndroidVersionSupported;
        }
    }

    /**
     * Conducts an instant capability audit on the current device state.
     */
    public static AuditedCapabilities auditDevice(Context context) {
        PackageManager pm = context.getPackageManager();
        
        // 1. Check NFC hardware feature support
        boolean hasNfcHardware = pm.hasSystemFeature(PackageManager.FEATURE_NFC);
        NfcAdapter adapter = null;
        try {
            adapter = NfcAdapter.getDefaultAdapter(context);
        } catch (Exception e) {
            // Defensive null/exception handling for custom ROMs
        }
        if (adapter == null) {
            hasNfcHardware = false;
        }

        // 2. Check NFC enabled state
        boolean isNfcEnabled = false;
        if (adapter != null) {
            try {
                isNfcEnabled = adapter.isEnabled();
            } catch (Exception e) {
                // Secure adapter access
            }
        }

        // 3. Check Host Card Emulation (HCE) capability
        boolean hasHceSupport = pm.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION);

        // 4. Android Version Compatibility (Require Android 9+ / API 28 for modern security layers)
        boolean isAndroidVersionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P;

        // 5. Biometric or Device PIN/Pattern/Password Configuration check
        boolean isBiometricOrDeviceCredentialAvailable = false;
        try {
            BiometricManager biometricManager = BiometricManager.from(context);
            if (biometricManager != null) {
                int canAuthenticate = biometricManager.canAuthenticate(
                        BiometricManager.Authenticators.BIOMETRIC_STRONG | 
                        BiometricManager.Authenticators.DEVICE_CREDENTIAL
                );
                isBiometricOrDeviceCredentialAvailable = (canAuthenticate == BiometricManager.BIOMETRIC_SUCCESS);
            }
        } catch (Throwable t) {
            // Guard against potential support library incompatibilities or missing system service binders
        }

        return new AuditedCapabilities(
            hasNfcHardware,
            isNfcEnabled,
            hasHceSupport,
            isAndroidVersionSupported,
            isBiometricOrDeviceCredentialAvailable
        );
    }
}
