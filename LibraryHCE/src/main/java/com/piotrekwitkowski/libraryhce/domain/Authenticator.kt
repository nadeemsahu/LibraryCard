package com.piotrekwitkowski.libraryhce.domain

interface Authenticator {
    interface AuthCallback {
        fun onSuccess()
        fun onFailed(reason: String)
        fun onCancelled()
    }

    fun authenticate(
        title: String,
        subtitle: String,
        negativeButtonText: String?,
        allowedAuthenticators: Int,
        callback: AuthCallback
    )
}
