package com.piotrekwitkowski.libraryhce.reader;

import android.content.Context;
import android.content.SharedPreferences;
import android.nfc.Tag;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.ByteUtils;
import com.piotrekwitkowski.nfc.desfire.AID;
import com.piotrekwitkowski.nfc.desfire.InvalidParameterException;
import com.piotrekwitkowski.nfc.desfire.AESKey;
import com.piotrekwitkowski.libraryhce.PaymentCardDetector;

public class LibraryReader {
    private static final String TAG = "LibraryReader";
    private final Context context;
    private final OnCardClonedListener listener;

    public interface OnCardClonedListener {
        void onCardCloned(String libraryId, String hexDump);
        void onCloneFailed(String error);
        /** Called when the scanned card is identified as an EMV/payment card. */
        void onPaymentCardRejected();
    }

    public LibraryReader(Context ctx, OnCardClonedListener listener) {
        this.context = ctx;
        this.listener = listener;
    }

    public void processTag(Tag tag) throws InvalidParameterException {
        Log.i(TAG, "processTag()");

        if (tag == null) {
            Log.i(TAG, "processTag() - tag is null");
            if (listener != null) {
                listener.onCloneFailed("No NFC tag detected.");
            }
            return;
        }

        // Print detected technologies
        String[] techList = tag.getTechList();
        Log.i(TAG, "Detected Tag Techs: " + java.util.Arrays.toString(techList));

        // ── PAYMENT CARD SECURITY GATE ──────────────────────────────────────
        // This check MUST run before any ISO-DEP connect, APDU exchange,
        // SharedPreferences write, or listener callback. If this tag is an
        // EMV/banking card the app MUST NOT touch it further.
        if (PaymentCardDetector.isPaymentCard(tag)) {
            Log.i(TAG, "SECURITY GATE: Payment card detected. Blocking all further processing.");
            if (listener != null) {
                listener.onPaymentCardRejected();
            }
            return;
        }
        // ───────────────────────────────────────────────────────────────────

        final IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            Log.i(TAG, "Tag does not support ISO-DEP (ISO 14443-4). Falling back to Tag UID cloning.");
            if (!tryUidFallback(tag)) {
                if (listener != null) {
                    listener.onCloneFailed("Unsupported card type and failed to read Tag UID.");
                }
            }
            return;
        }

        final AID LIBRARY_AID = new AID("015548");
        final AESKey LIBRARY_KEY = new AESKey("00000000000000000000000000000000");
        final int LIBRARY_KEY_NUMBER = 0;
        final int FILE_NUMBER = 0;
        final int FILE_OFFSET = 10;
        final int FILE_LENGTH = 12;

        try {
            StudentId studentId = StudentId.getStudentId(this.context, isoDep);
            studentId.selectApplication(LIBRARY_AID);
            studentId.authenticateAES(LIBRARY_KEY, LIBRARY_KEY_NUMBER);
            
            // Read the full 32 bytes to clone the entire file structure
            byte[] fileData = studentId.readData(FILE_NUMBER, 0, 32);
            // Read the library ID (starts at offset 10 with length 12)
            byte[] libraryIdBytes = studentId.readData(FILE_NUMBER, FILE_OFFSET, FILE_LENGTH);
            
            String libraryId = new String(libraryIdBytes).trim();
            String fullFileHex = ByteUtils.toHexString(fileData);
            
            Log.i(TAG, "libraryId: " + libraryId);
            Log.i(TAG, "file0 hex: " + fullFileHex);

            // Save the cloned data to SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences("LibraryNFC", Context.MODE_PRIVATE);
            prefs.edit()
                .putString("cloned_card_id", libraryId)
                .putString("cloned_card_data", fullFileHex)
                .putString("cloned_card_key", "00000000000000000000000000000000")
                .apply();

            if (listener != null) {
                listener.onCardCloned(libraryId, fullFileHex);
            }

            studentId.close();
        } catch (Exception e) {
            e.printStackTrace();
            String errorMsg = e.getMessage();
            if (errorMsg == null) {
                errorMsg = e.getClass().getSimpleName();
            }
            Log.i(TAG, "DESFire read failed: " + errorMsg + ". Falling back to Tag UID cloning.");
            
            if (!tryUidFallback(tag)) {
                if (listener != null) {
                    listener.onCloneFailed(errorMsg);
                }
            }
        }
    }

    private boolean tryUidFallback(Tag tag) {
        byte[] uid = tag.getId();
        if (uid != null && uid.length > 0) {
            String libraryId = formatUidToLibraryId(uid);
            
            // Build the 32-byte payloadHex
            byte[] idBytes = new byte[12];
            byte[] sourceBytes = libraryId.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(sourceBytes, 0, idBytes, 0, Math.min(sourceBytes.length, idBytes.length));
            String idHex = ByteUtils.toHexString(idBytes);
            String payloadHex = "00353835363836000000" + idHex + "00000000000000000000";

            Log.i(TAG, "Successfully extracted fallback UID Library ID: " + libraryId);
            Log.i(TAG, "Fallback payloadHex: " + payloadHex);

            // Save fallback to SharedPreferences (so HCEService works instantly)
            SharedPreferences prefs = context.getSharedPreferences("LibraryNFC", Context.MODE_PRIVATE);
            prefs.edit()
                .putString("cloned_card_id", libraryId)
                .putString("cloned_card_data", payloadHex)
                .putString("cloned_card_key", "00000000000000000000000000000000")
                .apply();

            if (listener != null) {
                listener.onCardCloned(libraryId, payloadHex);
            }
            return true;
        }
        return false;
    }

    private String formatUidToLibraryId(byte[] uid) {
        String hex = ByteUtils.toHexString(uid).toUpperCase();
        if (hex.length() == 8) {
            // 4-byte UID: prepend "UID0" to make it 12 chars
            return "UID0" + hex;
        } else if (hex.length() >= 12) {
            // 7-byte or 10-byte UID: take the first 12 characters
            return hex.substring(0, 12);
        } else {
            // fallback: pad with zeros to 12 chars
            StringBuilder sb = new StringBuilder(hex);
            while (sb.length() < 12) {
                sb.append("0");
            }
            return sb.toString();
        }
    }
}
