package se.wmuth.openc25k

import android.app.Application
import timber.log.Timber

class OpenC25kApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Timber for logging
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        Timber.d("OpenC25k Application created")
    }
}
