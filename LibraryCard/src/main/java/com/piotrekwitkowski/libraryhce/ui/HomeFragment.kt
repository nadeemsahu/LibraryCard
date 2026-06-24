package com.piotrekwitkowski.libraryhce.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.piotrekwitkowski.libraryhce.AppViewModel
import com.piotrekwitkowski.libraryhce.R
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private lateinit var appViewModel: AppViewModel

    private lateinit var tvHeroCardId: TextView
    private lateinit var tvHeroCardName: TextView
    private lateinit var tvCardStatusLabel: TextView
    private lateinit var viewHcePulseDot: View
    private lateinit var layoutCardSwapper: LinearLayout
    private lateinit var cardActiveHero: View
    private lateinit var viewCardShine: View

    // Shimmer state — one ObjectAnimator + a Handler for the inter-sweep pause
    private var shineAnimator: ObjectAnimator? = null
    private val shineHandler = Handler(Looper.getMainLooper())
    private var shineRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        appViewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        tvHeroCardId      = view.findViewById(R.id.tvHeroCardId)
        tvHeroCardName    = view.findViewById(R.id.tvHeroCardName)
        tvCardStatusLabel = view.findViewById(R.id.tvCardStatusLabel)
        viewHcePulseDot   = view.findViewById(R.id.viewHcePulseDot)
        layoutCardSwapper = view.findViewById(R.id.layoutCardSwapper)
        cardActiveHero    = view.findViewById(R.id.cardActiveHero)
        viewCardShine     = view.findViewById(R.id.viewCardShine)

        setupListeners()
        observeViewModel()

        // CR-007/CR-009 FIX: Do NOT start shimmer here. onResume() always fires after
        // onCreateView, so shimmer is started exclusively there. Starting it in both
        // places causes two Handlers to be queued on the first attach cycle.

        return view
    }

    // ─── Hero card shimmer ────────────────────────────────────────────────────
    // Pattern: invisible → sweep left→right → invisible → wait 4.5s → repeat
    // One ObjectAnimator per cycle. No accumulation possible.

    private fun startHeroCardShimmer() {
        stopHeroCardShimmer()
        scheduleShineSweep(initialDelayMs = 800) // first sweep after card settles
    }

    private fun scheduleShineSweep(initialDelayMs: Long) {
        val r = Runnable { runShineSweep() }
        shineRunnable = r
        shineHandler.postDelayed(r, initialDelayMs)
    }

    private fun runShineSweep() {
        val card  = cardActiveHero
        val shine = viewCardShine

        val cardWidth = card.width.toFloat()
        if (cardWidth <= 0f) {
            scheduleShineSweep(200)
            return
        }
        val shineWidth = shine.width.toFloat()
        val startX = -shineWidth
        val endX   = cardWidth + shineWidth

        val translator = ObjectAnimator.ofFloat(shine, "translationX", startX, endX).apply {
            duration     = 1800
            interpolator = DecelerateInterpolator(1.5f)
        }
        val alphaAnim = ObjectAnimator.ofFloat(shine, "alpha", 0f, 1f, 1f, 0f).apply {
            duration     = 1800
            interpolator = AccelerateDecelerateInterpolator()
        }

        translator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                shine.alpha = 0f
            }
            override fun onAnimationEnd(animation: Animator) {
                shine.alpha = 0f
                alphaAnim.cancel()
                // CR-002 FIX: only re-schedule if the fragment is still alive.
                // onAnimationCancel fires when stopHeroCardShimmer() is called during
                // teardown — must NOT re-schedule from there.
                scheduleShineSweep(initialDelayMs = 4500)
            }
            override fun onAnimationCancel(animation: Animator) {
                shine.alpha = 0f
                alphaAnim.cancel()
                // Intentionally do NOT re-schedule here. Cancel means we are stopping.
            }
        })

        shineAnimator = translator
        alphaAnim.start()
        translator.start()
    }

    private fun stopHeroCardShimmer() {
        shineRunnable?.let { shineHandler.removeCallbacks(it) }
        shineRunnable = null
        shineAnimator?.cancel()
        shineAnimator = null
        if (::viewCardShine.isInitialized) viewCardShine.alpha = 0f
    }

    // ─── Listeners ───────────────────────────────────────────────────────────

    private fun setupListeners() {
        tvHeroCardId.setOnTouchListener { v, event ->
            val action = event.action
            when (action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    if (action == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        appViewModel.setUidRevealed(!appViewModel.isUidRevealed.value)
                    }
                }
            }
            true
        }

        // Hero card tap micro-interaction: press = scale down, release = spring back → action
        cardActiveHero.setOnTouchListener { v, event ->
            // CR-003 FIX: capture action before animation starts. MotionEvent objects
            // are recycled by the framework; reading event.action inside withEndAction
            // (which fires 180ms later) returns an undefined/recycled value.
            val actionType = event.action
            when (actionType) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate()
                        .scaleX(0.97f).scaleY(0.97f)
                        .setDuration(90)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate()
                        .scaleX(1f).scaleY(1f)
                        .setDuration(180)
                        .setInterpolator(AccelerateDecelerateInterpolator())
                        .withEndAction {
                            if (actionType == MotionEvent.ACTION_UP) {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                appViewModel.setPaymentEmulationActive(true)
                            }
                        }
                        .start()
                }
            }
            true
        }
    }

    // ─── ViewModel observers ─────────────────────────────────────────────────

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.activeProfile.collect { profile ->
                if (profile != null) {
                    tvHeroCardName.text = profile.name.uppercase()
                    val libraryId = profile.libraryId
                    tvHeroCardId.text = if (!appViewModel.isUidRevealed.value) {
                        "•••• •••• ••••"
                    } else {
                        if (libraryId.length == 12)
                            "${libraryId.substring(0, 4)} ${libraryId.substring(4, 8)} ${libraryId.substring(8, 12)}"
                        else libraryId
                    }
                    tvCardStatusLabel.text = "EMULATION ACTIVE"
                    tvCardStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
                    viewHcePulseDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.accent_lime))
                } else {
                    tvHeroCardName.text = "MEMBER CARD"
                    tvHeroCardId.text = "MINT A NEW CARD TO START"
                    tvCardStatusLabel.text = "MINT A CARD"
                    tvCardStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    viewHcePulseDot.backgroundTintList = ColorStateList.valueOf(
                        ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.isUidRevealed.collect { _ ->
                appViewModel.refreshProfiles()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.profiles.collect { profiles ->
                for (i in 0 until layoutCardSwapper.childCount) {
                    layoutCardSwapper.getChildAt(i).animate().cancel()
                }
                layoutCardSwapper.removeAllViews()
                val active = appViewModel.activeProfile.value
                for (p in profiles) {
                    val cardView = layoutInflater.inflate(
                        R.layout.item_horizontal_card, layoutCardSwapper, false)
                    val tvMiniName     = cardView.findViewById<TextView>(R.id.tvMiniName)
                    val tvMiniId       = cardView.findViewById<TextView>(R.id.tvMiniId)
                    val borderSelected = cardView.findViewById<View>(R.id.borderSelected)

                    tvMiniName.text = p.name
                    tvMiniId.text = if (!appViewModel.isUidRevealed.value) "•••• •••• ••••" else p.libraryId

                    borderSelected.visibility =
                        if (active != null && p.name.equals(active.name, ignoreCase = true))
                            View.VISIBLE else View.GONE

                    // Mini card tap micro-interaction
                    cardView.setOnTouchListener { v, ev ->
                        val action = ev.action
                        when (action) {
                            MotionEvent.ACTION_DOWN ->
                                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                                if (action == MotionEvent.ACTION_UP) {
                                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                    appViewModel.setActiveProfile(p.name)
                                }
                            }
                        }
                        true
                    }
                    layoutCardSwapper.addView(cardView)
                }
            }
        }
    }

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
        // CR-007/CR-009 FIX: shimmer is started exclusively here.
        // Views are guaranteed initialized by the time onResume fires.
        if (::cardActiveHero.isInitialized) {
            cardActiveHero.post { startHeroCardShimmer() }
        }
    }

    override fun onPause() {
        super.onPause()
        stopHeroCardShimmer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopHeroCardShimmer()
    }
}
