package com.example.chatapp

import android.app.Application
import com.example.chatapp.ui.moth.MothBackgroundSync
import com.google.firebase.FirebaseApp
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ChatApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            // Ignore if already initialized
        }
        MothBackgroundSync.scheduleWorkerIfNeeded(this)
    }
}
