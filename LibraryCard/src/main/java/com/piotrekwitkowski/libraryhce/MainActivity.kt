package com.piotrekwitkowski.libraryhce

import android.content.res.ColorStateList
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.piotrekwitkowski.libraryhce.domain.Authenticator
import com.piotrekwitkowski.libraryhce.domain.PaymentStateRepository
import com.piotrekwitkowski.libraryhce.reader.LibraryReader
import com.piotrekwitkowski.libraryhce.ui.CardsFragment
import com.piotrekwitkowski.libraryhce.ui.HomeFragment
import com.piotrekwitkowski.libraryhce.ui.SettingsFragment
import com.piotrekwitkowski.log.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), NfcAdapter.ReaderCallback, LibraryReader.OnCardClonedListener {

    private val TAG = "MainActivity"

    @Inject lateinit var authenticator: Authenticator
    @Inject lateinit var nfcSessionManager: NfcSessionManager
    @Inject lateinit var paymentStateRepository: PaymentStateRepository

    private val appViewModel: AppViewModel by viewModels()
    private lateinit var libraryReader: LibraryReader

    // Bottom Navigation
    private lateinit var navHome: LinearLayout
    private lateinit var navCards: LinearLayout
    private lateinit var navSettings: LinearLayout
    private lateinit var navScan: LinearLayout
    private lateinit var imgNavHome: ImageView
    private lateinit var txtNavHome: TextView
    private lateinit var imgNavCards: ImageView
    private lateinit var txtNavCards: TextView
    private lateinit var imgNavScan: ImageView
    private lateinit var txtNavScan: TextView
    private lateinit var imgNavSettings: ImageView
    private lateinit var txtNavSettings: TextView

    // Overlays
    private lateinit var layoutScanOverlay: View
    private lateinit var layoutPaymentOverlay: View
    private lateinit var layoutUnsupportedOverlay: View
    private lateinit var layoutNfcDisabledOverlay: View

    private var lastScanTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        libraryReader = LibraryReader(this, this)

        bindViews()
        setupNavigation()
        observeViewModel()

        // Load default fragment
        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), navHome)
        }
    }

    private fun bindViews() {
        navHome = findViewById(R.id.nav_home)
        navCards = findViewById(R.id.nav_cards)
        navSettings = findViewById(R.id.nav_settings)
        navScan = findViewById(R.id.nav_scan)

        imgNavHome = findViewById(R.id.imgNavHome)
        txtNavHome = findViewById(R.id.txtNavHome)
        imgNavCards = findViewById(R.id.imgNavCards)
        txtNavCards = findViewById(R.id.txtNavCards)
        imgNavScan = findViewById(R.id.imgNavScan)
        txtNavScan = findViewById(R.id.txtNavScan)
        imgNavSettings = findViewById(R.id.imgNavSettings)
        txtNavSettings = findViewById(R.id.txtNavSettings)

        layoutScanOverlay = findViewById(R.id.layoutScanOverlay)
        layoutPaymentOverlay = findViewById(R.id.layoutPaymentOverlay)
        layoutUnsupportedOverlay = findViewById(R.id.layoutUnsupportedOverlay)
        layoutNfcDisabledOverlay = findViewById(R.id.layoutNfcDisabledOverlay)

        findViewById<View>(R.id.btnCancelScan)?.setOnClickListener {
            appViewModel.setClonerActive(false)
        }

        findViewById<View>(R.id.btnCancelPayment)?.setOnClickListener {
            appViewModel.setPaymentEmulationActive(false)
        }
    }

    private fun setupNavigation() {
        navHome.setOnClickListener { switchFragment(HomeFragment(), navHome) }
        navCards.setOnClickListener { switchFragment(CardsFragment(), navCards) }
        navSettings.setOnClickListener { switchFragment(SettingsFragment(), navSettings) }

        navScan.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            appViewModel.setClonerActive(!appViewModel.isClonerActive.value)
        }
    }

    private fun switchFragment(fragment: Fragment, navButton: View) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, fragment)
            .commit()

        // Update UI colors
        val inactive = ContextCompat.getColor(this, R.color.text_muted)
        val active = ContextCompat.getColor(this, R.color.text_primary)

        imgNavHome.imageTintList = ColorStateList.valueOf(if (navButton == navHome) active else inactive)
        txtNavHome.setTextColor(if (navButton == navHome) active else inactive)

        imgNavCards.imageTintList = ColorStateList.valueOf(if (navButton == navCards) active else inactive)
        txtNavCards.setTextColor(if (navButton == navCards) active else inactive)

        imgNavSettings.imageTintList = ColorStateList.valueOf(if (navButton == navSettings) active else inactive)
        txtNavSettings.setTextColor(if (navButton == navSettings) active else inactive)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            appViewModel.isClonerActive.collect { isActive ->
                updateScanButtonUi(isActive)
                if (isActive) {
                    layoutScanOverlay.visibility = View.VISIBLE
                    if (nfcSessionManager.isNfcEnabled) {
                        nfcSessionManager.enableReaderMode(this@MainActivity)
                    }
                } else {
                    layoutScanOverlay.visibility = View.GONE
                    nfcSessionManager.disableReaderMode()
                }
            }
        }

        lifecycleScope.launch {
            appViewModel.isPaymentEmulationActive.collect { isActive ->
                if (isActive) {
                    val profile = appViewModel.activeProfile.value
                    if (profile == null) {
                        Toast.makeText(this@MainActivity, "Mint a card first", Toast.LENGTH_SHORT).show()
                        appViewModel.setPaymentEmulationActive(false)
                        return@collect
                    }
                    layoutPaymentOverlay.visibility = View.VISIBLE
                    triggerBiometricAuthentication()
                } else {
                    layoutPaymentOverlay.visibility = View.GONE
                    paymentStateRepository.setAuthorized(false, null)
                }
            }
        }

        lifecycleScope.launch {
            paymentStateRepository.apduInteractionEvent.collect {
                // Flash success when payment happens
                Toast.makeText(this@MainActivity, "Card emulated successfully!", Toast.LENGTH_SHORT).show()
                appViewModel.setPaymentEmulationActive(false)
            }
        }
    }

    private fun updateScanButtonUi(isActive: Boolean) {
        if (isActive) {
            navScan.setBackgroundResource(R.drawable.scan_button_background)
            navScan.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_surface))
            imgNavScan.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.accent_lime))
            txtNavScan.setTextColor(ContextCompat.getColor(this, R.color.accent_lime))
        } else {
            navScan.setBackgroundResource(R.drawable.scan_button_background)
            navScan.backgroundTintList = null
            imgNavScan.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.bg_void))
            txtNavScan.setTextColor(ContextCompat.getColor(this, R.color.bg_void))
        }
    }

    private fun triggerBiometricAuthentication() {
        val biometricManager = BiometricManager.from(this)
        var authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        }

        authenticator.authenticate("Identity Verification", "Verify to arm card", null, authenticators, object : Authenticator.AuthCallback {
            override fun onSuccess() {
                runOnUiThread {
                    paymentStateRepository.setAuthorized(true, appViewModel.activeProfile.value?.libraryId)
                    findViewById<TextView>(R.id.tvPaymentStatusLabel)?.text = "ARMED & READY"
                }
            }

            override fun onFailed(reason: String) {
                runOnUiThread {
                    appViewModel.setPaymentEmulationActive(false)
                    Toast.makeText(this@MainActivity, "Auth failed: $reason", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onCancelled() {
                runOnUiThread { appViewModel.setPaymentEmulationActive(false) }
            }
        })
    }

    override fun onTagDiscovered(tag: Tag) {
        // Phase 5: Scan Debounce to prevent duplicate clones
        val now = System.currentTimeMillis()
        if (now - lastScanTime < 2000) return
        lastScanTime = now

        if (appViewModel.isClonerActive.value) {
            try {
                libraryReader.processTag(tag)
            } catch (t: Throwable) {
                runOnUiThread {
                    appViewModel.setClonerActive(false)
                    Toast.makeText(this, "Error scanning tag: ${t.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onPaymentCardRejected() {
        runOnUiThread {
            appViewModel.setClonerActive(false)
            findViewById<View>(R.id.layoutPaymentRejectedOverlay)?.visibility = View.VISIBLE
        }
    }

    override fun onCardCloned(libraryId: String, hexDump: String) {
        runOnUiThread {
            appViewModel.setClonerActive(false)
            
            val builder = AlertDialog.Builder(this, R.style.CustomDialogTheme)
            builder.setTitle("Name Cloned Card")

            val container = FrameLayout(this)
            val params = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            val marginPx = (20 * resources.displayMetrics.density).toInt()
            params.setMargins(marginPx, marginPx, marginPx, marginPx)

            val input = EditText(this)
            input.hint = "e.g. Nadeem's Card"
            input.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            input.layoutParams = params
            container.addView(input)
            builder.setView(container)

            builder.setPositiveButton("Save") { _, _ ->
                val name = if (input.text.toString().trim().isEmpty()) "Cloned Card ($libraryId)" else input.text.toString().trim()
                val profile = ProfileManager.CardProfile(name, libraryId, hexDump, "00000000000000000000000000000000")
                ProfileManager.addProfile(this, profile)
                appViewModel.refreshProfiles()
                Toast.makeText(this, "Card saved", Toast.LENGTH_SHORT).show()
            }
            builder.setNegativeButton("Discard", null)
            builder.show()
        }
    }

    override fun onCloneFailed(error: String) {
        runOnUiThread {
            appViewModel.setClonerActive(false)
            Toast.makeText(this, "Clone failed: $error", Toast.LENGTH_LONG).show()
        }
    }

    override fun onPause() {
        super.onPause()
        try { nfcAdapter?.disableReaderMode(this) } catch (e: Exception) {}
    }

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
}
