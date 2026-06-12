package com.piotrekwitkowski.libraryhce.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.piotrekwitkowski.libraryhce.AppViewModel
import com.piotrekwitkowski.libraryhce.DeviceCapabilityManager
import com.piotrekwitkowski.libraryhce.R
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {
    private lateinit var appViewModel: AppViewModel

    private lateinit var tvHeroCardId: TextView
    private lateinit var tvHeroCardName: TextView
    private lateinit var tvCardStatusLabel: TextView
    private lateinit var viewHcePulseDot: View
    private lateinit var tvHomeNfcStatus: TextView
    private lateinit var btnHomeEnableNfc: Button
    private lateinit var layoutCardSwapper: LinearLayout
    private lateinit var cardActiveHero: View

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        appViewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        tvHeroCardId = view.findViewById(R.id.tvHeroCardId)
        tvHeroCardName = view.findViewById(R.id.tvHeroCardName)
        tvCardStatusLabel = view.findViewById(R.id.tvCardStatusLabel)
        viewHcePulseDot = view.findViewById(R.id.viewHcePulseDot)
        tvHomeNfcStatus = view.findViewById(R.id.tvHomeNfcStatus)
        btnHomeEnableNfc = view.findViewById(R.id.btnHomeEnableNfc)
        layoutCardSwapper = view.findViewById(R.id.layoutCardSwapper)
        cardActiveHero = view.findViewById(R.id.cardActiveHero)

        setupListeners()
        observeViewModel()

        return view
    }

    private fun setupListeners() {
        btnHomeEnableNfc.setOnClickListener {
            val intent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS)
            startActivity(intent)
        }

        tvHeroCardId.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val current = appViewModel.isUidRevealed.value
            if (!current) {
                // In a real refactor, we would trigger biometric auth here
                // For now, we'll just reveal
                appViewModel.setUidRevealed(true)
            } else {
                appViewModel.setUidRevealed(false)
            }
        }

        cardActiveHero.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            appViewModel.setPaymentEmulationActive(true)
        }
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.activeProfile.collect { profile ->
                if (profile != null) {
                    tvHeroCardName.text = profile.name.uppercase()
                    val libraryId = profile.libraryId
                    if (!appViewModel.isUidRevealed.value) {
                        tvHeroCardId.text = "•••• •••• ••••"
                    } else {
                        if (libraryId.length == 12) {
                            tvHeroCardId.text = "${libraryId.substring(0, 4)} ${libraryId.substring(4, 8)} ${libraryId.substring(8, 12)}"
                        } else {
                            tvHeroCardId.text = libraryId
                        }
                    }
                    tvCardStatusLabel.text = "EMULATION ACTIVE"
                    tvCardStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
                    viewHcePulseDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_lime))
                } else {
                    tvHeroCardName.text = "MEMBER CARD"
                    tvHeroCardId.text = "MINT A NEW CARD TO START"
                    tvCardStatusLabel.text = "MINT A CARD"
                    tvCardStatusLabel.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                    viewHcePulseDot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.text_secondary))
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.isUidRevealed.collect { revealed ->
                // Force re-emit of active profile to update UI
                val current = appViewModel.activeProfile.value
                appViewModel.refreshProfiles() // Lazy way to trigger re-render
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.profiles.collect { profiles ->
                layoutCardSwapper.removeAllViews()
                val active = appViewModel.activeProfile.value
                for (p in profiles) {
                    val cardView = layoutInflater.inflate(R.layout.item_horizontal_card, layoutCardSwapper, false)
                    val tvMiniName = cardView.findViewById<TextView>(R.id.tvMiniName)
                    val tvMiniId = cardView.findViewById<TextView>(R.id.tvMiniId)
                    val borderSelected = cardView.findViewById<View>(R.id.borderSelected)

                    tvMiniName.text = p.name
                    if (!appViewModel.isUidRevealed.value) {
                        tvMiniId.text = "•••• •••• ••••"
                    } else {
                        tvMiniId.text = p.libraryId
                    }

                    if (active != null && p.name.equals(active.name, ignoreCase = true)) {
                        borderSelected.visibility = View.VISIBLE
                    } else {
                        borderSelected.visibility = View.GONE
                    }

                    cardView.setOnClickListener { v ->
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        appViewModel.setActiveProfile(p.name)
                    }

                    layoutCardSwapper.addView(cardView)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkNfcStatus()
    }

    private fun checkNfcStatus() {
        val audit = DeviceCapabilityManager.auditDevice(requireContext())
        if (!audit.isWalletFullySupported || !audit.isNfcEnabled) {
            tvHomeNfcStatus.text = "NFC is Disabled or Unsupported"
            tvHomeNfcStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_error))
            btnHomeEnableNfc.visibility = View.VISIBLE
        } else {
            tvHomeNfcStatus.text = "NFC hardware: Active & Emulating"
            tvHomeNfcStatus.setTextColor(ContextCompat.getColor(requireContext(), R.color.state_success))
            btnHomeEnableNfc.visibility = View.GONE
        }
    }
}
