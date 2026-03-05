package com.smartattendance.app.teacher

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.smartattendance.app.databinding.ActivityAttendanceListBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.AttendanceRecord
import com.smartattendance.app.utils.Constants
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
 * Shows all students who were marked present in a specific session.
 * Accessible only to the teacher who owns the session.
 */
class AttendanceListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAttendanceListBinding
    private val records = mutableListOf<AttendanceRecord>()
    private lateinit var adapter: RecordAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAttendanceListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val sessionId = intent.getStringExtra(Constants.EXTRA_SESSION_ID) ?: run {
            toast("Missing session ID"); finish(); return
        }

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapter = RecordAdapter(records)
        binding.rvRecords.layoutManager = LinearLayoutManager(this)
        binding.rvRecords.adapter        = adapter

        loadRecords(sessionId)
    }

    private fun loadRecords(sessionId: String) {
        binding.progressBar.show()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Load session metadata
                val session = FirebaseManager.getSession(sessionId)
                binding.tvSessionTitle.text  = session?.subject ?: "Session"
                binding.tvPresentCount.text  = "Total Present: ${session?.presentCount ?: 0}"

                // Load records
                val list = FirebaseManager.getSessionRecords(sessionId)
                records.clear()
                records.addAll(list)
                adapter.notifyDataSetChanged()
                binding.tvEmpty.visibility =
                    if (list.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                toast("Error: ${e.message}")
            } finally {
                binding.progressBar.hide()
            }
        }
    }

    // ── Inner adapter ─────────────────────────────────────────────────────────

    inner class RecordAdapter(private val data: List<AttendanceRecord>) :
        RecyclerView.Adapter<RecordAdapter.VH>() {

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val tvName:   TextView = view.findViewById(R.id.tvStudentName)
            val tvRoll:   TextView = view.findViewById(R.id.tvRollNo)
            val tvTime:   TextView = view.findViewById(R.id.tvTime)
            val tvCoords: TextView = view.findViewById(R.id.tvCoords)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_record, parent, false))

        override fun getItemCount() = data.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val r = data[position]
            holder.tvName.text   = r.studentName
            holder.tvRoll.text   = "Roll: ${r.rollNo}"
            holder.tvTime.text   = r.timestamp.toDateTimeString()
            holder.tvCoords.text = "%.4f, %.4f".format(r.latitude, r.longitude)
        }
    }
}
