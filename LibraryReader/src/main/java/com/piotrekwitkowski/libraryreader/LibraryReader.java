package com.piotrekwitkowski.libraryreader;

import android.content.Context;
import android.nfc.Tag;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.desfire.AID;
import com.piotrekwitkowski.nfc.desfire.InvalidParameterException;
import com.piotrekwitkowski.nfc.desfire.AESKey;

class LibraryReader {
    private static final String TAG = "LibraryReader";
    private final Context context;
    private final OnPaymentRejectedListener paymentRejectedListener;

    interface OnPaymentRejectedListener {
        void onPaymentCardRejected();
    }

    LibraryReader(Context ctx) {
        this.context = ctx;
        this.paymentRejectedListener = null;
    }

    LibraryReader(Context ctx, OnPaymentRejectedListener listener) {
        this.context = ctx;
        this.paymentRejectedListener = listener;
    }

    void processTag(Tag tag) throws InvalidParameterException {
        Log.i(TAG, "processTag()");

        // ── PAYMENT CARD SECURITY GATE ──────────────────────────────────────
        if (PaymentCardDetector.isPaymentCard(tag)) {
            Log.i(TAG, "SECURITY GATE: Payment card detected. Blocking processing.");
            if (paymentRejectedListener != null) {
                paymentRejectedListener.onPaymentCardRejected();
            }
            return;
        }
        // ───────────────────────────────────────────────────────────────────

        final AID LIBRARY_AID = new AID("015548");
        final AESKey LIBRARY_KEY = new AESKey("00000000000000000000000000000000");
        final int LIBRARY_KEY_NUMBER = 0;
        final int FILE_NUMBER = 0;
        final int FILE_OFFSET = 10;
        final int FILE_LENGTH = 12;
        final IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            Log.i(TAG, "Tag does not support ISO-DEP");
            if (paymentRejectedListener != null) {
                // Not a payment card, but unsupported. We can reuse this or ignore.
            }
            throw new InvalidParameterException("Tag does not support ISO-DEP");
        }

        try {
            StudentId studentId = StudentId.getStudentId(this.context, isoDep);
            studentId.selectApplication(LIBRARY_AID);
            studentId.authenticateAES(LIBRARY_KEY, LIBRARY_KEY_NUMBER);
            byte[] libraryId = studentId.readData(FILE_NUMBER, FILE_OFFSET, FILE_LENGTH);
            Log.i(TAG, "libraryId: " + new String(libraryId));

            studentId.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
