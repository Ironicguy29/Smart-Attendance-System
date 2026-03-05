package com.smartattendance.app.student

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartattendance.app.databinding.ActivityAttendanceHistoryBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceRecord
import com.smartattendance.app.utils.attendanceColor
import com.smartattendance.app.utils.hide
import com.smartattendance.app.utils.show
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartattendance.app.R
import com.smartattendance.app.utils.toDateTimeString

/**
 * Full attendance history for the logged-in student.
 * Shows: overall % stat card + per-session record list.
 */
class AttendanceHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceHistoryBinding
    private val records = mutableListOf<AttendanceRecord>()
    private lateinit var adapter: HistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = HistoryAdapter(records)
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter        = adapter

        loadHistory()
    }

    private fun loadHistory() {
        val uid = FirebaseManager.currentUser?.uid ?: return
        binding.progressBar.show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val all     = FirebaseManager.getStudentRecords(uid)
                val total   = all.size
                val present = all.count { it.status == "PRESENT" }
                val pct     = if (total > 0) (present * 100f / total) else 0f

                binding.tvTotalSessions.text  = total.toString()
                binding.tvPresentCount.text   = present.toString()
                binding.tvAbsentCount.text    = (total - present).toString()
                binding.tvPercentage.text     = "%.1f%%".format(pct)
                binding.tvPercentage.setTextColor(attendanceColor(pct))
                binding.circularProgress.progress = pct.toInt()

                records.clear()
                records.addAll(all)
                adapter.notifyDataSetChanged()
                binding.tvEmpty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            } finally {
                binding.progressBar.hide()
            }
        }
    }

    // ── History adapter ───────────────────────────────────────────────────────

    inner class HistoryAdapter(private val data: List<AttendanceRecord>) :
        RecyclerView.Adapter<HistoryAdapter.VH>() {

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
