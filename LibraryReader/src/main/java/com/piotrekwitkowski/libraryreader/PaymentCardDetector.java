package com.piotrekwitkowski.libraryreader;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.piotrekwitkowski.log.Log;

import java.io.IOException;

/**
 * PaymentCardDetector (LibraryReader module mirror)
 *
 * Mirrors the HCE module detector — provides identical EMV detection
 * for the standalone LibraryReader app.
 *
 * The reader MUST call {@link #isPaymentCard(Tag)} at the top of every
 * tag-discovered flow before any processing occurs.
 */
public final class PaymentCardDetector {

    private static final String TAG = "PaymentCardDetector";

    private static final byte[] AID_PPSE = hexToBytes("325041592E5359532E4444463031");

    private static final byte[] SELECT_HEADER = {0x00, (byte) 0xA4, 0x04, 0x00};

    private static final String[] PROBE_AIDS = {
        "A0000000031010", // Visa
        "A0000000041010", // Mastercard
        "A0000000043060", // Maestro
        "A00000002501",   // Amex
        "A0000001523010", // Discover
        "A0000005241010", // RuPay
        "A000000333010101", // UnionPay
        "A0000000651010", // JCB
        "A0000006581010", // MIR
    };

    public static boolean isPaymentCard(Tag tag) {
        if (tag == null) return false;

        if (hasBankingTechProfile(tag)) {
            Log.i(TAG, "BLOCKED: Payment card detected via technology fingerprint.");
            return true;
        }

        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) return false;

        try {
            isoDep.connect();
            if (respondsToPpse(isoDep)) {
                Log.i(TAG, "BLOCKED: Payment card detected via PPSE.");
                return true;
            }
            if (matchesKnownPaymentAid(isoDep)) {
                Log.i(TAG, "BLOCKED: Payment card detected via known AID.");
                return true;
            }
        } catch (IOException e) {
            Log.i(TAG, "ISO-DEP probe IOException: " + e.getMessage());
        } finally {
            try { isoDep.close(); } catch (IOException ignored) { }
        }

        return false;
    }

    private static boolean hasBankingTechProfile(Tag tag) {
        boolean hasIsoDep = false;
        boolean hasNfcB   = false;
        for (String tech : tag.getTechList()) {
            if (tech.equals("android.nfc.tech.IsoDep")) hasIsoDep = true;
            if (tech.equals("android.nfc.tech.NfcB"))   hasNfcB   = true;
        }
        return hasNfcB && hasIsoDep;
    }

    private static boolean respondsToPpse(IsoDep isoDep) throws IOException {
        byte[] response = isoDep.transceive(buildSelectApdu(AID_PPSE));
        if (response == null || response.length < 2) return false;
        byte sw1 = response[response.length - 2];
        return sw1 == (byte) 0x90 || sw1 == (byte) 0x61
            || sw1 == (byte) 0x62 || sw1 == (byte) 0x63;
    }

    private static boolean matchesKnownPaymentAid(IsoDep isoDep) throws IOException {
        for (String aidHex : PROBE_AIDS) {
            byte[] response;
            try {
                response = isoDep.transceive(buildSelectApdu(hexToBytes(aidHex)));
            } catch (IOException e) {
                return false;
            }
            if (response == null || response.length < 2) continue;
            byte sw1 = response[response.length - 2];
            if (sw1 == (byte) 0x90 || sw1 == (byte) 0x61) {
                Log.i(TAG, "Payment AID matched: " + aidHex);
                return true;
            }
        }
        return false;
    }

    private static byte[] buildSelectApdu(byte[] aid) {
        byte[] apdu = new byte[SELECT_HEADER.length + 1 + aid.length + 1];
        System.arraycopy(SELECT_HEADER, 0, apdu, 0, SELECT_HEADER.length);
        apdu[SELECT_HEADER.length] = (byte) aid.length;
        System.arraycopy(aid, 0, apdu, SELECT_HEADER.length + 1, aid.length);
        apdu[apdu.length - 1] = 0x00;
        return apdu;
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                               +  Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    private PaymentCardDetector() {}
}
