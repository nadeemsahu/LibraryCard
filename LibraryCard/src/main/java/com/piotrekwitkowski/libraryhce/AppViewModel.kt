package com.piotrekwitkowski.libraryhce

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

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
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val fetchedProfiles = ProfileManager.getProfiles(context)
            val fetchedActive = ProfileManager.getActiveProfile(context)
            
            _profiles.value = fetchedProfiles
            _activeProfile.value = fetchedActive
        }
    }

    fun setActiveProfile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.setActiveProfile(getApplication(), name)
            refreshProfiles()
        }
    }

    fun deleteProfile(name: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ProfileManager.deleteProfile(getApplication(), name)
            refreshProfiles()
        }
    }
}
