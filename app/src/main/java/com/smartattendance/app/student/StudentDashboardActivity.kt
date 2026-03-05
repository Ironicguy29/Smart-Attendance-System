package com.smartattendance.app.student

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartattendance.app.activities.LoginActivity
import com.smartattendance.app.databinding.ActivityStudentDashboardBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceRecord
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartattendance.app.R
import com.smartattendance.app.utils.toDateTimeString

/**
 * Student home screen.
 * Shows: student name, quick attendance percentage, recent records, + "Scan QR" button.
 */
class StudentDashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudentDashboardBinding
    private val records = mutableListOf<AttendanceRecord>()
    private lateinit var adapter: RecentAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStudentDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecycler()
        setupClicks()
        loadProfile()
        loadRecentRecords()
    }

    private fun setupRecycler() {
        adapter = RecentAdapter(records)
        binding.rvRecent.layoutManager = LinearLayoutManager(this)
        binding.rvRecent.adapter        = adapter
    }

    private fun setupClicks() {
        binding.btnScanQR.setOnClickListener {
            startActivity(Intent(this, ScanQRActivity::class.java))
        }
        binding.btnHistory.setOnClickListener {
            startActivity(Intent(this, AttendanceHistoryActivity::class.java))
        }
        binding.ivLogout.setOnClickListener {
            FirebaseManager.signOut()
            startActivity(Intent(this, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            })
        }
    }

    private fun loadProfile() {
        val uid = FirebaseManager.currentUser?.uid ?: return
        CoroutineScope(Dispatchers.Main).launch {
            val student = FirebaseManager.getStudent(uid)
            binding.tvStudentName.text = "Hi, ${student?.name ?: "Student"} 👋"
            binding.tvRollNo.text      = "Roll No: ${student?.rollNo ?: "-"}"
        }
    }

    private fun loadRecentRecords() {
        val uid = FirebaseManager.currentUser?.uid ?: return
        binding.progressBar.show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val all = FirebaseManager.getStudentRecords(uid)
                records.clear()
                records.addAll(all.take(5))        // show last 5
                adapter.notifyDataSetChanged()

                // Attendance % card
                val total    = all.size
                val present  = all.count { it.status == "PRESENT" }
                val pct      = if (total > 0) (present * 100f / total) else 0f
                binding.tvAttendancePct.text     = "%.1f%%".format(pct)
                binding.tvTotalClasses.text      = "Out of $total sessions"
                binding.tvEmptyRecent.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                toast("Error loading records: ${e.message}")
            } finally {
                binding.progressBar.hide()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadRecentRecords()
    }

    // ── Inline adapter ────────────────────────────────────────────────────────

    inner class RecentAdapter(private val data: List<AttendanceRecord>) :
        RecyclerView.Adapter<RecentAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvSubject: TextView = view.findViewById(R.id.tvSubject)
            val tvDate:    TextView = view.findViewById(R.id.tvDate)
            val tvStatus:  TextView = view.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_attendance_record, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = data[position]
            holder.tvSubject.text = r.subject
            holder.tvDate.text    = r.timestamp.toDateTimeString()
            holder.tvStatus.text  = r.status
            holder.tvStatus.setTextColor(
                if (r.status == "PRESENT") android.graphics.Color.parseColor("#4CAF50")
                else                       android.graphics.Color.parseColor("#F44336")
            )
        }
    }
}
