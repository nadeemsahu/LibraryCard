package com.piotrekwitkowski.libraryhce

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val _isClonerActive = MutableStateFlow(false)
    val isClonerActive: StateFlow<Boolean> = _isClonerActive.asStateFlow()

    private val _isPaymentEmulationActive = MutableStateFlow(false)
    val isPaymentEmulationActive: StateFlow<Boolean> = _isPaymentEmulationActive.asStateFlow()

    private val _profiles = MutableStateFlow<List<ProfileManager.CardProfile>>(emptyList())
    val profiles: StateFlow<List<ProfileManager.CardProfile>> = _profiles.asStateFlow()

    private val _activeProfile = MutableStateFlow<ProfileManager.CardProfile?>(null)
    val activeProfile: StateFlow<ProfileManager.CardProfile?> = _activeProfile.asStateFlow()

    private val _isUidRevealed = MutableStateFlow(false)
    val isUidRevealed: StateFlow<Boolean> = _isUidRevealed.asStateFlow()

    init {
        refreshProfiles()
    }

    fun setClonerActive(active: Boolean) {
        _isClonerActive.value = active
    }

    fun setPaymentEmulationActive(active: Boolean) {
        _isPaymentEmulationActive.value = active
    }

    fun setUidRevealed(revealed: Boolean) {
        _isUidRevealed.value = revealed
    }

    fun refreshProfiles() {
        val context = getApplication<Application>()
        _profiles.value = ProfileManager.getProfiles(context)
        _activeProfile.value = ProfileManager.getActiveProfile(context)
    }

    fun setActiveProfile(name: String) {
        ProfileManager.setActiveProfile(getApplication(), name)
        refreshProfiles()
    }

    fun deleteProfile(name: String) {
        ProfileManager.deleteProfile(getApplication(), name)
        refreshProfiles()
    }
}
