package com.piotrekwitkowski.libraryhce

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LibraryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
    }
}
