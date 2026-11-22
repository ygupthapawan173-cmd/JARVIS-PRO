package com.thanish.jarvispro

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds only. Register the generated debug token in Firebase Console.
 * Never ship this provider in a production build.
 */
object DebugAppCheckInitializer {
    @JvmStatic
    fun initialize(context: Context) {
        FirebaseApp.initializeApp(context.applicationContext)
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
