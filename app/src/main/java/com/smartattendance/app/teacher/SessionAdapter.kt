package com.smartattendance.app.teacher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.smartattendance.app.R
import com.smartattendance.app.models.AttendanceSession
import com.smartattendance.app.utils.toDateTimeString

class SessionAdapter(
    private val items: List<AttendanceSession>,
    private val onClick: (AttendanceSession) -> Unit
) : RecyclerView.Adapter<SessionAdapter.VH>() {

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvSubject: TextView     = view.findViewById(R.id.tvSubject)
        val tvDate: TextView        = view.findViewById(R.id.tvDate)
        val tvPresent: TextView     = view.findViewById(R.id.tvPresent)
        val tvStatus: TextView      = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_session, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val s = items[position]
        holder.tvSubject.text  = s.subject
        holder.tvDate.text     = s.startTime.toDateTimeString()
        holder.tvPresent.text  = "Present: ${s.presentCount}"
        holder.tvStatus.text   = if (s.isActive) "ACTIVE" else "CLOSED"
        holder.tvStatus.setTextColor(
            if (s.isActive) android.graphics.Color.parseColor("#4CAF50")
            else            android.graphics.Color.parseColor("#9E9E9E")
        )
        holder.itemView.setOnClickListener { onClick(s) }
    }
}
