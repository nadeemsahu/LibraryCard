package com.piotrekwitkowski.libraryhce;

import androidx.appcompat.app.AppCompatActivity;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.biometric.BiometricManager;
import androidx.core.content.ContextCompat;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.piotrekwitkowski.log.Log;
import com.piotrekwitkowski.libraryhce.domain.Authenticator;
import com.piotrekwitkowski.libraryhce.reader.LibraryReader;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import javax.inject.Inject;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity implements NfcAdapter.ReaderCallback {
    private static final String TAG = "MainActivity";
    private NfcAdapter nfcAdapter;

    // Screens layout references
    private ScrollView layoutHome;
    private ScrollView layoutCards;
    private ScrollView layoutSettings;

    // Bottom Navigation Bar tabs references
    private LinearLayout navHome;
    private LinearLayout navCards;
    private LinearLayout navScan;
    private LinearLayout navSettings;

    // Bottom Navigation Bar assets for color tinting
    private ImageView imgNavHome;
    private TextView txtNavHome;
    private ImageView imgNavCards;
    private TextView txtNavCards;
    private ImageView imgNavScan;
    private TextView txtNavScan;
    private ImageView imgNavSettings;
    private TextView txtNavSettings;

    // Hero Emulation Card Fields
    private TextView tvHeroCardId;
    private TextView tvHeroCardName;
    private TextView tvCardStatusLabel;
    private View viewHcePulseDot;

    // Home status
    private TextView tvHomeNfcStatus;
    private Button btnHomeEnableNfc;
    private LinearLayout layoutCardSwapper;

    // Cards Module fields
    private LinearLayout layoutVerticalCards;
    private LinearLayout layoutEmptyState;

    // Settings fields


    @Inject Authenticator authenticator;
    @Inject NfcSessionManager nfcSessionManager;
    private LibraryReader libraryReader;
    private List<ProfileManager.CardProfile> profilesList;
    private AppViewModel appViewModel;
    private boolean isUidRevealed = false;
    private static final int REQUEST_CODE_CONFIRM_PIN = 2024;

    // Scanning Overlay references
    private RelativeLayout layoutScanOverlay;
    private View pulseRing1;
    private View pulseRing2;
    private View pulseRing3;
    private Button btnCancelScan;

    // Payment Overlay references
    private RelativeLayout layoutPaymentOverlay;
    private View viewPaymentBgGlow;
    private View paymentRing1;
    private View paymentRing2;
    private View paymentRing3;
    private RelativeLayout cardPaymentContainer;
    private TextView tvPaymentStatusLabel;
    private TextView tvPaymentCardId;
    private TextView tvPaymentCardName;
    private TextView tvPaymentSystemCode;
    private View viewPaymentCardShine;
    private TextView tvPaymentInstruction;
    private FrameLayout layoutPaymentUnlockIndicator;
    private ImageView imgPaymentFingerprint;
    private Button btnCancelPayment;
    private RelativeLayout cardActiveHero;

    private boolean isPaymentRingsActive = false;
    private android.os.Handler ringsHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable ringsRunnable;

    // Payment-rejected overlay references
    private RelativeLayout layoutPaymentRejectedOverlay;
    private LinearLayout cardPaymentRejected;
    private View viewPaymentRejectedGlow;
    private Button btnPaymentRejectedDismiss;

    // Unsupported Device Overlay references
    private RelativeLayout layoutUnsupportedOverlay;
    private View viewUnsupportedGlow;
    private View unsupportedRing1;
    private View unsupportedRing2;
    private View unsupportedRing3;
    private FrameLayout layoutUnsupportedCenter;
    private Button btnUnsupportedLearnMore;
    private Button btnUnsupportedRetry;

    // NFC Disabled Overlay references
    private RelativeLayout layoutNfcDisabledOverlay;
    private View viewDisabledGlow;
    private View disabledRing1;
    private View disabledRing2;
    private View disabledRing3;
    private FrameLayout layoutDisabledCenter;
    private Button btnDisabledEnableNfc;
    private Button btnDisabledRetry;

    // Breathing rings state
    private boolean isBreathingRingsActive = false;
    private android.os.Handler breathingHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable breathingRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        appViewModel = new androidx.lifecycle.ViewModelProvider(this).get(AppViewModel.class);

        // Bind screens
        layoutHome = findViewById(R.id.layout_home);
        layoutCards = findViewById(R.id.layout_cards);
        layoutSettings = findViewById(R.id.layout_settings);

        // Bind Bottom Navigation tabs
        navHome = findViewById(R.id.nav_home);
        navCards = findViewById(R.id.nav_cards);
        navScan = findViewById(R.id.nav_scan);
        navSettings = findViewById(R.id.nav_settings);

        // Bind Navigation icons & text
        imgNavHome = findViewById(R.id.imgNavHome);
        txtNavHome = findViewById(R.id.txtNavHome);
        imgNavCards = findViewById(R.id.imgNavCards);
        txtNavCards = findViewById(R.id.txtNavCards);
        imgNavScan = findViewById(R.id.imgNavScan);
        txtNavScan = findViewById(R.id.txtNavScan);
        imgNavSettings = findViewById(R.id.imgNavSettings);
        txtNavSettings = findViewById(R.id.txtNavSettings);

        // Bind Hero Card elements
        tvHeroCardId = findViewById(R.id.tvHeroCardId);
        tvHeroCardName = findViewById(R.id.tvHeroCardName);
        tvCardStatusLabel = findViewById(R.id.tvCardStatusLabel);
        viewHcePulseDot = findViewById(R.id.viewHcePulseDot);

        // Bind Home elements
        tvHomeNfcStatus = findViewById(R.id.tvHomeNfcStatus);
        btnHomeEnableNfc = findViewById(R.id.btnHomeEnableNfc);
        layoutCardSwapper = findViewById(R.id.layoutCardSwapper);

        // Bind Cards elements
        layoutVerticalCards = findViewById(R.id.layoutVerticalCards);
        layoutEmptyState = findViewById(R.id.layoutEmptyState);

        // Bind Settings elements


        // Bind Scanning Overlay elements
        layoutScanOverlay = findViewById(R.id.layoutScanOverlay);
        pulseRing1 = findViewById(R.id.pulseRing1);
        pulseRing2 = findViewById(R.id.pulseRing2);
        pulseRing3 = findViewById(R.id.pulseRing3);
        btnCancelScan = findViewById(R.id.btnCancelScan);

        if (btnCancelScan != null) {
            btnCancelScan.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (appViewModel.isClonerActive().getValue()) {
                    toggleScanClonerMode();
                }
            });
        }

        // Bind Payment Overlay elements
        layoutPaymentOverlay = findViewById(R.id.layoutPaymentOverlay);
        viewPaymentBgGlow = findViewById(R.id.viewPaymentBgGlow);
        paymentRing1 = findViewById(R.id.paymentRing1);
        paymentRing2 = findViewById(R.id.paymentRing2);
        paymentRing3 = findViewById(R.id.paymentRing3);
        cardPaymentContainer = findViewById(R.id.cardPaymentContainer);
        tvPaymentStatusLabel = findViewById(R.id.tvPaymentStatusLabel);
        tvPaymentCardId = findViewById(R.id.tvPaymentCardId);
        tvPaymentCardName = findViewById(R.id.tvPaymentCardName);
        tvPaymentSystemCode = findViewById(R.id.tvPaymentSystemCode);
        viewPaymentCardShine = findViewById(R.id.viewPaymentCardShine);
        tvPaymentInstruction = findViewById(R.id.tvPaymentInstruction);
        layoutPaymentUnlockIndicator = findViewById(R.id.layoutPaymentUnlockIndicator);
        imgPaymentFingerprint = findViewById(R.id.imgPaymentFingerprint);
        btnCancelPayment = findViewById(R.id.btnCancelPayment);
        cardActiveHero = findViewById(R.id.cardActiveHero);

        // Bind Payment-Rejected overlay elements
        layoutPaymentRejectedOverlay = findViewById(R.id.layoutPaymentRejectedOverlay);
        cardPaymentRejected = findViewById(R.id.cardPaymentRejected);
        viewPaymentRejectedGlow = findViewById(R.id.viewPaymentRejectedGlow);
        btnPaymentRejectedDismiss = findViewById(R.id.btnPaymentRejectedDismiss);
        if (btnPaymentRejectedDismiss != null) {
            btnPaymentRejectedDismiss.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                dismissPaymentRejectedOverlay();
            });
        }

        if (tvHeroCardId != null) {
            tvHeroCardId.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (!isUidRevealed) {
                    triggerPinAuthentication();
                } else {
                    isUidRevealed = false;
                    if (ringsHandler != null) {
                        ringsHandler.removeCallbacks(concealRunnable);
                    }
                    refreshUiData();
                    Toast.makeText(MainActivity.this, "Card ID concealed", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (cardActiveHero != null) {
            cardActiveHero.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                launchPaymentEmulationFlow();
            });
        }

        if (btnCancelPayment != null) {
            btnCancelPayment.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                closePaymentEmulationFlow();
            });
        }

        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                if (!PaymentAuthState.isAuthorized()) {
                    triggerBiometricAuthentication();
                }
            });
        }

        // Register APDU event listener for real-time reader contact feedback
        PaymentAuthState.registerListener(() -> {
            runOnUiThread(() -> {
                handleApduInteractionSuccess();
            });
        });

        Log.i(TAG, "NFC Card Wallet ready.");

        // nfcSessionManager is injected by Hilt
        checkNfcStatus();

        // Display Android Specs


        // ------------------ NAVIGATION STATE BINDINGS ------------------
        navHome.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(layoutHome, navHome);
        });
        navCards.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(layoutCards, navCards);
        });
        navSettings.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
            switchTab(layoutSettings, navSettings);
        });

        // Scan floating action toggle button (Haptic & bounce animation wrapper)
        navScan.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            v.animate()
                .scaleX(0.85f)
                .scaleY(0.85f)
                .setDuration(100)
                .withEndAction(() -> {
                    v.animate()
                        .scaleX(1.05f)
                        .scaleY(1.05f)
                        .setDuration(150)
                        .withEndAction(() -> {
                            v.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                        })
                        .start();
                    toggleScanClonerMode();
                })
                .start();
        });

        // ------------------ HOME LAYOUT ACTIONS ------------------
        btnHomeEnableNfc.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
            startActivity(intent);
        });

        // ------------------ SETTINGS ACTIONS ------------------

        // Notification request for Android 13+
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

    
        // Initialize card cloning backend engine
        libraryReader = new LibraryReader(this, new LibraryReader.OnCardClonedListener() {

            @Override
            public void onPaymentCardRejected() {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    // Stop the scan overlay cleanly first
                    appViewModel.setClonerActive(false);
                    updateScanButtonUi();
                    if (layoutScanOverlay != null) {
                        layoutScanOverlay.animate()
                            .alpha(0f)
                            .setDuration(180)
                            .withEndAction(() -> {
                                layoutScanOverlay.setVisibility(View.GONE);
                                stopScanningAnimation();
                                // Then show the premium warning
                                showPaymentRejectedOverlay();
                            })
                            .start();
                    } else {
                        stopScanningAnimation();
                        showPaymentRejectedOverlay();
                    }
                    Log.i(TAG, "Payment card safely rejected — no data stored or processed.");
                });
            }

            @Override
            public void onCardCloned(String libraryId, String hexDump) {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    appViewModel.setClonerActive(false);
                    updateScanButtonUi();
                    
                    if (layoutScanOverlay != null) {
                        layoutScanOverlay.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                layoutScanOverlay.setVisibility(View.GONE);
                                stopScanningAnimation();
                            })
                            .start();
                    } else {
                        stopScanningAnimation();
                    }

                    // Check if card with same ID or payload already exists in our saved profiles database
                    List<ProfileManager.CardProfile> existing = ProfileManager.getProfiles(MainActivity.this);
                    boolean isDuplicate = false;
                    for (ProfileManager.CardProfile p : existing) {
                        if (p.libraryId.equalsIgnoreCase(libraryId) || p.payloadHex.equalsIgnoreCase(hexDump)) {
                            isDuplicate = true;
                            break;
                        }
                    }

                    if (isDuplicate) {
                        androidx.appcompat.app.AlertDialog.Builder dupBuilder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this, R.style.CustomDialogTheme);
                        dupBuilder.setTitle("Card Already Exists");
                        dupBuilder.setMessage("This card's credentials are already present in your LibraryCard collection.");
                        dupBuilder.setPositiveButton("OK", null);
                        dupBuilder.show();
                        return;
                    }

                    String toastMessage = "Physical Card Read Successful!";
                    if (libraryId.startsWith("UID0") || libraryId.startsWith("UID")) {
                        toastMessage = "Physical Card Read Successful (UID Fallback)!";
                    }
                    Toast.makeText(MainActivity.this, toastMessage, Toast.LENGTH_SHORT).show();

                    // Prompt to input name
                    androidx.appcompat.app.AlertDialog.Builder builder = new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this, R.style.CustomDialogTheme);
                    builder.setTitle("Name Cloned Card");
                    
                    android.widget.FrameLayout container = new android.widget.FrameLayout(MainActivity.this);
                    android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    int marginPx = (int) (20 * getResources().getDisplayMetrics().density);
                    params.leftMargin = marginPx;
                    params.rightMargin = marginPx;
                    params.topMargin = (int) (8 * getResources().getDisplayMetrics().density);
                    params.bottomMargin = (int) (8 * getResources().getDisplayMetrics().density);
                    
                    final EditText input = new EditText(MainActivity.this);
                    input.setHint("e.g. Nadeem's Card");
                    input.setHintTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_muted));
                    input.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                    input.setTextSize(14);
                    input.setLayoutParams(params);
                    
                    // Style underline beautifully using active electric lime accent
                    if (input.getBackground() != null) {
                        input.getBackground().setColorFilter(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime), android.graphics.PorterDuff.Mode.SRC_ATOP);
                    }
                    
                    container.addView(input);
                    builder.setView(container);

                    builder.setPositiveButton("Save Card", (dialog, which) -> {
                        String enteredName = input.getText().toString().trim();
                        if (enteredName.isEmpty()) {
                            enteredName = "Cloned Card (" + libraryId + ")";
                        }

                        ProfileManager.CardProfile newProfile = new ProfileManager.CardProfile(
                            enteredName,
                            libraryId,
                            hexDump,
                            "00000000000000000000000000000000"
                        );

                        if (ProfileManager.addProfile(MainActivity.this, newProfile)) {
                            refreshUiData();
                            Log.i(TAG, "Cloned card saved as profile: " + enteredName);
                            Toast.makeText(MainActivity.this, "Card profile saved successfully!", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, "Profile with name already exists!", Toast.LENGTH_LONG).show();
                        }
                    });

                    builder.setNegativeButton("Discard", (dialog, which) -> {
                        String autoName = "Cloned Card (" + libraryId + ")";
                        ProfileManager.CardProfile newProfile = new ProfileManager.CardProfile(
                            autoName,
                            libraryId,
                            hexDump,
                            "00000000000000000000000000000000"
                        );
                        ProfileManager.addProfile(MainActivity.this, newProfile);
                        refreshUiData();
                        Log.i(TAG, "Card saved under fallback: " + autoName);
                    });

                    builder.setCancelable(false);
                    builder.show();
                });
            }

            @Override
            public void onCloneFailed(String error) {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    appViewModel.setClonerActive(false);
                    updateScanButtonUi();
                    if (layoutScanOverlay != null) {
                        layoutScanOverlay.animate()
                            .alpha(0f)
                            .setDuration(200)
                            .withEndAction(() -> {
                                layoutScanOverlay.setVisibility(View.GONE);
                                stopScanningAnimation();
                            })
                            .start();
                    } else {
                        stopScanningAnimation();
                    }
                    Toast.makeText(MainActivity.this, "Clone Failed: " + error, Toast.LENGTH_LONG).show();
                    Log.i(TAG, "Cloner authentication/link error: " + error);
                });
            }
        });

        // Bind Unsupported Device Overlay elements
        layoutUnsupportedOverlay = findViewById(R.id.layoutUnsupportedOverlay);
        viewUnsupportedGlow = findViewById(R.id.viewUnsupportedGlow);
        unsupportedRing1 = findViewById(R.id.unsupportedRing1);
        unsupportedRing2 = findViewById(R.id.unsupportedRing2);
        unsupportedRing3 = findViewById(R.id.unsupportedRing3);
        layoutUnsupportedCenter = findViewById(R.id.layoutUnsupportedCenter);
        btnUnsupportedLearnMore = findViewById(R.id.btnUnsupportedLearnMore);
        btnUnsupportedRetry = findViewById(R.id.btnUnsupportedRetry);

        if (btnUnsupportedLearnMore != null) {
            btnUnsupportedLearnMore.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                showLearnMoreDialog();
            });
        }

        if (btnUnsupportedRetry != null) {
            btnUnsupportedRetry.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                btnUnsupportedRetry.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
                    btnUnsupportedRetry.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    checkNfcStatus();
                }).start();
            });
        }

        // Bind NFC Disabled Overlay elements
        layoutNfcDisabledOverlay = findViewById(R.id.layoutNfcDisabledOverlay);
        viewDisabledGlow = findViewById(R.id.viewDisabledGlow);
        disabledRing1 = findViewById(R.id.disabledRing1);
        disabledRing2 = findViewById(R.id.disabledRing2);
        disabledRing3 = findViewById(R.id.disabledRing3);
        layoutDisabledCenter = findViewById(R.id.layoutDisabledCenter);
        btnDisabledEnableNfc = findViewById(R.id.btnDisabledEnableNfc);
        btnDisabledRetry = findViewById(R.id.btnDisabledRetry);

        if (btnDisabledEnableNfc != null) {
            btnDisabledEnableNfc.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                Intent intent = new Intent(android.provider.Settings.ACTION_NFC_SETTINGS);
                startActivity(intent);
            });
        }

        if (btnDisabledRetry != null) {
            btnDisabledRetry.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                btnDisabledRetry.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).withEndAction(() -> {
                    btnDisabledRetry.animate().scaleX(1f).scaleY(1f).setDuration(80).start();
                    checkNfcStatus();
                }).start();
            });
        }

        // Initialize lists & active card UI info when all views are 100% bound
        refreshUiData();

        // Staggered premium Home entrance animation on app launch
        startHomeEntranceAnimation();
    }

    private void switchTab(View targetLayout, View navButton) {
        View currentVisible = null;
        if (layoutHome.getVisibility() == View.VISIBLE) currentVisible = layoutHome;
        else if (layoutCards.getVisibility() == View.VISIBLE) currentVisible = layoutCards;
        else if (layoutSettings.getVisibility() == View.VISIBLE) currentVisible = layoutSettings;

        if (currentVisible == targetLayout) return;

        // Animate Out current visible screen
        if (currentVisible != null) {
            final View oldView = currentVisible;
            oldView.animate()
                .alpha(0f)
                .translationY(80f)
                .setDuration(180)
                .withEndAction(() -> {
                    oldView.setVisibility(View.GONE);
                    oldView.setTranslationY(0f); // Reset
                })
                .start();
        }

        // Animate In target screen
        targetLayout.setVisibility(View.VISIBLE);
        targetLayout.setAlpha(0f);
        targetLayout.setTranslationY(-80f);
        targetLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(220)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(() -> {
                if (targetLayout == layoutHome) {
                    startHomeEntranceAnimation();
                }
            })
            .start();

        // Reset bottom icons highlight color
        int inactiveColor = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_muted);
        imgNavHome.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavHome.setTextColor(inactiveColor);
        imgNavCards.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavCards.setTextColor(inactiveColor);
        imgNavSettings.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavSettings.setTextColor(inactiveColor);

        // Highlight selected nav tab with clean active feedback expansion and bounce
        int activeColor = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_primary);
        if (navButton == navHome) {
            imgNavHome.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavHome.setTextColor(activeColor);
        } else if (navButton == navCards) {
            imgNavCards.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavCards.setTextColor(activeColor);
        } else if (navButton == navSettings) {
            imgNavSettings.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavSettings.setTextColor(activeColor);
        }
    }

    private void toggleScanClonerMode() {
        if (!nfcSessionManager.isNfcEnabled()) {
            Toast.makeText(this, "NFC hardware must be active to scan cards.", Toast.LENGTH_SHORT).show();
            return;
        }

        appViewModel.setClonerActive(!appViewModel.isClonerActive().getValue());
        updateScanButtonUi();

        if (appViewModel.isClonerActive().getValue()) {
            // Put navigation indicator to home to view real-time card state change
            switchTab(layoutHome, navHome);
            tvCardStatusLabel.setText("TAP PHYSICAL CARD");
            tvCardStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
            viewHcePulseDot.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
            
            // Show scanning overlay with modern scale and fade animation
            if (layoutScanOverlay != null) {
                layoutScanOverlay.setVisibility(View.VISIBLE);
                layoutScanOverlay.setAlpha(0f);
                layoutScanOverlay.setScaleX(0.95f);
                layoutScanOverlay.setScaleY(0.95f);
                layoutScanOverlay.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(300)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
                startScanningAnimation();
            }

            if (nfcSessionManager.isNfcEnabled()) {
                nfcSessionManager.enableReaderMode(this);
            } else {
                appViewModel.setClonerActive(false);
                showNfcDisabledOverlay();
            }
            Log.i(TAG, "Cloner reader activated. Awaiting target card...");
        } else {
            // Stop scanning logic
            nfcSessionManager.disableReaderMode();
            refreshUiData();
            
            // Hide scanning overlay with animation
            if (layoutScanOverlay != null) {
                layoutScanOverlay.animate()
                    .alpha(0f)
                    .scaleX(0.95f)
                    .scaleY(0.95f)
                    .setDuration(220)
                    .withEndAction(() -> {
                        layoutScanOverlay.setVisibility(View.GONE);
                        stopScanningAnimation();
                    })
                    .start();
            } else {
                stopScanningAnimation();
            }
            Log.i(TAG, "Cloner reader deactivated.");
        }
    }

    private void updateScanButtonUi() {
        if (appViewModel.isClonerActive().getValue()) {
            navScan.setBackgroundResource(R.drawable.scan_button_background);
            navScan.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface)));
            imgNavScan.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
            if (txtNavScan != null) {
                txtNavScan.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
            }
        } else {
            navScan.setBackgroundResource(R.drawable.scan_button_background);
            navScan.setBackgroundTintList(null);
            imgNavScan.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_void)));
            if (txtNavScan != null) {
                txtNavScan.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_void));
            }
        }
    }

    private void refreshUiData() {
        profilesList = ProfileManager.getProfiles(this);
        ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);

        if (active != null) {
            tvHeroCardName.setText(active.name.toUpperCase());
            String libraryId = active.libraryId;
            if (!isUidRevealed) {
                tvHeroCardId.setText("•••• •••• ••••");
            } else {
                if (libraryId.length() == 12) {
                    tvHeroCardId.setText(libraryId.substring(0, 4) + " " + libraryId.substring(4, 8) + " " + libraryId.substring(8, 12));
                } else {
                    tvHeroCardId.setText(libraryId);
                }
            }
        } else {
            tvHeroCardName.setText("MEMBER CARD");
            tvHeroCardId.setText("MINT A NEW CARD TO START");
        }

        // Set NFC status label
        checkNfcStatus();

        // Populate card switcher layouts
        populateHorizontalCardSwapper();
        populateVerticalCardsList();

        // Trigger premium shine sweeping reflection across active card surface
        if (active != null) {
            triggerCardShineAnimation();
        }
    }

    private void checkNfcStatus() {
        DeviceCapabilityManager.AuditedCapabilities audit = DeviceCapabilityManager.auditDevice(this);

        // 1. Audit Hardware Compatibility
        if (!audit.isWalletFullySupported()) {
            // Enter premium Unsupported Screen
            runOnUiThread(() -> {
                showUnsupportedOverlay();
                disableAllWalletFeatures();
            });
            return;
        } else {
            // Dismiss Unsupported Screen cleanly
            runOnUiThread(this::dismissUnsupportedOverlay);
        }

        // 2. Audit NFC Activation State
        if (!audit.isNfcEnabled) {
            // Enter premium NFC Disabled Screen
            runOnUiThread(() -> {
                showNfcDisabledOverlay();
                disableAllWalletFeatures();
            });
            return;
        } else {
            // Dismiss NFC Disabled Screen cleanly
            runOnUiThread(this::dismissNfcDisabledOverlay);
        }

        // 3. System is Fully Capable & Enabled — resume standard operation
        runOnUiThread(() -> {
            enableAllWalletFeatures();
            
            // Sync status indicators on home screen
            tvHomeNfcStatus.setText("NFC hardware: Active & Emulating");
            tvHomeNfcStatus.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_success)); // state_success
            btnHomeEnableNfc.setVisibility(View.GONE);
            
            ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);
            if (active != null) {
                tvCardStatusLabel.setText("EMULATION ACTIVE");
                tvCardStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)); // electric lime
                viewHcePulseDot.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
            } else {
                tvCardStatusLabel.setText("MINT A CARD");
                tvCardStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_secondary)); // text_secondary
                viewHcePulseDot.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_secondary)));
            }
        });
    }

    private void showUnsupportedOverlay() {
        if (layoutUnsupportedOverlay == null || layoutUnsupportedOverlay.getVisibility() == View.VISIBLE) return;

        layoutUnsupportedOverlay.setVisibility(View.VISIBLE);
        layoutUnsupportedOverlay.setAlpha(0f);
        layoutUnsupportedOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(this::startBreathingRingsLoop)
            .start();

        // Bounce center icon container
        if (layoutUnsupportedCenter != null) {
            layoutUnsupportedCenter.setScaleX(0.7f);
            layoutUnsupportedCenter.setScaleY(0.7f);
            layoutUnsupportedCenter.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
        }
    }

    private void dismissUnsupportedOverlay() {
        if (layoutUnsupportedOverlay == null || layoutUnsupportedOverlay.getVisibility() != View.VISIBLE) return;

        layoutUnsupportedOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction(() -> {
                layoutUnsupportedOverlay.setVisibility(View.GONE);
                stopBreathingRingsLoop();
            })
            .start();
    }

    private void showNfcDisabledOverlay() {
        if (layoutNfcDisabledOverlay == null || layoutNfcDisabledOverlay.getVisibility() == View.VISIBLE) return;

        layoutNfcDisabledOverlay.setVisibility(View.VISIBLE);
        layoutNfcDisabledOverlay.setAlpha(0f);
        layoutNfcDisabledOverlay.animate()
            .alpha(1f)
            .setDuration(300)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(this::startBreathingRingsLoop)
            .start();

        // Bounce center icon container
        if (layoutDisabledCenter != null) {
            layoutDisabledCenter.setScaleX(0.7f);
            layoutDisabledCenter.setScaleY(0.7f);
            layoutDisabledCenter.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(450)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.2f))
                .start();
        }
    }

    private void dismissNfcDisabledOverlay() {
        if (layoutNfcDisabledOverlay == null || layoutNfcDisabledOverlay.getVisibility() != View.VISIBLE) return;

        layoutNfcDisabledOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction(() -> {
                layoutNfcDisabledOverlay.setVisibility(View.GONE);
                stopBreathingRingsLoop();
            })
            .start();
    }

    private void disableAllWalletFeatures() {
        // Disable bottom nav clicks elegantly
        navHome.setClickable(false);
        navCards.setClickable(false);
        navScan.setClickable(false);
        navSettings.setClickable(false);

        // Mute nav elements visually to look locked
        int mutedColor = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_card);
        imgNavHome.setImageTintList(ColorStateList.valueOf(mutedColor));
        txtNavHome.setTextColor(mutedColor);
        imgNavCards.setImageTintList(ColorStateList.valueOf(mutedColor));
        txtNavCards.setTextColor(mutedColor);
        imgNavSettings.setImageTintList(ColorStateList.valueOf(mutedColor));
        txtNavSettings.setTextColor(mutedColor);
        navScan.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#151515")));
        imgNavScan.setImageTintList(ColorStateList.valueOf(mutedColor));

        // Disable card click hero action
        if (cardActiveHero != null) {
            cardActiveHero.setClickable(false);
        }
    }

    private void enableAllWalletFeatures() {
        // Re-enable bottom nav clicks
        navHome.setClickable(true);
        navCards.setClickable(true);
        navScan.setClickable(true);
        navSettings.setClickable(true);

        // Reset bottom icons highlight color based on active layout
        refreshUiDataColorsOnly();

        // Re-enable card click hero action
        if (cardActiveHero != null) {
            cardActiveHero.setClickable(true);
        }
    }

    private void refreshUiDataColorsOnly() {
        int inactiveColor = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_muted);
        imgNavHome.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavHome.setTextColor(inactiveColor);
        imgNavCards.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavCards.setTextColor(inactiveColor);
        imgNavSettings.setImageTintList(ColorStateList.valueOf(inactiveColor));
        txtNavSettings.setTextColor(inactiveColor);
        navScan.setBackgroundTintList(null);
        imgNavScan.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));

        int activeColor = androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_primary);
        if (layoutHome.getVisibility() == View.VISIBLE) {
            imgNavHome.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavHome.setTextColor(activeColor);
        } else if (layoutCards.getVisibility() == View.VISIBLE) {
            imgNavCards.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavCards.setTextColor(activeColor);
        } else if (layoutSettings.getVisibility() == View.VISIBLE) {
            imgNavSettings.setImageTintList(ColorStateList.valueOf(activeColor));
            txtNavSettings.setTextColor(activeColor);
        }
    }

    private void startBreathingRingsLoop() {
        if (isBreathingRingsActive) return;
        isBreathingRingsActive = true;
        breathingRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isBreathingRingsActive) return;

                if (layoutUnsupportedOverlay != null && layoutUnsupportedOverlay.getVisibility() == View.VISIBLE) {
                    animateBreathingRing(unsupportedRing1, 0);
                    animateBreathingRing(unsupportedRing2, 300);
                    animateBreathingRing(unsupportedRing3, 600);
                } else if (layoutNfcDisabledOverlay != null && layoutNfcDisabledOverlay.getVisibility() == View.VISIBLE) {
                    animateBreathingRing(disabledRing1, 0);
                    animateBreathingRing(disabledRing2, 300);
                    animateBreathingRing(disabledRing3, 600);
                }

                breathingHandler.postDelayed(this, 2000);
            }
        };
        breathingHandler.post(breathingRunnable);
    }

    private void animateBreathingRing(final View ring, int delay) {
        if (ring == null) return;
        ring.setVisibility(View.VISIBLE);
        ring.setScaleX(0.3f);
        ring.setScaleY(0.3f);
        ring.setAlpha(0.6f);
        ring.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .alpha(0f)
            .setStartDelay(delay)
            .setDuration(1400)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(() -> {
                ring.setVisibility(View.GONE);
                ring.setScaleX(0.3f);
                ring.setScaleY(0.3f);
                ring.setAlpha(0.6f);
            })
            .start();
    }

    private void stopBreathingRingsLoop() {
        isBreathingRingsActive = false;
        if (breathingRunnable != null) {
            breathingHandler.removeCallbacks(breathingRunnable);
        }
        // Cancel all active ring animations
        if (unsupportedRing1 != null) { unsupportedRing1.animate().cancel(); unsupportedRing1.setVisibility(View.GONE); }
        if (unsupportedRing2 != null) { unsupportedRing2.animate().cancel(); unsupportedRing2.setVisibility(View.GONE); }
        if (unsupportedRing3 != null) { unsupportedRing3.animate().cancel(); unsupportedRing3.setVisibility(View.GONE); }
        if (disabledRing1 != null) { disabledRing1.animate().cancel(); disabledRing1.setVisibility(View.GONE); }
        if (disabledRing2 != null) { disabledRing2.animate().cancel(); disabledRing2.setVisibility(View.GONE); }
        if (disabledRing3 != null) { disabledRing3.animate().cancel(); disabledRing3.setVisibility(View.GONE); }
    }

    private void showLearnMoreDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert);
        builder.setTitle("NFC & HCE Compatibility")
               .setMessage("This application requires dynamic NFC hardware features and Host Card Emulation (HCE) to replicate secure physical library credentials.\n\n"
                         + "Supported devices include almost all modern premium Android phones with NFC enabled.\n\n"
                         + "Common reasons for incompatibility:\n"
                         + "• Device lacks an NFC controller chip\n"
                         + "• HCE (Host Card Emulation) feature disabled by the carrier or manufacturer\n"
                         + "• Custom ROMs without secure element integrations")
               .setPositiveButton("Understand", (dialog, which) -> {
                   dialog.dismiss();
               })
               .setCancelable(true);
        AlertDialog dialog = builder.create();
        dialog.show();
        
        // Custom styling to make the dialog feel premium
        try {
            dialog.getWindow().setBackgroundDrawableResource(R.drawable.glass_card_background);
            TextView titleView = dialog.findViewById(android.R.id.title);
            if (titleView != null) {
                titleView.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_primary));
                titleView.setTextSize(18);
            }
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                messageView.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_secondary));
                messageView.setTextSize(14);
                messageView.setLineSpacing(1.4f, 1.4f);
            }
            Button button = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (button != null) {
                button.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
                button.setAllCaps(false);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void populateHorizontalCardSwapper() {
        layoutCardSwapper.removeAllViews();
        ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);

        for (ProfileManager.CardProfile p : profilesList) {
            View cardView = getLayoutInflater().inflate(R.layout.item_horizontal_card, layoutCardSwapper, false);
            TextView tvMiniName = cardView.findViewById(R.id.tvMiniName);
            TextView tvMiniId = cardView.findViewById(R.id.tvMiniId);
            View borderSelected = cardView.findViewById(R.id.borderSelected);

            tvMiniName.setText(p.name);
            if (!isUidRevealed) {
                tvMiniId.setText("•••• •••• ••••");
            } else {
                tvMiniId.setText(p.libraryId);
            }

            if (active != null && p.name.equalsIgnoreCase(active.name)) {
                borderSelected.setVisibility(View.VISIBLE);
            } else {
                borderSelected.setVisibility(View.GONE);
            }

            // Spring click scale feedback and haptics
            cardView.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP);
                v.animate()
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(90)
                    .withEndAction(() -> {
                        v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(140)
                            .setInterpolator(new android.view.animation.OvershootInterpolator(2f))
                            .withEndAction(() -> {
                                ProfileManager.setActiveProfile(MainActivity.this, p.name);
                                refreshUiData();
                                Log.i(TAG, "Switched emulated card dynamically to: " + p.name);
                            })
                            .start();
                    })
                    .start();
            });

            layoutCardSwapper.addView(cardView);
        }
    }

    private void populateVerticalCardsList() {
        layoutVerticalCards.removeAllViews();
        ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);

        for (ProfileManager.CardProfile p : profilesList) {
            View cardItem = getLayoutInflater().inflate(R.layout.item_vertical_card_profile, layoutVerticalCards, false);
            TextView tvProfileName = cardItem.findViewById(R.id.tvProfileName);
            TextView tvProfileId = cardItem.findViewById(R.id.tvProfileId);
            Button btnSetAsActive = cardItem.findViewById(R.id.btnSetAsActive);
            View btnDeleteCard = cardItem.findViewById(R.id.btnDeleteCard);

            tvProfileName.setText(p.name);
            if (!isUidRevealed) {
                tvProfileId.setText("ID: •••• •••• ••••");
            } else {
                tvProfileId.setText("ID: " + p.libraryId);
            }

            if (active != null && p.name.equalsIgnoreCase(active.name)) {
                btnSetAsActive.setText("ACTIVE");
                btnSetAsActive.setEnabled(false);
                btnSetAsActive.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_card)));
                btnSetAsActive.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
                btnDeleteCard.setVisibility(View.GONE);
            } else {
                btnSetAsActive.setText("ACTIVATE");
                btnSetAsActive.setEnabled(true);
                btnSetAsActive.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
                btnSetAsActive.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface));
                btnSetAsActive.setOnClickListener(v -> {
                    ProfileManager.setActiveProfile(MainActivity.this, p.name);
                    refreshUiData();
                    Log.i(TAG, "Switched emulated card dynamically to: " + p.name);
                });

                btnDeleteCard.setVisibility(View.VISIBLE);
                btnDeleteCard.setOnClickListener(v -> {
                    new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this, R.style.CustomDialogTheme)
                        .setTitle("Remove Card")
                        .setMessage("Remove \"" + p.name + "\" from your collection?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            ProfileManager.deleteProfile(MainActivity.this, p.name);
                            refreshUiData();
                            Log.i(TAG, "Deleted card profile: " + p.name);
                            Toast.makeText(MainActivity.this, "Card removed", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                });
            }

            layoutVerticalCards.addView(cardItem);
        }

        // Toggle empty state visibility
        if (layoutEmptyState != null) {
            layoutEmptyState.setVisibility(profilesList.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        checkNfcStatus();

        // Re-arm NFC reader mode and restore UI state if the cloner was active
        if (appViewModel.isClonerActive().getValue()) {
            layoutScanOverlay.setVisibility(View.VISIBLE);
            layoutScanOverlay.setAlpha(1f);
            startScanningAnimation();
            
            if (nfcSessionManager.isNfcEnabled()) {
                nfcSessionManager.enableReaderMode(this);
            }
        }

        // Restore Payment Emulation UI state if it was active
        if (PaymentAuthState.isAuthorized()) {
            layoutPaymentOverlay.setVisibility(View.VISIBLE);
            layoutPaymentOverlay.setAlpha(1f);
            layoutPaymentOverlay.setScaleX(1f);
            layoutPaymentOverlay.setScaleY(1f);
            startPaymentPulsingRings();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfcAdapter != null) {
            try {
                nfcAdapter.disableReaderMode(this);
            } catch (Throwable t) {
                // Ignore gracefully
            }
        }
    }

    @Override
    public void onTagDiscovered(Tag tag) {
        Log.reset(TAG, "onTagDiscovered()");
        if (appViewModel.isClonerActive().getValue()) {
            try {
                libraryReader.processTag(tag);
            } catch (Throwable t) {
                Log.i(TAG, "Error: " + t.getMessage());
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    appViewModel.setClonerActive(false);
                    updateScanButtonUi();
                    refreshUiData();
                    Toast.makeText(MainActivity.this, "Error scanning tag: " + t.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }
    }

    // ==========================================
    // PREMIUM UI MOTION DESIGN ANIMATION METHODS
    // ==========================================

    private void startScanningAnimation() {
        if (pulseRing1 == null || pulseRing2 == null || pulseRing3 == null) return;
        
        stopScanningAnimation();

        // Start continuous scales
        pulseRing1.startAnimation(createPulseAnimation(0));
        pulseRing2.startAnimation(createPulseAnimation(500));
        pulseRing3.startAnimation(createPulseAnimation(1000));
    }

    private android.view.animation.AnimationSet createPulseAnimation(long delay) {
        android.view.animation.ScaleAnimation scale = new android.view.animation.ScaleAnimation(
            1f, 3.2f, 1f, 3.2f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f,
            android.view.animation.Animation.RELATIVE_TO_SELF, 0.5f
        );
        scale.setDuration(1600);
        scale.setRepeatCount(android.view.animation.Animation.INFINITE);

        android.view.animation.AlphaAnimation alpha = new android.view.animation.AlphaAnimation(0.9f, 0f);
        alpha.setDuration(1600);
        alpha.setRepeatCount(android.view.animation.Animation.INFINITE);

        android.view.animation.AnimationSet set = new android.view.animation.AnimationSet(false);
        set.addAnimation(scale);
        set.addAnimation(alpha);
        set.setStartOffset(delay);
        set.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        return set;
    }

    private void stopScanningAnimation() {
        if (pulseRing1 != null) pulseRing1.clearAnimation();
        if (pulseRing2 != null) pulseRing2.clearAnimation();
        if (pulseRing3 != null) pulseRing3.clearAnimation();
    }

    private void triggerCardShineAnimation() {
        final View shineView = findViewById(R.id.viewCardShine);
        if (shineView == null) return;

        shineView.setTranslationX(-250f);
        shineView.animate()
            .translationX(650f)
            .setDuration(850)
            .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
            .start();
    }

    private void startHomeEntranceAnimation() {
        View header = findViewById(R.id.layoutHomeHeader);
        View heroCard = findViewById(R.id.cardActiveHero);
        View swapperTitle = findViewById(R.id.tvSwapperTitle);
        View swapperScroll = findViewById(R.id.scrollSwapper);
        View statusCard = findViewById(R.id.layoutStatusCard);
        View tapGuideCard = findViewById(R.id.layoutTapGuideCard);

        animateEntrance(header, 0);
        animateEntrance(heroCard, 80);
        animateEntrance(swapperTitle, 160);
        animateEntrance(swapperScroll, 200);
        animateEntrance(statusCard, 260);
        animateEntrance(tapGuideCard, 320);
    }

    private void animateEntrance(final View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(60f);
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(400)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();
    }

    private void launchPaymentEmulationFlow() {
        ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);
        if (active == null) {
            Toast.makeText(this, "Please mint or create a card profile first.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Sync active card visual details to overlay card
        if (tvPaymentCardName != null) tvPaymentCardName.setText(active.name.toUpperCase());
        if (tvPaymentSystemCode != null) tvPaymentSystemCode.setText("SECURE");
        
        if (tvPaymentCardId != null) {
            tvPaymentCardId.setText("•••• •••• ••••");
        }

        // Initialize state
        PaymentAuthState.setAuthorized(false, null);
        
        if (tvPaymentStatusLabel != null) {
            tvPaymentStatusLabel.setText("SECURE LOCKED");
            tvPaymentStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
        }
        if (tvPaymentInstruction != null) {
            tvPaymentInstruction.setText("Verify credentials to arm emulation");
            tvPaymentInstruction.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.text_secondary));
        }
        if (imgPaymentFingerprint != null) {
            imgPaymentFingerprint.setImageResource(R.drawable.ic_scan);
            imgPaymentFingerprint.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
        }
        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface)));
        }

        // Animate overlay entrance with spring-loaded scale up
        if (layoutPaymentOverlay != null) {
            layoutPaymentOverlay.setVisibility(View.VISIBLE);
            layoutPaymentOverlay.setAlpha(0f);
            layoutPaymentOverlay.setScaleX(0.85f);
            layoutPaymentOverlay.setScaleY(0.85f);
            layoutPaymentOverlay.animate()
                .alpha(1f)
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(350)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.9f))
                .withEndAction(this::triggerBiometricAuthentication)
                .start();
        }

        // Bounce/Squeeze payment card display
        if (cardPaymentContainer != null) {
            cardPaymentContainer.setScaleX(0.7f);
            cardPaymentContainer.setScaleY(0.7f);
            cardPaymentContainer.animate()
                .scaleX(1f)
                .scaleY(1f)
                .setDuration(400)
                .setInterpolator(new android.view.animation.OvershootInterpolator(1.3f))
                .start();
        }

        // Radial glow slow breath
        if (viewPaymentBgGlow != null) {
            viewPaymentBgGlow.setScaleX(0.8f);
            viewPaymentBgGlow.setScaleY(0.8f);
            viewPaymentBgGlow.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(1200)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
        }
    }

    private void closePaymentEmulationFlow() {
        PaymentAuthState.setAuthorized(false, null);
        stopPaymentPulsingRings();

        if (layoutPaymentOverlay != null && layoutPaymentOverlay.getVisibility() == View.VISIBLE) {
            layoutPaymentOverlay.animate()
                .alpha(0f)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(250)
                .withEndAction(() -> {
                    layoutPaymentOverlay.setVisibility(View.GONE);
                })
                .start();
        }
    }

    private void triggerBiometricAuthentication() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int authenticators;
        String subtitle;

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            int weakCheck = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
            int strongCheck = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);

            if (weakCheck == BiometricManager.BIOMETRIC_SUCCESS || strongCheck == BiometricManager.BIOMETRIC_SUCCESS) {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                        | BiometricManager.Authenticators.BIOMETRIC_WEAK
                        | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                subtitle = "Use fingerprint, face, or screen lock to arm card";
            } else {
                authenticators = BiometricManager.Authenticators.DEVICE_CREDENTIAL;
                subtitle = "Use your PIN, pattern, or password to arm card";
            }
        } else {
            int strongCheck = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG);
            if (strongCheck == BiometricManager.BIOMETRIC_SUCCESS) {
                authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG;
            } else {
                Log.i(TAG, "No biometric enrolled — auto-authorizing on legacy device.");
                handleBiometricSuccess();
                return;
            }
            subtitle = "Use fingerprint to arm card";
        }

        String negativeButtonText = (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R
                && authenticators == BiometricManager.Authenticators.BIOMETRIC_STRONG) ? "Cancel" : null;

        authenticator.authenticate("Identity Verification", subtitle, negativeButtonText, authenticators, new Authenticator.AuthCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    handleBiometricSuccess();
                });
            }

            @Override
            public void onFailed(String reason) {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    handleBiometricFailure(reason);
                });
            }

            @Override
            public void onCancelled() {
                runOnUiThread(() -> {
                    if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                    closePaymentEmulationFlow();
                });
            }
        });
    }

    private void handleBiometricSuccess() {
        ProfileManager.CardProfile active = ProfileManager.getActiveProfile(this);
        if (active == null) return;
        PaymentAuthState.setAuthorized(true, active.libraryId);

        if (tvPaymentCardId != null) {
            String libraryId = active.libraryId;
            if (libraryId.length() == 12) {
                tvPaymentCardId.setText(libraryId.substring(0, 4) + " " + libraryId.substring(4, 8) + " " + libraryId.substring(8, 12));
            } else {
                tvPaymentCardId.setText(libraryId);
            }
        }

        // Success unlock vibration feedback
        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        // Glow card with active armed badge
        if (tvPaymentStatusLabel != null) {
            tvPaymentStatusLabel.setText("ARMED & READY");
            tvPaymentStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
        }
        
        if (tvPaymentInstruction != null) {
            tvPaymentInstruction.setText("Hold near library reader terminal");
            tvPaymentInstruction.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime));
        }
        if (imgPaymentFingerprint != null) {
            imgPaymentFingerprint.setImageResource(R.drawable.ic_nfc_wave);
            imgPaymentFingerprint.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.accent_lime)));
        }
        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface)));
        }

        // Start Concentric Pulsing NFC waves
        startPaymentPulsingRings();
        
        // Sweep shine gradient
        triggerPaymentCardSweepLoop();
    }

    private void handleBiometricFailure(String error) {
        PaymentAuthState.setAuthorized(false, null);

        // Perform Card Shake Animation
        if (cardPaymentContainer != null) {
            cardPaymentContainer.animate().translationX(-15f).setDuration(50).withEndAction(() -> 
                cardPaymentContainer.animate().translationX(15f).setDuration(50).withEndAction(() -> 
                    cardPaymentContainer.animate().translationX(-10f).setDuration(50).withEndAction(() -> 
                        cardPaymentContainer.animate().translationX(10f).setDuration(50).withEndAction(() -> 
                            cardPaymentContainer.animate().translationX(0f).setDuration(50).start()
                        ).start()
                    ).start()
                ).start()
            ).start();
        }

        if (tvPaymentStatusLabel != null) {
            tvPaymentStatusLabel.setText("AUTH FAILED");
            tvPaymentStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_error));
        }
        if (tvPaymentInstruction != null) {
            tvPaymentInstruction.setText(error + " Tap icon to retry.");
            tvPaymentInstruction.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_error));
        }
        if (imgPaymentFingerprint != null) {
            imgPaymentFingerprint.setImageResource(R.drawable.ic_scan);
            imgPaymentFingerprint.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_error)));
        }
        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface)));
        }
    }

    private void handleApduInteractionSuccess() {
        if (!PaymentAuthState.isAuthorized()) return;

        // Vibrate to confirm card read complete
        if (layoutPaymentOverlay != null) {
            layoutPaymentOverlay.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        }

        // Cyan/Green success confirmation glow
        if (tvPaymentStatusLabel != null) {
            tvPaymentStatusLabel.setText("TRANSACTION COMPLETE");
            tvPaymentStatusLabel.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_success));
        }
        if (tvPaymentInstruction != null) {
            tvPaymentInstruction.setText("Card emulated successfully!");
            tvPaymentInstruction.setTextColor(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_success));
        }
        if (imgPaymentFingerprint != null) {
            imgPaymentFingerprint.setImageResource(R.drawable.ic_scan); // or custom tick
            imgPaymentFingerprint.setImageTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.state_success)));
        }
        if (layoutPaymentUnlockIndicator != null) {
            layoutPaymentUnlockIndicator.setBackgroundTintList(ColorStateList.valueOf(androidx.core.content.ContextCompat.getColor(MainActivity.this, R.color.bg_surface)));
        }

        // Delay 1.5 seconds, then dismiss and return
        if (ringsHandler != null) {
            ringsHandler.postDelayed(() -> {
                if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                closePaymentEmulationFlow();
                Toast.makeText(MainActivity.this, "Library card emulated successfully", Toast.LENGTH_SHORT).show();
            }, 1500);
        }
    }

    private void startPaymentPulsingRings() {
        isPaymentRingsActive = true;
        ringsRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isPaymentRingsActive) return;

                animatePaymentRing(paymentRing1, 0);
                animatePaymentRing(paymentRing2, 300);
                animatePaymentRing(paymentRing3, 600);

                ringsHandler.postDelayed(this, 1800);
            }
        };
        ringsHandler.post(ringsRunnable);
    }

    private void animatePaymentRing(final View ring, int delay) {
        if (ring == null) return;
        ring.setVisibility(View.VISIBLE);
        ring.setScaleX(0.3f);
        ring.setScaleY(0.3f);
        ring.setAlpha(0.7f);
        ring.animate()
            .scaleX(1.4f)
            .scaleY(1.4f)
            .alpha(0f)
            .setStartDelay(delay)
            .setDuration(1200)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .withEndAction(() -> {
                ring.setVisibility(View.GONE);
                ring.setScaleX(0.3f);
                ring.setScaleY(0.3f);
                ring.setAlpha(0.7f);
            })
            .start();
    }

    private void stopPaymentPulsingRings() {
        isPaymentRingsActive = false;
        if (ringsRunnable != null) {
            ringsHandler.removeCallbacks(ringsRunnable);
        }
        if (paymentRing1 != null) {
            paymentRing1.animate().cancel();
            paymentRing1.setVisibility(View.GONE);
        }
        if (paymentRing2 != null) {
            paymentRing2.animate().cancel();
            paymentRing2.setVisibility(View.GONE);
        }
        if (paymentRing3 != null) {
            paymentRing3.animate().cancel();
            paymentRing3.setVisibility(View.GONE);
        }
    }

    private void triggerPaymentCardSweepLoop() {
        if (!PaymentAuthState.isAuthorized()) return;
        if (viewPaymentCardShine != null) {
            viewPaymentCardShine.setTranslationX(-250f);
            viewPaymentCardShine.animate()
                .translationX(400f)
                .setDuration(1200)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    if (ringsHandler != null) {
                        ringsHandler.postDelayed(() -> {
                            if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
                            if (PaymentAuthState.isAuthorized()) {
                                triggerPaymentCardSweepLoop();
                            }
                        }, 2500);
                    }
                })
                .start();
        }
    }

    // =========================================================
    // PAYMENT CARD REJECTED OVERLAY — show / dismiss
    // =========================================================

    /**
     * Shows the premium payment-rejected warning overlay with a spring entrance
     * animation and a soft haptic pulse. Safe to call from any thread via
     * runOnUiThread.
     */
    private void showPaymentRejectedOverlay() {
        if (layoutPaymentRejectedOverlay == null) return;

        // Soft haptic feedback — non-aggressive, just "tap"
        layoutPaymentRejectedOverlay.performHapticFeedback(
            android.view.HapticFeedbackConstants.KEYBOARD_TAP);

        layoutPaymentRejectedOverlay.setVisibility(View.VISIBLE);
        layoutPaymentRejectedOverlay.setAlpha(0f);

        // Fade-in the dim background
        layoutPaymentRejectedOverlay.animate()
            .alpha(1f)
            .setDuration(280)
            .setInterpolator(new android.view.animation.DecelerateInterpolator())
            .start();

        // Spring-in the card from below
        if (cardPaymentRejected != null) {
            cardPaymentRejected.setAlpha(0f);
            cardPaymentRejected.setTranslationY(120f);
            cardPaymentRejected.setScaleX(0.88f);
            cardPaymentRejected.setScaleY(0.88f);
            cardPaymentRejected.animate()
                .alpha(1f)
                .translationY(0f)
                .scaleX(1f)
                .scaleY(1f)
                .setStartDelay(100)
                .setDuration(420)
                .setInterpolator(new android.view.animation.OvershootInterpolator(0.85f))
                .start();
        }

        // Gentle glow pulse
        if (viewPaymentRejectedGlow != null) {
            viewPaymentRejectedGlow.setScaleX(0.6f);
            viewPaymentRejectedGlow.setScaleY(0.6f);
            viewPaymentRejectedGlow.setAlpha(0f);
            viewPaymentRejectedGlow.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .alpha(0.6f)
                .setDuration(600)
                .setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator())
                .start();
        }
    }

    /**
     * Dismisses the payment-rejected overlay with a smooth fade-and-scale-out.
     */
    private void dismissPaymentRejectedOverlay() {
        if (layoutPaymentRejectedOverlay == null ||
                layoutPaymentRejectedOverlay.getVisibility() != View.VISIBLE) return;

        // Scale-out the card
        if (cardPaymentRejected != null) {
            cardPaymentRejected.animate()
                .alpha(0f)
                .translationY(60f)
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(200)
                .start();
        }

        layoutPaymentRejectedOverlay.animate()
            .alpha(0f)
            .setDuration(260)
            .withEndAction(() -> {
                layoutPaymentRejectedOverlay.setVisibility(View.GONE);
                // Reset for next use
                if (cardPaymentRejected != null) {
                    cardPaymentRejected.setAlpha(1f);
                    cardPaymentRejected.setTranslationY(0f);
                    cardPaymentRejected.setScaleX(1f);
                    cardPaymentRejected.setScaleY(1f);
                }
            })
            .start();
    }

    private final Runnable concealRunnable = new Runnable() {
        @Override
        public void run() {
            if (MainActivity.this.isFinishing() || MainActivity.this.isDestroyed()) return;
            isUidRevealed = false;
            refreshUiData();
            Toast.makeText(MainActivity.this, "Card ID concealed for security", Toast.LENGTH_SHORT).show();
        }
    };

    private void revealUidTemporarily() {
        isUidRevealed = true;
        refreshUiData();
        
        if (ringsHandler != null) {
            ringsHandler.removeCallbacks(concealRunnable);
            ringsHandler.postDelayed(concealRunnable, 15000);
        }
        Toast.makeText(MainActivity.this, "Card ID revealed for 15s", Toast.LENGTH_SHORT).show();
    }

    private void triggerPinAuthentication() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            authenticator.authenticate("Confirm Device PIN", "Enter your phone PIN to reveal the card ID", null, BiometricManager.Authenticators.DEVICE_CREDENTIAL, new Authenticator.AuthCallback() {
                @Override
                public void onSuccess() {
                    runOnUiThread(() -> revealUidTemporarily());
                }

                @Override
                public void onFailed(String reason) {
                    Toast.makeText(MainActivity.this, "Authentication Failed", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onCancelled() {}
            });
        } else {
            android.app.KeyguardManager km = (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
            if (km != null && km.isDeviceSecure()) {
                Intent intent = km.createConfirmDeviceCredentialIntent("Confirm PIN", "Enter your phone PIN to reveal the card ID");
                if (intent != null) {
                    startActivityForResult(intent, REQUEST_CODE_CONFIRM_PIN);
                }
            } else {
                revealUidTemporarily();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_CONFIRM_PIN) {
            if (resultCode == RESULT_OK) {
                revealUidTemporarily();
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopBreathingRingsLoop();
        // Securely revoke authorization and hide the payment overlay when app is backgrounded or screen locked
        closePaymentEmulationFlow();
    }

    @Override
    public void onBackPressed() {
        if (layoutScanOverlay != null && layoutScanOverlay.getVisibility() == View.VISIBLE) {
            if (appViewModel.isClonerActive().getValue()) {
                toggleScanClonerMode();
            } else {
                layoutScanOverlay.setVisibility(View.GONE);
                stopScanningAnimation();
            }
            return;
        }
        if (layoutPaymentOverlay != null && layoutPaymentOverlay.getVisibility() == View.VISIBLE) {
            closePaymentEmulationFlow();
            return;
        }
        if (layoutPaymentRejectedOverlay != null && layoutPaymentRejectedOverlay.getVisibility() == View.VISIBLE) {
            dismissPaymentRejectedOverlay();
            return;
        }
        if (layoutUnsupportedOverlay != null && layoutUnsupportedOverlay.getVisibility() == View.VISIBLE) {
            dismissUnsupportedOverlay();
            return;
        }
        if (layoutHome != null && layoutHome.getVisibility() != View.VISIBLE) {
            switchTab(layoutHome, navHome);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        PaymentAuthState.unregisterListener();
        stopBreathingRingsLoop();
        stopPaymentPulsingRings();
        
        if (ringsHandler != null) {
            ringsHandler.removeCallbacksAndMessages(null);
        }
        if (breathingHandler != null) {
            breathingHandler.removeCallbacksAndMessages(null);
        }
        super.onDestroy();
    }
}

