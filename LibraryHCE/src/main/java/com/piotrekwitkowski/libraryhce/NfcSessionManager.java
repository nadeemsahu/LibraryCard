package com.piotrekwitkowski.libraryhce;

import android.app.Activity;
import android.nfc.NfcAdapter;
import android.util.Log;

public class NfcSessionManager {
    private static final String TAG = "NfcSessionManager";
    private final Activity activity;
    private NfcAdapter nfcAdapter;

    public NfcSessionManager(Activity activity) {
        this.activity = activity;
        this.nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
    }

    public boolean isNfcAvailable() {
        return nfcAdapter != null;
    }

    public boolean isNfcEnabled() {
        try {
            return nfcAdapter != null && nfcAdapter.isEnabled();
        } catch (Throwable t) {
            return false;
        }
    }

    public void enableReaderMode(NfcAdapter.ReaderCallback callback) {
        if (nfcAdapter != null && isNfcEnabled()) {
            try {
                nfcAdapter.enableReaderMode(activity, callback,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                        | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
                Log.i(TAG, "Reader mode enabled.");
            } catch (Throwable t) {
                Log.e(TAG, "Failed to enable reader mode: " + t.getMessage());
            }
        }
    }

    public void disableReaderMode() {
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(activity);
                Log.i(TAG, "Reader mode disabled.");
            } catch (Throwable t) {
                Log.e(TAG, "Failed to disable reader mode: " + t.getMessage());
            }
        }
    }
}
