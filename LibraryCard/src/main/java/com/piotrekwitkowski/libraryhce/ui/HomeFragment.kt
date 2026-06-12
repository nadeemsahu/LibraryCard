package com.piotrekwitkowski.libraryhce.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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

        // Defer shimmer start until the card is laid out (we need its measured width)
        cardActiveHero.post { startHeroCardShimmer() }

        return view
    }

    // ─── Hero card shimmer ────────────────────────────────────────────────────
    // Pattern: hide → reset → sweep across → hide → wait 4–6s → repeat
    // Uses a single ObjectAnimator recycled on each cycle, avoiding accumulation.

    private fun startHeroCardShimmer() {
        stopHeroCardShimmer()
        scheduleShineSweep(initialDelayMs = 800)   // first sweep after 800ms so card settles
    }

    private fun scheduleShineSweep(initialDelayMs: Long) {
        val r = Runnable { runShineSweep() }
        shineRunnable = r
        shineHandler.postDelayed(r, initialDelayMs)
    }

    private fun runShineSweep() {
        val card = cardActiveHero
        val shine = viewCardShine

        // Card width is known after layout; shine is 120dp wide, starts off-screen left
        val cardWidth = card.width.toFloat()
        if (cardWidth <= 0f) {
            // View not yet measured — retry after next frame
            scheduleShineSweep(200)
            return
        }
        val shineWidth = shine.width.toFloat()
        val startX = -shineWidth          // fully off the left edge
        val endX   = cardWidth + shineWidth // fully off the right edge

        // Fade in, sweep, fade out — all handled by a translationX animator with
        // alpha keyframed via the fraction so there's no abrupt reset flash.
        val animator = ObjectAnimator.ofFloat(shine, "translationX", startX, endX).apply {
            duration    = 1800
            interpolator = DecelerateInterpolator(1.5f)
            // Alpha is driven by a ValueAnimator listener so it's in sync with translation
        }

        // Separate alpha animator — ramp up/hold/ramp down over the same duration
        val alphaAnimator = ObjectAnimator.ofFloat(shine, "alpha", 0f, 1f, 1f, 0f).apply {
            duration    = 1800
            interpolator = AccelerateDecelerateInterpolator()
        }

        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                shine.alpha = 0f
            }
            override fun onAnimationEnd(animation: Animator) {
                shine.alpha = 0f
                alphaAnimator.cancel()
                // Schedule the next sweep after a 4–5s pause
                scheduleShineSweep(initialDelayMs = 4500)
            }
            override fun onAnimationCancel(animation: Animator) {
                shine.alpha = 0f
                alphaAnimator.cancel()
            }
        })

        shineAnimator = animator
        alphaAnimator.start()
        animator.start()
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
        // UID reveal toggle
        tvHeroCardId.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            appViewModel.setUidRevealed(!appViewModel.isUidRevealed.value)
        }

        // Hero card tap — micro-interaction: scale down on press, spring back on release
        cardActiveHero.setOnTouchListener { v, event ->
            when (event.action) {
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
                            if (event.action == MotionEvent.ACTION_UP) {
                                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                appViewModel.setPaymentEmulationActive(true)
                            }
                        }
                        .start()
                }
            }
            true  // consume — OnClickListener replaced by touch for micro-interaction
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
                layoutCardSwapper.removeAllViews()
                val active = appViewModel.activeProfile.value
                for (p in profiles) {
                    val cardView = layoutInflater.inflate(
                        R.layout.item_horizontal_card, layoutCardSwapper, false)
                    val tvMiniName    = cardView.findViewById<TextView>(R.id.tvMiniName)
                    val tvMiniId      = cardView.findViewById<TextView>(R.id.tvMiniId)
                    val borderSelected = cardView.findViewById<View>(R.id.borderSelected)

                    tvMiniName.text = p.name
                    tvMiniId.text = if (!appViewModel.isUidRevealed.value) "•••• •••• ••••" else p.libraryId

                    borderSelected.visibility =
                        if (active != null && p.name.equals(active.name, ignoreCase = true))
                            View.VISIBLE else View.GONE

                    // Mini card tap micro-interaction
                    cardView.setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN ->
                                v.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80).start()
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                                if (event.action == MotionEvent.ACTION_UP) {
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

    // ─── Lifecycle cleanup ────────────────────────────────────────────────────

    override fun onResume() {
        super.onResume()
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
