package com.smartattendance.app.teacher

import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.smartattendance.app.databinding.ActivityGenerateQrBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceSession
import com.smartattendance.app.utils.Constants
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toCountdownString
import com.smartattendance.app.utils.toast
import com.smartattendance.app.utils.QRCodeGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Displays a dynamic QR code that refreshes every 10 seconds.
 *
 * Also shows:
 * • Session countdown timer (until window closes)
 * • Token countdown (10 s QR rotation)
 * • Live present-student count
 * • "End Session" button
 */
class GenerateQRActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGenerateQrBinding

    private var sessionId: String  = ""
    private var session: AttendanceSession? = null

    // ── Timers ────────────────────────────────────────────────────────────────
    private var sessionCountdown: CountDownTimer? = null   // 2-minute window
    private val qrHandler = Handler(Looper.getMainLooper())
    private val qrRefreshRunnable = object : Runnable {
        override fun run() {
            generateNewQRCode()
            qrHandler.postDelayed(this, Constants.QR_TOKEN_VALIDITY_MS)
        }
    }

    // ── Present-count refresh ─────────────────────────────────────────────────
    private val countRefreshHandler  = Handler(Looper.getMainLooper())
    private val countRefreshRunnable = Runnable { refreshPresentCount() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGenerateQrBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getStringExtra(Constants.EXTRA_SESSION_ID) ?: run {
            toast("No session ID"); finish(); return
        }

        binding.toolbar.setNavigationOnClickListener { confirmEnd() }
        binding.btnEndSession.setOnClickListener { confirmEnd() }

        loadSession()
    }

    private fun loadSession() {
        binding.progressBar.show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                session = FirebaseManager.getSession(sessionId)
                session?.let { startDisplaying(it) }
                    ?: run { toast("Session not found"); finish() }
            } catch (e: Exception) {
                toast("Error: ${e.message}"); finish()
            } finally {
                binding.progressBar.hide()
            }
        }
    }

    private fun startDisplaying(s: AttendanceSession) {
        binding.tvSubject.text  = s.subject
        binding.tvDuration.text = "Window: ${s.durationMinutes} min"

        val remaining = s.endTime - System.currentTimeMillis()
        if (remaining <= 0) {
            sessionExpired(); return
        }

        // Session countdown
        sessionCountdown = object : CountDownTimer(remaining, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                binding.tvSessionCountdown.text = "Closes in: ${millisUntilFinished.toCountdownString()}"
                refreshPresentCount()
            }
            override fun onFinish() { sessionExpired() }
        }.start()

        // Start QR rotation immediately
        qrHandler.post(qrRefreshRunnable)
    }

    // ── QR generation ─────────────────────────────────────────────────────────

    private fun generateNewQRCode() {
        val token   = QRCodeGenerator.buildToken(sessionId)
        val bitmap  = QRCodeGenerator.tokenToBitmap(token)
        runOnUiThread {
            binding.ivQrCode.setImageBitmap(bitmap)
            startTokenCountdown()
        }
    }

    private var tokenCountdown: CountDownTimer? = null

    private fun startTokenCountdown() {
        tokenCountdown?.cancel()
        tokenCountdown = object : CountDownTimer(Constants.QR_TOKEN_VALIDITY_MS, 1000L) {
            override fun onTick(ms: Long) {
                binding.tvTokenTimer.text = "QR refreshes in: ${(ms / 1000L)}s"
                updateProgressBar(ms)
            }
            override fun onFinish() { /* qrHandler handles next cycle */ }
        }.start()
    }

    private fun updateProgressBar(remainingMs: Long) {
        val progress = ((remainingMs.toFloat() / Constants.QR_TOKEN_VALIDITY_MS) * 100).toInt()
        binding.qrProgressBar.progress = progress
    }

    // ── Present count ─────────────────────────────────────────────────────────

    private fun refreshPresentCount() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val updated = FirebaseManager.getSession(sessionId)
                binding.tvPresentCount.text = "Present: ${updated?.presentCount ?: 0}"
            } catch (_: Exception) {}
        }
    }

    // ── Session management ────────────────────────────────────────────────────

    private fun sessionExpired() {
        runOnUiThread {
            toast("Session window has closed")
            binding.btnEndSession.text = "Session Ended"
            qrHandler.removeCallbacks(qrRefreshRunnable)
            tokenCountdown?.cancel()
        }
    }

    private fun confirmEnd() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("End Session")
            .setMessage("Are you sure you want to end this attendance session?")
            .setPositiveButton("End") { _, _ -> endSession() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun endSession() {
        qrHandler.removeCallbacks(qrRefreshRunnable)
        tokenCountdown?.cancel()
        sessionCountdown?.cancel()

        CoroutineScope(Dispatchers.Main).launch {
            try {
                FirebaseManager.closeSession(sessionId)
                toast("Session ended")
            } catch (e: Exception) {
                toast("Error closing session: ${e.message}")
            } finally {
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        qrHandler.removeCallbacks(qrRefreshRunnable)
        tokenCountdown?.cancel()
        sessionCountdown?.cancel()
        countRefreshHandler.removeCallbacks(countRefreshRunnable)
    }
}
