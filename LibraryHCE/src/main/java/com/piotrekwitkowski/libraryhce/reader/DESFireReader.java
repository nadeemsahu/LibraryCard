package com.piotrekwitkowski.libraryhce.reader;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.ByteUtils;
import com.piotrekwitkowski.nfc.desfire.Commands;
import com.piotrekwitkowski.nfc.desfire.ResponseCodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

class DESFireReader {
    private static final String TAG = "DESFireReader";

    static void selectApplication(IsoDep isoDep, byte[] aid) throws IOException, DESFireReaderException {
        Log.i(TAG, "selectApplication()");

        Response response = isoDep.transceive(Commands.SELECT_APPLICATION, aid);
        if (response.getResponseCode() != ResponseCodes.SUCCESS) {
            throw new DESFireReaderException("selectApplication() failed. Response status: " + response.getResponseCode());
        }
    }

    static byte[] authenticateAES(IsoDep isoDep, byte[] aesKey, byte keyNumber) throws IOException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, BadPaddingException, IllegalBlockSizeException, DESFireReaderException {
        Log.i(TAG, "authenticateAES()");

        Response response = isoDep.transceive(Commands.AUTHENTICATE_AES, keyNumber);
        byte[] challenge = response.getData();
        Log.i(TAG, "challenge: " + ByteUtils.toHexString(challenge));

        Cipher cipher = Cipher.getInstance("AES/CBC/NoPadding");
        Key aes = new SecretKeySpec(aesKey, "AES");
        IvParameterSpec ivParam = new IvParameterSpec(new byte[16]);
        cipher.init(Cipher.DECRYPT_MODE, aes, ivParam);
        byte[] B = cipher.doFinal(challenge);
        Log.i(TAG, "cipheredData: " + ByteUtils.toHexString(B));

        byte[] A = ByteUtils.getRandomBytes(16);
        byte[] rotatedB = ByteUtils.rotateOneLeft(B);
        byte[] C = ByteUtils.concatenate(A, rotatedB);

        ivParam = new IvParameterSpec(challenge);
        cipher.init(Cipher.ENCRYPT_MODE, aes, ivParam);
        byte[] D = cipher.doFinal(C);
        byte[] command = ByteUtils.concatenate(Commands.ADDITIONAL_FRAME, D);
        response = isoDep.transceive(command);
        challenge = response.getData();

        if (response.getResponseCode() != ResponseCodes.SUCCESS) {
            throw new DESFireReaderException("authenticateAES failed");
        }

        byte[] last16Bytes = ByteUtils.last16Bytes(command);
        ivParam = new IvParameterSpec(last16Bytes);
        cipher.init(Cipher.DECRYPT_MODE, aes, ivParam);
        byte[] E = cipher.doFinal(challenge);

        if (!Arrays.equals(ByteUtils.rotateOneLeft(A), E)) {
            throw new DESFireReaderException("authenticateAES failed");
        }

        ByteArrayOutputStream sessionKeyOutputStream = new ByteArrayOutputStream();
        sessionKeyOutputStream.write(A, 0, 4);
        sessionKeyOutputStream.write(B, 0, 4);
        sessionKeyOutputStream.write(A, 12, 4);
        sessionKeyOutputStream.write(B, 12, 4);
        return sessionKeyOutputStream.toByteArray();
    }

    static byte[] readData(IsoDep isoDep, int fileNumber, int offset, int length) throws IOException, DESFireReaderException {
        Log.i(TAG, "readData()");

        byte[] offsetBytes = ByteUtils.first3Bytes(offset);
        byte[] lengthBytes = ByteUtils.first3Bytes(length);

        byte[] params = ByteUtils.concatenate(offsetBytes, lengthBytes);
        byte[] commandData = ByteUtils.concatenate((byte) fileNumber, params);

        Response response = isoDep.transceive(Commands.READ_DATA, commandData);
        if (response.getResponseCode() == ResponseCodes.SUCCESS) {
            return response.getData();
        } else if (response.getResponseCode() == ResponseCodes.BOUNDARY_ERROR) {
            throw new DESFireReaderException("Boundary error!");
        } else {
            throw new DESFireReaderException("readData failed. Response status: " + response.getResponseCode());
        }
    }
}
