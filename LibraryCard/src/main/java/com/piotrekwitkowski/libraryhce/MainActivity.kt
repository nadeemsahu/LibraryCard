package com.piotrekwitkowski.libraryhce

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.content.res.ColorStateList
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
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

    // Scan pulse animation
    private var scanPulseAnimator: AnimatorSet? = null

    // Payment card shimmer — ObjectAnimator + Handler for inter-sweep pause
    private var paymentShineAnimator: ObjectAnimator? = null
    private val paymentShineHandler = Handler(Looper.getMainLooper())
    private var paymentShineRunnable: Runnable? = null

    private var lastScanTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        libraryReader = LibraryReader(this, this)

        bindViews()
        setupNavigation()
        observeViewModel()
        checkDeviceCapabilities()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (appViewModel.isPaymentEmulationActive.value) {
                    appViewModel.setPaymentEmulationActive(false)
                } else if (appViewModel.isClonerActive.value) {
                    appViewModel.setClonerActive(false)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })

        // Load default fragment
        if (savedInstanceState == null) {
            switchFragment(HomeFragment(), navHome)
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check every time the user returns (e.g. after enabling NFC in Settings)
        checkDeviceCapabilities()
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

        // Payment-rejected overlay dismiss
        findViewById<View>(R.id.btnPaymentRejectedDismiss)?.setOnClickListener {
            findViewById<View>(R.id.layoutPaymentRejectedOverlay)?.visibility = View.GONE
        }

        // BUG-001 FIX: wire the four previously-dead error overlay buttons

        // Unsupported device overlay
        findViewById<View>(R.id.btnUnsupportedLearnMore)?.setOnClickListener {
            AlertDialog.Builder(this, R.style.CustomDialogTheme)
                .setTitle("NFC Wallet Unavailable")
                .setMessage(
                    "This app requires an Android device with NFC and Host Card Emulation (HCE) support.\n\n" +
                    "• NFC lets you scan physical library cards.\n" +
                    "• HCE lets your phone emulate a card at readers.\n\n" +
                    "Please use a compatible NFC-enabled Android device to access all features."
                )
                .setPositiveButton("Got It", null)
                .show()
        }
        findViewById<View>(R.id.btnUnsupportedRetry)?.setOnClickListener {
            checkDeviceCapabilities()
        }

        // NFC disabled overlay
        findViewById<View>(R.id.btnDisabledEnableNfc)?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
        findViewById<View>(R.id.btnDisabledRetry)?.setOnClickListener {
            checkDeviceCapabilities()
        }
    }

    private fun setupNavigation() {
        // Nav tap micro-interaction: quick scale down + spring back
        fun navTap(view: View, action: () -> Unit) {
            view.setOnTouchListener { v, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN ->
                        v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                            .setInterpolator(DecelerateInterpolator()).start()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                            .setInterpolator(OvershootInterpolator(2f)).start()
                        if (event.action == MotionEvent.ACTION_UP) action()
                    }
                }
                true
            }
        }

        navTap(navHome)     { switchFragment(HomeFragment(), navHome) }
        navTap(navCards)    { switchFragment(CardsFragment(), navCards) }
        navTap(navSettings) { switchFragment(SettingsFragment(), navSettings) }

        navScan.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80)
                        .setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(200)
                        .setInterpolator(OvershootInterpolator(2f)).start()
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                        appViewModel.setClonerActive(!appViewModel.isClonerActive.value)
                    }
                }
            }
            true
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
                    // Animate scan overlay in: alpha fade
                    layoutScanOverlay.animate().cancel()
                    layoutScanOverlay.alpha = 0f
                    layoutScanOverlay.visibility = View.VISIBLE
                    layoutScanOverlay.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    startScanPulseAnimation()
                    if (nfcSessionManager.isNfcEnabled) {
                        nfcSessionManager.enableReaderMode(this@MainActivity)
                    }
                } else {
                    layoutScanOverlay.animate().cancel()
                    layoutScanOverlay.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { layoutScanOverlay.visibility = View.GONE }
                        .start()
                    stopScanPulseAnimation()
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
                    // Slide payment card up from below, then sweep shine
                    val paymentCard = findViewById<View>(R.id.cardPaymentContainer)
                    paymentCard?.translationY = 80f
                    paymentCard?.alpha = 0f
                    layoutPaymentOverlay.alpha = 0f
                    layoutPaymentOverlay.visibility = View.VISIBLE
                    layoutPaymentOverlay.animate()
                        .alpha(1f)
                        .setDuration(220)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    paymentCard?.animate()
                        ?.translationY(0f)
                        ?.alpha(1f)
                        ?.setDuration(420)
                        ?.setStartDelay(60)
                        ?.setInterpolator(OvershootInterpolator(1.2f))
                        ?.withEndAction { startPaymentCardShimmer() }
                        ?.start()
                    triggerBiometricAuthentication()
                } else {
                    stopPaymentCardShimmer()
                    // CR-010 FIX: cancel any in-flight animator on the overlay before
                    // starting a new one. Rapid toggle causes withEndAction{GONE} from
                    // a previous fade-out to fire during the next fade-in cycle.
                    layoutPaymentOverlay.animate().cancel()
                    layoutPaymentOverlay.animate()
                        .alpha(0f)
                        .setDuration(180)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction { layoutPaymentOverlay.visibility = View.GONE }
                        .start()
                    paymentStateRepository.setAuthorized(false, null)
                }
            }
        }

        lifecycleScope.launch {
            paymentStateRepository.apduInteractionEvent.collect {
                // Pulse haptic when payment APDUs are exchanged
                val paymentCard = findViewById<View>(R.id.cardPaymentContainer)
                paymentCard?.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            }
        }

        lifecycleScope.launch {
            paymentStateRepository.emulationCompleteEvent.collect {
                // Flash success when payment session officially ends (deactivated)
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

    private fun startScanPulseAnimation() {
        stopScanPulseAnimation()
        val ring1 = findViewById<View>(R.id.pulseRing1) ?: return
        val ring2 = findViewById<View>(R.id.pulseRing2) ?: return
        val ring3 = findViewById<View>(R.id.pulseRing3) ?: return

        // AnimatorSet does NOT support repeatCount — it must be set on each ObjectAnimator directly.
        // Each ring's animators are started independently so the repeat actually fires.
        fun startPulse(view: View, durationMs: Long, startDelayMs: Long) {
            val scaleX = ObjectAnimator.ofFloat(view, "scaleX", 0.85f, 1.1f, 0.85f).apply {
                this.duration    = durationMs
                this.startDelay  = startDelayMs
                repeatCount      = ObjectAnimator.INFINITE
                repeatMode       = ObjectAnimator.RESTART
                interpolator     = AccelerateDecelerateInterpolator()
            }
            val scaleY = ObjectAnimator.ofFloat(view, "scaleY", 0.85f, 1.1f, 0.85f).apply {
                this.duration    = durationMs
                this.startDelay  = startDelayMs
                repeatCount      = ObjectAnimator.INFINITE
                repeatMode       = ObjectAnimator.RESTART
                interpolator     = AccelerateDecelerateInterpolator()
            }
            val alpha = ObjectAnimator.ofFloat(view, "alpha", view.alpha, view.alpha * 0.4f, view.alpha).apply {
                this.duration    = durationMs
                this.startDelay  = startDelayMs
                repeatCount      = ObjectAnimator.INFINITE
                repeatMode       = ObjectAnimator.RESTART
                interpolator     = AccelerateDecelerateInterpolator()
            }
            scaleX.start()
            scaleY.start()
            alpha.start()
        }

        startPulse(ring1, 1800, 0)
        startPulse(ring2, 1800, 300)
        startPulse(ring3, 1800, 600)

        // Keep a single sentinel animator so stopScanPulseAnimation() can cancel all via tag
        scanPulseAnimator = AnimatorSet() // used only as a cancellation token; real animators are on the views
    }

    private fun stopScanPulseAnimation() {
        scanPulseAnimator?.cancel()
        scanPulseAnimator = null
        // Cancel property animators running directly on each ring view and reset to XML defaults
        listOf(R.id.pulseRing1, R.id.pulseRing2, R.id.pulseRing3).forEach { id ->
            val ring = findViewById<View>(id) ?: return@forEach
            ring.animate().cancel()
            ring.scaleX = 1f
            ring.scaleY = 1f
            // Restore original alpha values set in XML (ring1=0.5, ring2=0.3, ring3=0.2)
            ring.alpha = when (id) {
                R.id.pulseRing1 -> 0.5f
                R.id.pulseRing2 -> 0.3f
                else            -> 0.2f
            }
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
                // CR-005 FIX: ProfileManager.addProfile() returns false for duplicate names.
                // Previously the return value was ignored, showing "Card saved" even on failure.
                val saved = ProfileManager.addProfile(this, profile)
                if (saved) {
                    appViewModel.refreshProfiles()
                    Toast.makeText(this, "Card saved", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "A card with that name already exists", Toast.LENGTH_LONG).show()
                }
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
        // CR-008 FIX: delegate exclusively to nfcSessionManager which has its own null/error
        // guard. Previously we also called nfcAdapter?.disableReaderMode(this) directly,
        // creating two redundant teardown paths, one of which was unguarded.
        nfcSessionManager.disableReaderMode()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel all animators to prevent memory leaks when Activity is destroyed
        stopScanPulseAnimation()
        stopPaymentCardShimmer()
    }

    // ─── Payment card shimmer ─────────────────────────────────────────────────
    // Stronger than hero card (alpha 0 → 1 vs 0 → 0.5).
    // Same sweep-pause pattern, but shorter pause (2.5s) to feel more "armed/active".

    private fun startPaymentCardShimmer() {
        stopPaymentCardShimmer()
        schedulePaymentShineSweep(initialDelayMs = 200)
    }

    private fun schedulePaymentShineSweep(initialDelayMs: Long) {
        val r = Runnable { runPaymentShineSweep() }
        paymentShineRunnable = r
        paymentShineHandler.postDelayed(r, initialDelayMs)
    }

    private fun runPaymentShineSweep() {
        val card  = findViewById<View>(R.id.cardPaymentContainer) ?: return
        val shine = findViewById<View>(R.id.viewPaymentCardShine) ?: return

        val cardWidth  = card.width.toFloat()
        if (cardWidth <= 0f) {
            schedulePaymentShineSweep(200)
            return
        }
        val shineWidth = shine.width.toFloat()
        val startX = -shineWidth
        val endX   = cardWidth + shineWidth

        val translator = ObjectAnimator.ofFloat(shine, "translationX", startX, endX).apply {
            duration    = 1400
            interpolator = DecelerateInterpolator(1.5f)
        }
        val alphaAnim = ObjectAnimator.ofFloat(shine, "alpha", 0f, 1f, 1f, 0f).apply {
            duration    = 1400
            interpolator = AccelerateDecelerateInterpolator()
        }

        translator.addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: android.animation.Animator) { shine.alpha = 0f }
            override fun onAnimationEnd(animation: android.animation.Animator) {
                shine.alpha = 0f
                alphaAnim.cancel()
                // CR-004 FIX: only re-schedule the next sweep if the payment overlay is
                // still visible. If the overlay was dismissed while the animation was
                // running, onAnimationEnd still fires — without this guard it would
                // re-queue a sweep on an invisible / detached view.
                if (layoutPaymentOverlay.visibility == View.VISIBLE) {
                    schedulePaymentShineSweep(initialDelayMs = 2500)
                }
            }
            override fun onAnimationCancel(animation: android.animation.Animator) {
                shine.alpha = 0f
                alphaAnim.cancel()
                // Intentionally do NOT re-schedule. Cancel means we are stopping.
            }
        })

        paymentShineAnimator = translator
        alphaAnim.start()
        translator.start()
    }

    private fun stopPaymentCardShimmer() {
        paymentShineRunnable?.let { paymentShineHandler.removeCallbacks(it) }
        paymentShineRunnable = null
        paymentShineAnimator?.cancel()
        paymentShineAnimator = null
        findViewById<View>(R.id.viewPaymentCardShine)?.alpha = 0f
    }

    // BUG-002 FIX: run device capability check and show the correct blocking overlay.
    // Called from onCreate + onResume so the overlay auto-dismisses after the user
    // enables NFC in Settings and returns to the app.
    private fun checkDeviceCapabilities() {
        val audit = DeviceCapabilityManager.auditDevice(this)

        when {
            !audit.isWalletFullySupported -> {
                // Device has no NFC hardware or no HCE support — permanently incompatible
                layoutNfcDisabledOverlay.visibility = View.GONE
                layoutUnsupportedOverlay.visibility = View.VISIBLE
            }
            !audit.isNfcEnabled -> {
                // Hardware present but NFC is currently turned off
                layoutUnsupportedOverlay.visibility = View.GONE
                layoutNfcDisabledOverlay.visibility = View.VISIBLE
            }
            else -> {
                // All good — hide both gatekeeping overlays
                layoutUnsupportedOverlay.visibility = View.GONE
                layoutNfcDisabledOverlay.visibility = View.GONE
            }
        }
    }

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
}
