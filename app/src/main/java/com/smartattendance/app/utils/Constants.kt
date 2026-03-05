package com.smartattendance.app.utils

/**
 * App-wide constants — single source of truth.
 */
object Constants {

    // ── QR Token settings ────────────────────────────────────────────────────
    const val QR_TOKEN_VALIDITY_MS      = 10_000L   // token refreshes every 10 s
    const val QR_IMAGE_SIZE_PX          = 800       // generated QR bitmap size

    // ── Session defaults ─────────────────────────────────────────────────────
    const val DEFAULT_SESSION_DURATION_MIN  = 2
    const val DEFAULT_RADIUS_METERS         = 50.0

    // ── GPS ───────────────────────────────────────────────────────────────────
    const val LOCATION_UPDATE_INTERVAL_MS   = 5_000L
    const val LOCATION_FASTEST_INTERVAL_MS  = 2_000L
    const val LOCATION_MAX_AGE_MS           = 30_000L   // reject stale fixes older than 30 s

    // ── SharedPreferences keys ────────────────────────────────────────────────
    const val PREF_FILE                     = "smart_attendance_prefs"
    const val PREF_USER_ROLE                = "user_role"
    const val PREF_USER_UID                 = "user_uid"
    const val PREF_DEVICE_ID                = "device_id"

    // ── Intent extras ─────────────────────────────────────────────────────────
    const val EXTRA_SESSION_ID              = "extra_session_id"
    const val EXTRA_CLASS_ID                = "extra_class_id"
    const val EXTRA_USER_ROLE               = "extra_user_role"
    const val EXTRA_QR_TOKEN                = "extra_qr_token"

    // ── Firestore field names ──────────────────────────────────────────────────
    const val FIELD_IS_ACTIVE               = "isActive"
    const val FIELD_PRESENT_COUNT           = "presentCount"

    // ── Request codes ─────────────────────────────────────────────────────────
    const val RC_CAMERA_PERMISSION          = 1001
    const val RC_LOCATION_PERMISSION        = 1002
    const val RC_QR_SCAN                    = 2001

    // ── Error messages ────────────────────────────────────────────────────────
    const val ERR_SESSION_EXPIRED           = "Session has expired. Attendance window closed."
    const val ERR_OUTSIDE_RADIUS            = "You are outside the allowed classroom radius."
    const val ERR_DUPLICATE                 = "Attendance already marked for this session."
    const val ERR_INVALID_TOKEN             = "QR code is invalid or has expired."
    const val ERR_LOCATION_UNAVAILABLE      = "Unable to fetch your location. Enable GPS."
    const val ERR_NETWORK                   = "Network error. Please check your connection."
}
