package com.piotrekwitkowski.libraryhce.reader;

import android.content.Context;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.ByteUtils;
import com.piotrekwitkowski.nfc.Iso7816;
import com.piotrekwitkowski.nfc.desfire.AID;
import com.piotrekwitkowski.nfc.desfire.AESKey;

import java.io.IOException;
import java.util.Arrays;

@SuppressWarnings("SameParameterValue")
class StudentId {
    private static final String TAG = "StudentId";
    private final IsoDep isoDep;
    enum idForm {PHYSICAL, HCE}

    private StudentId(IsoDep isoDep) {
        this.isoDep = isoDep;
    }

    static StudentId getStudentId(Context context, IsoDep isoDep) throws Exception {
        Log.i(TAG, "getStudentId()");
        isoDep.connect();

        idForm idForm = getIdForm(isoDep);
        Log.i(TAG, "ID form: "+ idForm);

        if (idForm == idForm.PHYSICAL) {
            return new StudentId(isoDep);
        } else if (idForm == idForm.HCE) {
            Response response = HCE.selectAndroidApp(context, isoDep);
            if (Arrays.equals(response.getBytes(), Iso7816.RESPONSE_SUCCESS)) {
                return new StudentId(isoDep);
            } else {
                throw new StudentIdException("HCE Mobile Application select was unsuccessful");
            }
        } else {
            throw new StudentIdException("ID form not supported");
        }
    }

    void close() throws IOException {
        isoDep.close();
    }

    private static idForm getIdForm(IsoDep isoDep) throws StudentIdException {
        Log.i(TAG, "getIdForm()");

        byte[] historicalBytes = isoDep.getHistoricalBytes();
        Log.i(TAG, "historicalBytes: " + (historicalBytes == null ? "null" : ByteUtils.toHexString(historicalBytes)));

        if (historicalBytes != null && Arrays.equals(historicalBytes, new byte[]{(byte) 0x80})) {
            return idForm.PHYSICAL;
        } else if (historicalBytes == null || historicalBytes.length == 0 || Arrays.equals(historicalBytes, new byte[]{})) {
            return idForm.HCE;
        } else {
            // Default to physical if unknown
            return idForm.PHYSICAL;
        }
    }

    void selectApplication(AID aid) throws IOException, DESFireReaderException {
        byte[] applicationAid = aid.getBytes();
        DESFireReader.selectApplication(this.isoDep, applicationAid);
        Log.i(TAG, "Application selected: " + ByteUtils.toHexString(applicationAid));
    }

    void authenticateAES(AESKey key, int keyNumber) throws Exception {
        byte[] sessionKey = DESFireReader.authenticateAES(this.isoDep, key.getKey(), (byte) keyNumber);
        Log.i(TAG, "Session key: " + ByteUtils.toHexString(sessionKey));
    }

    byte[] readData(int fileNumber, int offset, int length) throws IOException, DESFireReaderException {
        byte[] response = DESFireReader.readData(this.isoDep, fileNumber, offset, length);
        byte[] data = ByteUtils.trimEnd(response, 8);
        Log.i(TAG, "Data: " + ByteUtils.toHexString(data));
        return data;
    }

}
