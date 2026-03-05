package com.smartattendance.app.utils

import android.content.Context
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar
import java.text.SimpleDateFormat
import java.util.*

/**
 * Top-level extension functions — importable individually, callable naturally.
 *
 * Usage in Activities / Fragments:
 *   import com.smartattendance.app.utils.toast
 *   toast("Hello")
 */

// ── Toast helpers ─────────────────────────────────────────────────────────────
fun Context.toast(msg: String) =
    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

fun Context.longToast(msg: String) =
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

// ── Snackbar ──────────────────────────────────────────────────────────────────
fun View.snack(msg: String, duration: Int = Snackbar.LENGTH_LONG) =
    Snackbar.make(this, msg, duration).show()

// ── Visibility ────────────────────────────────────────────────────────────────
fun View.show()      { visibility = View.VISIBLE   }
fun View.hide()      { visibility = View.GONE      }
fun View.invisible() { visibility = View.INVISIBLE }

// ── Date / time formatting ────────────────────────────────────────────────────
private val dateTimeFmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
private val timeFmt     = SimpleDateFormat("hh:mm:ss a",           Locale.getDefault())
private val dateFmt     = SimpleDateFormat("dd MMM yyyy",          Locale.getDefault())

fun Long.toDateTimeString(): String = dateTimeFmt.format(Date(this))
fun Long.toTimeString():     String = timeFmt.format(Date(this))
fun Long.toDateString():     String = dateFmt.format(Date(this))

/** Convert milliseconds remaining into "MM:SS" display string. */
fun Long.toCountdownString(): String {
    val total = this / 1_000L
    val mins  = total / 60
    val secs  = total % 60
    return "%02d:%02d".format(mins, secs)
}

// ── Dialog helper ─────────────────────────────────────────────────────────────
fun Context.showAlert(
    title: String,
    message: String,
    positiveText: String = "OK",
    negativeText: String? = null,
    onPositive: (() -> Unit)? = null,
    onNegative: (() -> Unit)? = null
) {
    val builder = AlertDialog.Builder(this)
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(positiveText) { _, _ -> onPositive?.invoke() }
    if (negativeText != null)
        builder.setNegativeButton(negativeText) { _, _ -> onNegative?.invoke() }
    builder.show()
}

// ── Attendance percentage color ───────────────────────────────────────────────
fun attendanceColor(percentage: Float): Int = when {
    percentage >= 75f -> android.graphics.Color.parseColor("#4CAF50")
    percentage >= 50f -> android.graphics.Color.parseColor("#FF9800")
    else              -> android.graphics.Color.parseColor("#F44336")
}

