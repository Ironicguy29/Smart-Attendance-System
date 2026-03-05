package com.smartattendance.app.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlin.math.*

/**
 * Wraps Fused Location Provider into coroutine-friendly helpers.
 *
 * Usage:
 *  val helper = GPSLocationHelper(context)
 *  val location = helper.getLastLocation()          // one-shot
 *  helper.locationFlow().collect { loc -> … }       // stream
 */
class GPSLocationHelper(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // ── Permission check ─────────────────────────────────────────────────────

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED

    // ── One-shot last known location ─────────────────────────────────────────

    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        if (!hasLocationPermission()) return null
        return try {
            fusedClient.lastLocation.await()
        } catch (e: Exception) {
            null
        }
    }

    // ── Continuous location flow ──────────────────────────────────────────────

    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<Location> = callbackFlow {
        if (!hasLocationPermission()) {
            close(SecurityException("Location permission not granted"))
            return@callbackFlow
        }

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.LOCATION_UPDATE_INTERVAL_MS
        )
            .setMinUpdateIntervalMillis(Constants.LOCATION_FASTEST_INTERVAL_MS)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { trySend(it) }
            }
        }

        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose { fusedClient.removeLocationUpdates(callback) }
    }

    // ── Distance / radius helpers ────────────────────────────────────────────

    companion object {
        /**
         * Calculate distance between two lat/lng points using the Haversine formula.
         * Returns distance in **metres**.
         */
        fun distanceMeters(
            lat1: Double, lon1: Double,
            lat2: Double, lon2: Double
        ): Double {
            val earthRadius = 6_371_000.0   // metres
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            return earthRadius * c
        }

        /**
         * Returns true if the student is within [radiusMeters] of the classroom.
         */
        fun isWithinRadius(
            studentLat: Double, studentLon: Double,
            classroomLat: Double, classroomLon: Double,
            radiusMeters: Double = Constants.DEFAULT_RADIUS_METERS
        ): Boolean {
            val dist = distanceMeters(studentLat, studentLon, classroomLat, classroomLon)
            return dist <= radiusMeters
        }

        /**
         * Check that a location fix is recent enough to be trusted.
         */
        fun isLocationFresh(location: Location): Boolean {
            val ageMs = System.currentTimeMillis() - location.time
            return ageMs <= Constants.LOCATION_MAX_AGE_MS
        }
    }
}
