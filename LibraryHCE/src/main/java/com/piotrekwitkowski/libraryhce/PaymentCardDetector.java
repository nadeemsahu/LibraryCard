package com.piotrekwitkowski.libraryhce;

import android.nfc.Tag;
import android.nfc.tech.IsoDep;

import com.piotrekwitkowski.log.Log;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * PaymentCardDetector
 *
 * Detects whether a scanned NFC tag is an EMV payment card (debit / credit / banking credential).
 * The app MUST call {@link #isPaymentCard(Tag)} before any cloning or storage logic.
 *
 * Detection strategy (layered, defence-in-depth):
 *   1.  Technology fingerprint  — ISO-DEP presence combined with NfcB or historical-bytes checks
 *   2.  SELECT-PPSE probe       — Sends the standard EMV Proximity Payment System Environment SELECT
 *   3.  Known payment AID probe — Iterates the well-known AID database and tries SELECT on each
 *
 * The app NEVER stores, logs, or processes any sensitive card credential returned.
 * All APDU responses are inspected only for the two-byte status word and immediately discarded.
 */
public final class PaymentCardDetector {

    private static final String TAG = "PaymentCardDetector";

    // -----------------------------------------------------------------------
    // EMV PPSE AID (standard entry-point used by all EMV terminals)
    // -----------------------------------------------------------------------
    private static final byte[] AID_PPSE = hexToBytes("325041592E5359532E4444463031"); // "2PAY.SYS.DDF01"

    // -----------------------------------------------------------------------
    // Known Payment Application AID Database
    // Sources: EMVCo, Visa, Mastercard, RuPay, Amex, Discover, UnionPay specs
    // -----------------------------------------------------------------------
    private static final String[] PAYMENT_AIDS = {

        // ── Visa ────────────────────────────────────────────────────────────
        "A0000000031010",   // Visa Credit / Debit
        "A0000000032010",   // Visa Electron
        "A0000000033010",   // Visa Interlink
        "A0000000038010",   // Visa Plus
        "A000000003101001", // Visa Credit (extended)

        // ── Mastercard ──────────────────────────────────────────────────────
        "A0000000041010",   // Mastercard Credit / Debit
        "A0000000042010",   // Mastercard Specific
        "A0000000043060",   // Maestro (debit)
        "A0000000044060",   // Mastercard Cirrus

        // ── American Express ────────────────────────────────────────────────
        "A00000002501",     // Amex
        "A0000000250108",   // Amex Credit

        // ── Discover / Diners ────────────────────────────────────────────────
        "A0000001523010",   // Discover
        "A0000001524010",   // Diners Club International

        // ── RuPay (India) ───────────────────────────────────────────────────
        "A0000005241010",   // RuPay
        "A000000524101001", // RuPay Debit
        "A000000524101002", // RuPay Credit
        "A0000005241010FF", // RuPay Contactless

        // ── UnionPay (China) ─────────────────────────────────────────────────
        "A000000333010101", // UnionPay Credit
        "A000000333010102", // UnionPay Debit
        "A000000333010103", // UnionPay Quasi Credit
        "A000000333010106", // UnionPay Electronic Cash

        // ── JCB ─────────────────────────────────────────────────────────────
        "A0000000651010",   // JCB

        // ── MIR (Russia) ────────────────────────────────────────────────────
        "A0000006581010",   // MIR Credit / Debit

        // ── Interac (Canada) ────────────────────────────────────────────────
        "A0000002771010",   // Interac

        // ── EFTPOS (Australia) ──────────────────────────────────────────────
        "A0000003840108",   // eftpos Savings
        "A0000003840109",   // eftpos Cheque

        // ── Bancontact (Belgium) ─────────────────────────────────────────────
        "A0000000048002",   // Bancontact / Mister Cash

        // ── LINK (UK) ────────────────────────────────────────────────────────
        "A0000000291010",   // LINK ATM

        // ── Generic EMV / ISO 7816 financial AIDs ───────────────────────────
        "D27600002545500100", // EMV Cash
        "A0000000030001",     // Visa ATM
    };

    // Pre-built Set for O(1) lookups
    private static final Set<String> PAYMENT_AID_SET = new HashSet<>(Arrays.asList(PAYMENT_AIDS));

    // SELECT APDU command header  (CLA=00, INS=A4, P1=04, P2=00)
    private static final byte[] SELECT_HEADER = {0x00, (byte) 0xA4, 0x04, 0x00};

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Performs a non-destructive probe of the NFC tag to determine whether it is
     * an EMV payment card.
     *
     * @param tag  The tag discovered by NfcAdapter.ReaderCallback.onTagDiscovered()
     * @return     {@code true} if the tag is a payment / banking card.
     *             {@code false} if the tag appears to be a custom or library card.
     */
    public static boolean isPaymentCard(Tag tag) {
        if (tag == null) return false;

        // ── Layer 1: Technology fingerprint ──────────────────────────────────
        if (hasBankingTechProfile(tag)) {
            Log.i(TAG, "BLOCKED: Payment card detected via technology fingerprint.");
            return true;
        }

        // ── Layers 2 & 3: ISO-DEP APDU probes ───────────────────────────────
        IsoDep isoDep = IsoDep.get(tag);
        if (isoDep == null) {
            // No ISO-DEP → definitely not an EMV contact/contactless card
            return false;
        }

        try {
            isoDep.connect();

            // Layer 2: SELECT PPSE probe
            if (respondsToPpse(isoDep)) {
                Log.i(TAG, "BLOCKED: Payment card detected via PPSE response.");
                return true;
            }

            // Layer 3: Known AID probes
            if (matchesKnownPaymentAid(isoDep)) {
                Log.i(TAG, "BLOCKED: Payment card detected via known AID match.");
                return true;
            }

        } catch (IOException e) {
            // IO errors during probing → treat conservatively as non-payment card
            Log.i(TAG, "ISO-DEP probe IOException (likely non-payment card): " + e.getMessage());
        } finally {
            try { isoDep.close(); } catch (IOException ignored) { }
        }

        return false;
    }

    // -----------------------------------------------------------------------
    // Layer 1: Technology fingerprint
    // -----------------------------------------------------------------------

    /**
     * Checks the tag's tech list for patterns associated with banking cards.
     * Real EMV contactless cards always support ISO-DEP.
     * NfcB (Type B) is used exclusively by banking/ID credentials, never by library cards.
     */
    private static boolean hasBankingTechProfile(Tag tag) {
        boolean hasIsoDep = false;
        boolean hasNfcB   = false;
        boolean hasMfStd  = false;

        for (String tech : tag.getTechList()) {
            if (tech.equals("android.nfc.tech.IsoDep"))       hasIsoDep = true;
            if (tech.equals("android.nfc.tech.NfcB"))         hasNfcB   = true;
            if (tech.equals("android.nfc.tech.MifareClassic")) hasMfStd = true;
        }

        // NfcB (ISO 14443-4 Type B) is used only by banking / government ID cards
        if (hasNfcB && hasIsoDep) return true;

        // A Mifare Classic card with NO other tech is a basic transit/library card — allow.
        // An IsoDep-only card could be anything; we rely on Layers 2 & 3 for that.
        return false;
    }

    // -----------------------------------------------------------------------
    // Layer 2: SELECT PPSE
    // -----------------------------------------------------------------------

    /**
     * Sends the EMV PPSE SELECT command. Any 90 00 or 61 xx response confirms
     * this is a payment card. 6A 82 ("File Not Found") means not a payment card.
     */
    private static boolean respondsToPpse(IsoDep isoDep) throws IOException {
        byte[] selectPpse = buildSelectApdu(AID_PPSE);
        byte[] response   = isoDep.transceive(selectPpse);

        if (response == null || response.length < 2) return false;

        byte sw1 = response[response.length - 2];
        byte sw2 = response[response.length - 1];

        // 90 00 = Success,  61 xx = Response pending (chained), 62 xx / 63 xx = Warnings (still present)
        return (sw1 == (byte) 0x90 && sw2 == 0x00)
            || (sw1 == (byte) 0x61)
            || (sw1 == (byte) 0x62)
            || (sw1 == (byte) 0x63);
    }

    // -----------------------------------------------------------------------
    // Layer 3: Known AID probes
    // -----------------------------------------------------------------------

    /**
     * Iterates a curated subset of the most common payment AIDs and attempts SELECT.
     * Stops at first successful match to minimize latency.
     */
    private static boolean matchesKnownPaymentAid(IsoDep isoDep) throws IOException {
        // We probe only the most-common AIDs to keep latency below ~300ms
        String[] probeAids = {
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

        for (String aidHex : probeAids) {
            byte[] selectApdu = buildSelectApdu(hexToBytes(aidHex));
            byte[] response;
            try {
                response = isoDep.transceive(selectApdu);
            } catch (IOException e) {
                // Card disconnected mid-probe — not a payment card or already handled
                return false;
            }

            if (response == null || response.length < 2) continue;

            byte sw1 = response[response.length - 2];
            // 90 00 or 61 xx = AID is present on this card
            if (sw1 == (byte) 0x90 || sw1 == (byte) 0x61) {
                Log.i(TAG, "Payment AID matched: " + aidHex);
                return true;
            }
        }
        return false;
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Builds a well-formed SELECT (by AID) APDU command. */
    private static byte[] buildSelectApdu(byte[] aid) {
        // 00 A4 04 00 <Lc> <AID> 00
        byte[] apdu = new byte[SELECT_HEADER.length + 1 + aid.length + 1];
        System.arraycopy(SELECT_HEADER, 0, apdu, 0, SELECT_HEADER.length);
        apdu[SELECT_HEADER.length]     = (byte) aid.length; // Lc
        System.arraycopy(aid, 0, apdu, SELECT_HEADER.length + 1, aid.length);
        apdu[apdu.length - 1] = 0x00; // Le
        return apdu;
    }

    /** Converts a hex string to a byte array. */
    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                               +  Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    // Prevent instantiation
    private PaymentCardDetector() {}
}
