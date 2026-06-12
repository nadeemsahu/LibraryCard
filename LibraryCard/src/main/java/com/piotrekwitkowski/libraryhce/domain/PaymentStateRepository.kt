package com.piotrekwitkowski.libraryhce.domain

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PaymentStateRepository @Inject constructor() {
    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _authorizedCardId = MutableStateFlow<String?>(null)
    val authorizedCardId: StateFlow<String?> = _authorizedCardId.asStateFlow()

    private val _apduInteractionEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val apduInteractionEvent: SharedFlow<Unit> = _apduInteractionEvent.asSharedFlow()

    fun setAuthorized(auth: Boolean, cardId: String?) {
        _isAuthorized.value = auth
        _authorizedCardId.value = cardId
    }

    fun notifyInteraction() {
        _apduInteractionEvent.tryEmit(Unit)
    }
}
