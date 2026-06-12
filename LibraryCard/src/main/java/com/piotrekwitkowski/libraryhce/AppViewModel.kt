package com.piotrekwitkowski.libraryhce

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppViewModel : ViewModel() {
    private val _isClonerActive = MutableStateFlow(false)
    val isClonerActive: StateFlow<Boolean> = _isClonerActive.asStateFlow()

    private val _isPaymentEmulationActive = MutableStateFlow(false)
    val isPaymentEmulationActive: StateFlow<Boolean> = _isPaymentEmulationActive.asStateFlow()

    fun setClonerActive(active: Boolean) {
        _isClonerActive.value = active
    }

    fun setPaymentEmulationActive(active: Boolean) {
        _isPaymentEmulationActive.value = active
    }
}
