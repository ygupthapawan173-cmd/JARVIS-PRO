package com.thanish.jarvispro

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * Debug builds only. The generated debug token must be registered in Firebase Console.
 * Never ship the debug provider in a production build.
 */
object DebugAppCheckInitializer {
    @JvmStatic
    fun initialize(context: Context) {
        Firebase.initialize(context = context.applicationContext)
        Firebase.appCheck.installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
