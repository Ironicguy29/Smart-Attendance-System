package com.smartattendance.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.smartattendance.app.databinding.ActivityLoginBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.student.StudentDashboardActivity
import com.smartattendance.app.teacher.TeacherDashboardActivity
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Login screen.
 * Authenticates via Firebase Auth → reads role from Firestore → routes to correct dashboard.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnLogin.setOnClickListener { attemptLogin() }
        binding.tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            toast("Please fill in all fields")
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            toast("Enter a valid email address")
            return
        }

        setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val firebaseUser = FirebaseManager.signIn(email, password)
                val user         = FirebaseManager.getUser(firebaseUser.uid)

                val role = user?.role ?: "STUDENT"
                val dest = if (role == "TEACHER") TeacherDashboardActivity::class.java
                           else                   StudentDashboardActivity::class.java

                startActivity(Intent(this@LoginActivity, dest).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
            } catch (e: Exception) {
                setLoading(false)
                toast(mapFirebaseError(e.message))
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLogin.isEnabled = !loading
    }

    private fun mapFirebaseError(msg: String?): String = when {
        msg == null                          -> "Login failed. Try again."
        msg.contains("no user record")       -> "No account found with this email."
        msg.contains("password is invalid")  -> "Incorrect password."
        msg.contains("blocked")              -> "Too many attempts. Try again later."
        msg.contains("network")              -> "Network error. Check your connection."
        else                                  -> "Login failed: $msg"
    }
}
