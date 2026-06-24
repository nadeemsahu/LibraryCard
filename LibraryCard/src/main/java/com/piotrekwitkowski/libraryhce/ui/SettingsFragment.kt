package com.piotrekwitkowski.libraryhce.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.piotrekwitkowski.libraryhce.R

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val cardSettingsInfo = view.findViewById<View>(R.id.cardSettingsInfo)
        cardSettingsInfo.setOnTouchListener { v, ev ->
            val action = ev.action
            when (action) {
                MotionEvent.ACTION_DOWN ->
                    v.animate().scaleX(0.97f).scaleY(0.97f).setDuration(80).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    if (action == MotionEvent.ACTION_UP) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                }
            }
            true
        }
        return view
    }
}
