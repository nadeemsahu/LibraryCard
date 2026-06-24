package com.piotrekwitkowski.libraryhce;

import android.nfc.cardemulation.HostApduService;
import android.os.Bundle;

import com.piotrekwitkowski.nfc.se.SecureElement;
import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.ByteUtils;
import com.piotrekwitkowski.nfc.Iso7816;
import com.piotrekwitkowski.nfc.se.Application;
import com.piotrekwitkowski.nfc.se.Emulation;
import com.piotrekwitkowski.nfc.desfire.InvalidParameterException;
import com.piotrekwitkowski.libraryhce.application.DynamicLibraryApplication;
import com.piotrekwitkowski.libraryhce.domain.PaymentStateRepository;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class HCEService extends HostApduService {
    private static final String TAG = "HCEService";
    private boolean firstInteraction = true;
    private Emulation emulation;
    private final NotificationService notifications = new NotificationService(this);

    @Inject
    PaymentStateRepository paymentStateRepository;

    @Override
    public byte[] processCommandApdu(byte[] command, Bundle extras) {
        if (!paymentStateRepository.isAuthorized().getValue()) {
            Log.i(TAG, "APDU Rejected: Emulation requires biometric authentication.");
            return new byte[] { (byte) 0x6A, (byte) 0x82 }; // File not found / Blocked
        }

        // Trigger real-time UI response on front-end
        paymentStateRepository.notifyInteraction();

        byte[] response = firstInteraction ? getFirstResponse(command) : getNextResponse(command);
        Log.i(TAG, "--> " + ByteUtils.toHexString(response));
        return response;
    }

    private byte[] getFirstResponse(byte[] command) {
        Log.reset(TAG, "<-- " + ByteUtils.toHexString(command));
        notifications.createNotificationChannel(this);
        notifications.show("<--" + ByteUtils.toHexString(command));

        firstInteraction = false;
        try {
            emulation = getEmulation();
            return Iso7816.RESPONSE_SUCCESS;
        } catch (InvalidParameterException e) {
            return Iso7816.RESPONSE_INTERNAL_ERROR;
        }
    }

    private Emulation getEmulation() throws InvalidParameterException {
        ProfileManager.CardProfile activeProfile = ProfileManager.getActiveProfile(this);
        if (activeProfile == null) {
            Log.i(TAG, "No active profile set. Emulation cannot start.");
            throw new InvalidParameterException("No active profile set");
        }

        Log.i(TAG, "Loading HCE for Profile '" + activeProfile.name + "' with Hex: " + activeProfile.payloadHex);

        Application libraryApplication = new DynamicLibraryApplication(activeProfile.payloadHex, activeProfile.aesKeyHex);
        Application[] applications = new Application[] {libraryApplication};
        SecureElement seWrapper = new SecureElement(applications);
        return new Emulation(seWrapper);
    }

    private byte[] getNextResponse(byte[] command) {
        Log.i(TAG, "<-- " + ByteUtils.toHexString(command));
        notifications.show("<--" + ByteUtils.toHexString(command));
        if (emulation == null) {
            Log.i(TAG, "getNextResponse: emulation object was null. Attempting lazy recovery initialization.");
            try {
                emulation = getEmulation();
            } catch (InvalidParameterException e) {
                Log.i(TAG, "getNextResponse recovery failed: " + e.getMessage());
                return Iso7816.RESPONSE_INTERNAL_ERROR;
            }
        }
        return emulation.getResponse(command);
    }

    @Override
    public void onDeactivated(int reason) {
        Log.i(TAG, "onDeactivated(). Reason: " + reason);
        firstInteraction = true;
        emulation = null;
        paymentStateRepository.notifyDeactivated();
    }

}
