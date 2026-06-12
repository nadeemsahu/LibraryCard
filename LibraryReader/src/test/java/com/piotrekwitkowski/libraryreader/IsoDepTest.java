package com.piotrekwitkowski.libraryreader;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;

import static org.junit.Assert.assertArrayEquals;

public class IsoDepTest {

    private android.nfc.tech.IsoDep mockRawIsoDep;

    @Before
    public void setUp() {
        mockRawIsoDep = Mockito.mock(android.nfc.tech.IsoDep.class);
    }

    @Test
    public void testTransceiveReturnsExpectedData() throws Exception {
        byte[] command = new byte[]{0x00, (byte) 0xA4, 0x04, 0x00, 0x07, (byte) 0xF0, 0x39, 0x41, 0x48, 0x14, (byte) 0x81, 0x00, 0x00};
        byte[] expectedResponse = new byte[]{(byte) 0x90, 0x00};

        Mockito.when(mockRawIsoDep.transceive(command)).thenReturn(expectedResponse);

        // Access private constructor via reflection
        java.lang.reflect.Constructor<IsoDep> constructor = IsoDep.class.getDeclaredConstructor(android.nfc.tech.IsoDep.class);
        constructor.setAccessible(true);
        IsoDep isoDepWrapper = constructor.newInstance(mockRawIsoDep);

        // Test transceive wrapped response
        com.piotrekwitkowski.libraryreader.Response response = isoDepWrapper.transceive(command);
        assertArrayEquals(expectedResponse, response.getBytes());
    }
}
