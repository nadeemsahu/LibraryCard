package com.piotrekwitkowski.libraryhce.application;

import com.piotrekwitkowski.nfc.desfire.InvalidParameterException;
import com.piotrekwitkowski.nfc.desfire.AESKey;
import com.piotrekwitkowski.nfc.se.Application;

public class DynamicLibraryApplication extends Application {
    public DynamicLibraryApplication(String cardHex, String aesKeyHex) throws InvalidParameterException {
        super(new LibraryAID(), new AESKey(aesKeyHex), new DynamicLibraryFile(cardHex));
    }
}
