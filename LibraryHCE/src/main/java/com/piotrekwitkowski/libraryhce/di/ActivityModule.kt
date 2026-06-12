package com.piotrekwitkowski.libraryhce.di

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.piotrekwitkowski.libraryhce.BiometricAuthenticator
import com.piotrekwitkowski.libraryhce.NfcSessionManager
import com.piotrekwitkowski.libraryhce.domain.Authenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

@Module
@InstallIn(ActivityComponent::class)
object ActivityModule {

    @Provides
    @ActivityScoped
    fun provideAuthenticator(activity: android.app.Activity): Authenticator {
        return BiometricAuthenticator(activity as AppCompatActivity)
    }

    @Provides
    @ActivityScoped
    fun provideNfcSessionManager(activity: android.app.Activity): NfcSessionManager {
        return NfcSessionManager(activity)
    }
}
