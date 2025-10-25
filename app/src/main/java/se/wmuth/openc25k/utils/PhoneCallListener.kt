@file:Suppress("DEPRECATION")

package se.wmuth.openc25k.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import timber.log.Timber
import java.util.concurrent.Executor

/**
 * Listens for phone call state changes and notifies callback
 *
 * Handles both legacy PhoneStateListener (API < 31) and new TelephonyCallback (API 31+)
 */
class PhoneCallListener(
    private val context: Context,
    private val onCallStateChanged: (isInCall: Boolean) -> Unit
) {
    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // For API < 31
    @Suppress("DEPRECATION")
    private val legacyPhoneStateListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            handleCallStateChange(state)
        }
    }

    // For API >= 31
    @RequiresApi(Build.VERSION_CODES.S)
    private val telephonyCallback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            handleCallStateChange(state)
        }
    }

    private var isRegistered = false

    /**
     * Start listening for phone call state changes
     *
     * Requires READ_PHONE_STATE permission - silently fails if not granted
     */
    fun startListening() {
        if (isRegistered) {
            Timber.w("PhoneCallListener already registered")
            return
        }

        // Check for READ_PHONE_STATE permission
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Timber.w("READ_PHONE_STATE permission not granted, phone call auto-pause disabled")
            return
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Use new TelephonyCallback API for Android 12+
                telephonyManager.registerTelephonyCallback(
                    context.mainExecutor,
                    telephonyCallback
                )
            } else {
                // Use legacy PhoneStateListener for older versions
                @Suppress("DEPRECATION")
                telephonyManager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
            }

            isRegistered = true
            Timber.d("PhoneCallListener started successfully")
        } catch (e: SecurityException) {
            Timber.e(e, "Failed to register phone call listener due to security exception")
        }
    }

    /**
     * Stop listening for phone call state changes
     */
    fun stopListening() {
        if (!isRegistered) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            telephonyManager.unregisterTelephonyCallback(telephonyCallback)
        } else {
            @Suppress("DEPRECATION")
            telephonyManager.listen(legacyPhoneStateListener, PhoneStateListener.LISTEN_NONE)
        }

        isRegistered = false
        Timber.d("PhoneCallListener stopped")
    }

    /**
     * Handle call state changes
     */
    private fun handleCallStateChange(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_RINGING,
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Phone is ringing or in call
                Timber.d("Phone call detected, pausing run")
                onCallStateChanged(true)
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                // Call ended
                Timber.d("Phone call ended")
                onCallStateChanged(false)
            }
        }
    }
}
