package com.smartattendance.app

import android.app.Application
import android.os.Build
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions

/**
 * Custom Application class.
 * Ensures Firebase is initialized before any Activity runs.
 *
 * USE_EMULATOR = true  → points to local emulator (no billing needed)
 * USE_EMULATOR = false → points to real Firebase (needs Blaze plan)
 */
class SmartAttendanceApp : Application() {

    companion object {
        // Set to false when you have real Firebase with billing enabled
        const val USE_EMULATOR = true

        // Mac's Wi-Fi IP for physical device testing
        private const val MAC_IP = "192.168.31.206"

        /** Returns true when running inside the Android emulator */
        fun isEmulator(): Boolean =
            Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.startsWith("unknown") ||
            Build.MODEL.contains("Emulator") ||
            Build.MODEL.contains("Android SDK built for x86") ||
            Build.MANUFACTURER.contains("Genymotion") ||
            Build.BRAND.startsWith("generic") ||
            Build.DEVICE.startsWith("generic") ||
            Build.PRODUCT.contains("sdk")

        /** Host to use for Firebase emulator connections */
        fun emulatorHost(): String = if (isEmulator()) "10.0.2.2" else MAC_IP
    }

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

        if (USE_EMULATOR) {
            val host = emulatorHost()
            FirebaseFirestore.getInstance().useEmulator(host, 8080)
            FirebaseAuth.getInstance().useEmulator(host, 9099)
            FirebaseFunctions.getInstance().useEmulator(host, 5001)
        }
    }
}
