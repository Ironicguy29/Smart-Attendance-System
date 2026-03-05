package com.smartattendance.app.teacher

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.smartattendance.app.databinding.ActivitySessionManagerBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceSession
import com.smartattendance.app.utils.Constants
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat

/**
 * Teacher creates a new attendance session here.
 *
 * Steps:
 * 1. Fill in subject & duration.
 * 2. "Use my current location" → fetches GPS coords.
 * 3. "Start Session" → writes to Firestore and opens GenerateQRActivity.
 */
class SessionManagerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySessionManagerBinding
    private lateinit var fusedClient: FusedLocationProviderClient

    private var sessionLat  = 0.0
    private var sessionLon  = 0.0
    private var locationSet = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySessionManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        binding.btnGetLocation.setOnClickListener { fetchLocation() }
        binding.btnStartSession.setOnClickListener { startSession() }
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                Constants.RC_LOCATION_PERMISSION
            )
            return
        }

        binding.btnGetLocation.isEnabled = false
        binding.tvLocationStatus.text    = "Fetching location…"

        fusedClient.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                sessionLat   = loc.latitude
                sessionLon   = loc.longitude
                locationSet  = true
                binding.tvLocationStatus.text =
                    "📍 %.5f, %.5f (accuracy ±%.1f m)".format(sessionLat, sessionLon, loc.accuracy)
            } else {
                binding.tvLocationStatus.text = "Could not get location. Try again."
            }
            binding.btnGetLocation.isEnabled = true
        }.addOnFailureListener {
            binding.tvLocationStatus.text    = "Location error: ${it.message}"
            binding.btnGetLocation.isEnabled = true
        }
    }

    private fun startSession() {
        val subject  = binding.etSubject.text.toString().trim()
        val classId  = binding.etClassId.text.toString().trim()
        val durationText = binding.etDuration.text.toString().trim()

        if (subject.isEmpty()) { toast("Enter subject name"); return }
        if (!locationSet)      { toast("Set classroom location first"); return }

        val duration = durationText.toIntOrNull() ?: Constants.DEFAULT_SESSION_DURATION_MIN
        val radiusM  = binding.etRadius.text.toString().toDoubleOrNull() ?: Constants.DEFAULT_RADIUS_METERS
        val now      = System.currentTimeMillis()
        val uid      = FirebaseManager.currentUser?.uid ?: return

        val session = AttendanceSession(
            classId         = classId,
            teacherId       = uid,
            subject         = subject,
            startTime       = now,
            endTime         = now + duration * 60_000L,
            durationMinutes = duration,
            latitude        = sessionLat,
            longitude       = sessionLon,
            radiusMeters    = radiusM,
            isActive        = true
        )

        binding.btnStartSession.isEnabled = false
        binding.progressBar.visibility    = View.VISIBLE

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val sessionId = FirebaseManager.createSession(session)
                toast("Session started!")
                startActivity(
                    Intent(this@SessionManagerActivity, GenerateQRActivity::class.java).apply {
                        putExtra(Constants.EXTRA_SESSION_ID, sessionId)
                    }
                )
                finish()
            } catch (e: Exception) {
                toast("Failed to create session: ${e.message}")
                binding.btnStartSession.isEnabled = true
                binding.progressBar.visibility    = View.GONE
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == Constants.RC_LOCATION_PERMISSION &&
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            fetchLocation()
        } else {
            toast("Location permission denied")
        }
    }
}
