package com.smartattendance.app.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

// ─── User roles ───────────────────────────────────────────────────────────────
enum class UserRole { STUDENT, TEACHER }

// ─── User (base) ──────────────────────────────────────────────────────────────
data class User(
    @DocumentId val uid: String = "",
    val name: String = "",
    val email: String = "",
    val role: String = UserRole.STUDENT.name,
    val deviceId: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

// ─── Student ──────────────────────────────────────────────────────────────────
data class Student(
    @DocumentId val studentId: String = "",
    val uid: String = "",
    val name: String = "",
    val rollNo: String = "",
    val email: String = "",
    val department: String = "",
    val semester: Int = 1,
    val deviceId: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

// ─── Teacher ──────────────────────────────────────────────────────────────────
data class Teacher(
    @DocumentId val teacherId: String = "",
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val department: String = "",
    val employeeId: String = "",
    @ServerTimestamp val createdAt: Date? = null
)

// ─── Class / Subject ──────────────────────────────────────────────────────────
data class ClassRoom(
    @DocumentId val classId: String = "",
    val subject: String = "",
    val subjectCode: String = "",
    val teacherId: String = "",
    val department: String = "",
    val semester: Int = 1,
    val room: String = ""
)

// ─── Attendance Session ───────────────────────────────────────────────────────
data class AttendanceSession(
    @DocumentId val sessionId: String = "",
    val classId: String = "",
    val teacherId: String = "",
    val subject: String = "",
    val startTime: Long = 0L,           // epoch millis
    val endTime: Long = 0L,             // epoch millis
    val durationMinutes: Int = 2,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Double = 50.0,
    val isActive: Boolean = true,
    val totalStudents: Int = 0,
    val presentCount: Int = 0,
    @ServerTimestamp val createdAt: Date? = null
) {
    /** True while the session window is still open */
    fun isWithinWindow(): Boolean {
        val now = System.currentTimeMillis()
        return isActive && now in startTime..endTime
    }
}

// ─── QR Token (embedded inside QR code as JSON) ───────────────────────────────
data class QRToken(
    val sessionId: String = "",
    val token: String = "",             // UUID refreshed every 10 seconds
    val timestamp: Long = 0L,           // when this token was generated
    val expiresAt: Long = 0L            // timestamp + 10_000 ms
)

// ─── Attendance Record ────────────────────────────────────────────────────────
data class AttendanceRecord(
    @DocumentId val recordId: String = "",
    val studentId: String = "",
    val studentName: String = "",
    val rollNo: String = "",
    val sessionId: String = "",
    val classId: String = "",
    val subject: String = "",
    val timestamp: Long = 0L,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val deviceId: String = "",
    val status: String = AttendanceStatus.PRESENT.name,
    @ServerTimestamp val markedAt: Date? = null
)

enum class AttendanceStatus { PRESENT, ABSENT, LATE }

// ─── Attendance Summary (for history screen) ──────────────────────────────────
data class AttendanceSummary(
    val classId: String = "",
    val subject: String = "",
    val totalClasses: Int = 0,
    val attendedClasses: Int = 0,
    val percentage: Float = 0f
)
