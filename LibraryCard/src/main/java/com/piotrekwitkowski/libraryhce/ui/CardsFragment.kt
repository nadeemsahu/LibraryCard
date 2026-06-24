package com.piotrekwitkowski.libraryhce.ui

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
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
import com.piotrekwitkowski.libraryhce.R
import kotlinx.coroutines.launch

class CardsFragment : Fragment() {
    private lateinit var appViewModel: AppViewModel
    
    private lateinit var layoutVerticalCards: LinearLayout
    private lateinit var layoutEmptyState: LinearLayout

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        appViewModel = ViewModelProvider(requireActivity())[AppViewModel::class.java]
        val view = inflater.inflate(R.layout.fragment_cards, container, false)
        layoutVerticalCards = view.findViewById(R.id.layoutVerticalCards)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        observeViewModel()
        return view
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            appViewModel.profiles.collect { profiles ->
                layoutVerticalCards.removeAllViews()
                val active = appViewModel.activeProfile.value

                for (p in profiles) {
                    val cardItem = layoutInflater.inflate(R.layout.item_vertical_card_profile, layoutVerticalCards, false)
                    val tvProfileName = cardItem.findViewById<TextView>(R.id.tvProfileName)
                    val tvProfileId = cardItem.findViewById<TextView>(R.id.tvProfileId)
                    val btnSetAsActive = cardItem.findViewById<Button>(R.id.btnSetAsActive)
                    val btnDeleteCard = cardItem.findViewById<View>(R.id.btnDeleteCard)

                    tvProfileName.text = p.name
                    if (!appViewModel.isUidRevealed.value) {
                        tvProfileId.text = "ID: •••• •••• ••••"
                    } else {
                        tvProfileId.text = "ID: " + p.libraryId
                    }

                    if (active != null && p.name.equals(active.name, ignoreCase = true)) {
                        btnSetAsActive.text = "ACTIVE"
                        btnSetAsActive.isEnabled = false
                        btnSetAsActive.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.bg_card))
                        btnSetAsActive.setTextColor(ContextCompat.getColor(requireContext(), R.color.accent_lime))
                        btnDeleteCard.visibility = View.GONE
                    } else {
                        btnSetAsActive.text = "ACTIVATE"
                        btnSetAsActive.isEnabled = true
                        btnSetAsActive.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.accent_lime))
                        btnSetAsActive.setTextColor(ContextCompat.getColor(requireContext(), R.color.bg_surface))
                        btnSetAsActive.setOnTouchListener { v, ev ->
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

                        btnDeleteCard.visibility = View.VISIBLE
                        btnDeleteCard.setOnClickListener {
                            androidx.appcompat.app.AlertDialog.Builder(requireContext(), R.style.CustomDialogTheme)
                                .setTitle("Remove Card")
                                .setMessage("Remove \"${p.name}\" from your collection?")
                                .setPositiveButton("Remove") { _, _ ->
                                    appViewModel.deleteProfile(p.name)
                                }
                                .setNegativeButton("Cancel", null)
                                .show()
                        }
                    }

                    layoutVerticalCards.addView(cardItem)
                }

                layoutEmptyState.visibility = if (profiles.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }
}
