package com.smartattendance.app.activities

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.smartattendance.app.R
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.student.StudentDashboardActivity
import com.smartattendance.app.teacher.TeacherDashboardActivity
import com.smartattendance.app.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Splash screen shown for 1.5 s while we check auth state.
 * Routes to: LoginActivity | TeacherDashboard | StudentDashboard
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({ checkAuthState() }, 1500)
    }

    private fun checkAuthState() {
        val user = FirebaseManager.currentUser
        if (user == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        // Fetch role from Firestore to decide which dashboard to open
        CoroutineScope(Dispatchers.Main).launch {
            val userProfile = FirebaseManager.getUser(user.uid)
            val role = userProfile?.role ?: ""
            val dest = if (role == "TEACHER") TeacherDashboardActivity::class.java
                       else                   StudentDashboardActivity::class.java
            startActivity(Intent(this@SplashActivity, dest))
            finish()
        }
    }
}
