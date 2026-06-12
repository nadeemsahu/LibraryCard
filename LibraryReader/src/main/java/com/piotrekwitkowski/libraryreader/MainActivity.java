package com.piotrekwitkowski.libraryreader;

import android.content.Intent;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.nfc.desfire.InvalidParameterException;

public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {
    private static final String TAG = "MainActivity";
    private LibraryReader libraryReader;
    private NfcAdapter nfcAdapter;
    private TextView nfcStatusText;
    private TextView readerStatusText;
    private Button btnEnableNfc;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        nfcStatusText = findViewById(R.id.nfcStatusText);
        readerStatusText = findViewById(R.id.readerStatusText);
        btnEnableNfc = findViewById(R.id.btnEnableNfc);
        TextView logTextView = findViewById(R.id.logTextView);
        TextView btnClearLogs = findViewById(R.id.btnClearLogs);

        Log.setLogTextView(logTextView);
        Log.reset(TAG, "onCreate()");

        // Construct with payment-rejected listener so the gate can surface feedback
        libraryReader = new LibraryReader(this, () -> {
            runOnUiThread(() -> {
                readerStatusText.setText("Payment card detected — cannot import.");
                readerStatusText.setTextColor(Color.parseColor("#FB8C00"));
                Log.i(TAG, "Payment card safely rejected.");
            });
        });

        nfcAdapter = NfcAdapter.getDefaultAdapter(this);

        btnEnableNfc.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            startActivity(intent);
        });

        btnClearLogs.setOnClickListener(v -> {
            Log.reset(TAG, "Logs cleared.");
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "onResume()");
        checkNfcStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.i(TAG, "onPause()");
        if (nfcAdapter != null) {
            nfcAdapter.disableReaderMode(this);
            Log.i(TAG, "NFC adapter disabled reader mode.");
        }
    }

    private void checkNfcStatus() {
        if (nfcAdapter == null) {
            nfcStatusText.setText("NFC Not Supported");
            nfcStatusText.setTextColor(Color.parseColor("#E53935"));
            readerStatusText.setText("Hardware Unavailable");
            readerStatusText.setTextColor(Color.parseColor("#E53935"));
            btnEnableNfc.setVisibility(View.GONE);
            Log.i(TAG, "NFC Hardware is not available on this device.");
        } else if (!nfcAdapter.isEnabled()) {
            nfcStatusText.setText("NFC Disabled");
            nfcStatusText.setTextColor(Color.parseColor("#FB8C00"));
            readerStatusText.setText("NFC Disabled");
            readerStatusText.setTextColor(Color.parseColor("#FB8C00"));
            btnEnableNfc.setVisibility(View.VISIBLE);
            Log.i(TAG, "NFC is disabled. Please enable NFC in settings.");
        } else {
            nfcStatusText.setText("NFC Ready & Active");
            nfcStatusText.setTextColor(Color.parseColor("#4CAF50"));
            readerStatusText.setText("Ready. Hold card near phone back.");
            readerStatusText.setTextColor(Color.parseColor("#00796B"));
            btnEnableNfc.setVisibility(View.GONE);

            // FLAG_READER_NFC_B added so Type-B banking cards are also detected and blocked
            nfcAdapter.enableReaderMode(this, this,
                NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_NFC_B
                    | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null);
            Log.i(TAG, "NFC adapter enabled. Waiting for a card...");
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Log.reset(TAG, "onTagDiscovered()");
        runOnUiThread(() -> {
            readerStatusText.setText("Card detected! Processing...");
            readerStatusText.setTextColor(Color.parseColor("#1565C0"));
        });
        try {
            libraryReader.processTag(tag);
        } catch (InvalidParameterException e) {
            Log.i(TAG, e.getMessage());
            runOnUiThread(() -> {
                readerStatusText.setText("Error reading card.");
                readerStatusText.setTextColor(Color.parseColor("#E53935"));
            });
        }
    }
}
