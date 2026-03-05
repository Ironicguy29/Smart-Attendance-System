package com.smartattendance.app.student

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.smartattendance.app.databinding.ActivityScanQrBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.utils.Constants
import com.smartattendance.app.utils.DeviceIdHelper
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toast
import com.smartattendance.app.utils.GPSLocationHelper
import com.smartattendance.app.utils.QRCodeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Full-screen QR scanner for students.
 *
 * Security flow:
 *  1. Parse QR → extract QRToken
 *  2. Client-side expiry check (quick feedback)
 *  3. Fetch GPS location
 *  4. Call Cloud Function verifyAndMarkAttendance with:
 *       { sessionId, token, timestamp, studentLat, studentLon, studentId, deviceId }
 *  5. Show result to student
 */
class ScanQRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityScanQrBinding
    private lateinit var fusedClient: FusedLocationProviderClient

    private var isProcessing = false   // prevent double-scan

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityScanQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        checkPermissionsAndStartScan()
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun checkPermissionsAndStartScan() {
        val perms = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
        val missing = perms.filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startScanner()
        } else {
            ActivityCompat.requestPermissions(this, missing.toTypedArray(), Constants.RC_CAMERA_PERMISSION)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.RC_CAMERA_PERMISSION &&
            grantResults.all { it == PackageManager.PERMISSION_GRANTED }
        ) {
            startScanner()
        } else {
            toast("Camera and Location permissions are required")
            finish()
        }
    }

    // ── ZXing scanner ─────────────────────────────────────────────────────────

    private fun startScanner() {
        binding.barcodeView.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(result: BarcodeResult) {
                if (isProcessing) return
                isProcessing = true
                binding.barcodeView.pause()
                handleScanResult(result.text)
            }
        })
    }

    // ── Scan handling ─────────────────────────────────────────────────────────

    private fun handleScanResult(raw: String) {
        val token = QRCodeGenerator.parseToken(raw)
        if (token == null) {
            showError(Constants.ERR_INVALID_TOKEN)
            return
        }

        // Quick client-side expiry check
        if (QRCodeGenerator.isTokenExpired(token)) {
            showError(Constants.ERR_INVALID_TOKEN + " (expired)")
            return
        }

        binding.progressBar.show()
        binding.tvStatus.text = "Verifying location…"

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val location = getLocation()
                if (location == null) {
                    showError(Constants.ERR_LOCATION_UNAVAILABLE)
                    return@launch
                }

                val uid      = FirebaseManager.currentUser?.uid ?: return@launch
                val student  = FirebaseManager.getStudent(uid)
                val deviceId = DeviceIdHelper.getDeviceId(this@ScanQRActivity)

                binding.tvStatus.text = "Submitting attendance…"

                val payload = mapOf(
                    "sessionId"   to token.sessionId,
                    "token"       to token.token,
                    "timestamp"   to token.timestamp,
                    "studentId"   to uid,
                    "studentName" to (student?.name ?: ""),
                    "rollNo"      to (student?.rollNo ?: ""),
                    "studentLat"  to location.latitude,
                    "studentLon"  to location.longitude,
                    "deviceId"    to deviceId
                )

                val result = FirebaseManager.markAttendance(payload)
                val success = result["success"] as? Boolean ?: false
                val message = result["message"] as? String ?: "Unknown response"

                if (success) {
                    showSuccess(message)
                } else {
                    showError(message)
                }

            } catch (e: Exception) {
                showError(e.message ?: Constants.ERR_NETWORK)
            }
        }
    }

    // ── Location helper ───────────────────────────────────────────────────────

    private suspend fun getLocation(): Location? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) return null

        return try {
            @Suppress("MissingPermission")
            val loc = fusedClient.lastLocation.await()
            if (loc != null && GPSLocationHelper.isLocationFresh(loc)) loc else null
        } catch (e: Exception) { null }
    }

    // ── UI feedback ───────────────────────────────────────────────────────────

    private fun showSuccess(message: String) {
        binding.progressBar.hide()
        binding.tvStatus.text = "✅ $message"
        binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#4CAF50"))
        binding.btnRescan.visibility = View.GONE

        // Auto-close after 2 s
        binding.root.postDelayed({ finish() }, 2000)
    }

    private fun showError(message: String) {
        binding.progressBar.hide()
        binding.tvStatus.text = "❌ $message"
        binding.tvStatus.setTextColor(android.graphics.Color.parseColor("#F44336"))
        binding.btnRescan.visibility = View.VISIBLE
        binding.btnRescan.setOnClickListener {
            isProcessing = false
            binding.tvStatus.text = "Scanning…"
            binding.tvStatus.setTextColor(android.graphics.Color.WHITE)
            binding.btnRescan.visibility = View.GONE
            binding.barcodeView.resume()
        }
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onResume()  { super.onResume();  binding.barcodeView.resume() }
    override fun onPause()   { super.onPause();   binding.barcodeView.pause()  }
    override fun onDestroy() { super.onDestroy(); binding.barcodeView.pause()  }
}
