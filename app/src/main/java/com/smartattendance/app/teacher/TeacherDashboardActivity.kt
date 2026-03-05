package com.smartattendance.app.teacher

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartattendance.app.activities.LoginActivity
import com.smartattendance.app.databinding.ActivityTeacherDashboardBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceSession
import com.smartattendance.app.utils.Constants
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Teacher home screen.
 * Shows: teacher name, list of recent sessions, + FAB to create a new session.
 */
class TeacherDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTeacherDashboardBinding
    private val sessions = mutableListOf<AttendanceSession>()
    private lateinit var adapter: SessionAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTeacherDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupClicks()
        loadTeacherProfile()
        loadSessions()
    }

    private fun setupRecyclerView() {
        adapter = SessionAdapter(sessions) { session ->
            // Tap a session → view its attendance list
            startActivity(Intent(this, AttendanceListActivity::class.java).apply {
                putExtra(Constants.EXTRA_SESSION_ID, session.sessionId)
            })
        }
        binding.rvSessions.layoutManager = LinearLayoutManager(this)
        binding.rvSessions.adapter        = adapter
    }

    private fun setupClicks() {
        // Start new attendance session
        binding.fabNewSession.setOnClickListener {
            startActivity(Intent(this, SessionManagerActivity::class.java))
        }

        // Sign out
        binding.ivLogout.setOnClickListener {
            FirebaseManager.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun loadTeacherProfile() {
        val uid = FirebaseManager.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val teacher = FirebaseManager.getTeacher(uid)
            binding.tvTeacherName.text = "Welcome, ${teacher?.name ?: "Teacher"}"
            binding.tvDepartment.text  = teacher?.department ?: ""
        }
    }

    private fun loadSessions() {
        val uid = FirebaseManager.currentUser?.uid ?: return
        binding.progressBar.show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val list = FirebaseManager.getTeacherSessions(uid)
                sessions.clear()
                sessions.addAll(list)
                adapter.notifyDataSetChanged()
                binding.tvEmptyState.visibility =
                    if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            } catch (e: Exception) {
                toast("Failed to load sessions: ${e.message}")
            } finally {
                binding.progressBar.hide()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadSessions()   // refresh on return from sub-screens
    }
}
