package com.smartattendance.app.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.smartattendance.app.models.*
import kotlinx.coroutines.tasks.await

/**
 * Central singleton that wraps every Firebase interaction.
 *
 * All methods are suspend functions so callers use coroutines —
 * never blocking the main thread.
 */
object FirebaseManager {

    // ── Firebase instances ───────────────────────────────────────────────────
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val functions: FirebaseFunctions by lazy { FirebaseFunctions.getInstance() }

    // ── Collection paths ─────────────────────────────────────────────────────
    private const val COL_USERS              = "users"
    private const val COL_STUDENTS           = "students"
    private const val COL_TEACHERS           = "teachers"
    private const val COL_CLASSES            = "classes"
    private const val COL_SESSIONS           = "attendanceSessions"
    private const val COL_RECORDS            = "attendanceRecords"

    val currentUser: FirebaseUser? get() = auth.currentUser

    // ════════════════════════════════════════════════════════════════════════
    // AUTH
    // ════════════════════════════════════════════════════════════════════════

    /** Sign in with email + password. Returns the FirebaseUser on success. */
    suspend fun signIn(email: String, password: String): FirebaseUser {
        val result = auth.signInWithEmailAndPassword(email, password).await()
        return result.user ?: error("Sign-in succeeded but user was null")
    }

    /** Register a new account. Does NOT write Firestore documents. */
    suspend fun register(email: String, password: String): FirebaseUser {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        return result.user ?: error("Registration succeeded but user was null")
    }

    fun signOut() = auth.signOut()

    // ════════════════════════════════════════════════════════════════════════
    // USER PROFILE
    // ════════════════════════════════════════════════════════════════════════

    suspend fun saveUser(user: User) {
        db.collection(COL_USERS).document(user.uid).set(user).await()
    }

    suspend fun getUser(uid: String): User? =
        db.collection(COL_USERS).document(uid).get().await().toObject(User::class.java)

    /** Save student-specific profile */
    suspend fun saveStudent(student: Student) {
        db.collection(COL_STUDENTS).document(student.uid).set(student).await()
    }

    suspend fun getStudent(uid: String): Student? =
        db.collection(COL_STUDENTS).document(uid).get().await().toObject(Student::class.java)

    /** Save teacher-specific profile */
    suspend fun saveTeacher(teacher: Teacher) {
        db.collection(COL_TEACHERS).document(teacher.uid).set(teacher).await()
    }

    suspend fun getTeacher(uid: String): Teacher? =
        db.collection(COL_TEACHERS).document(uid).get().await().toObject(Teacher::class.java)

    // ════════════════════════════════════════════════════════════════════════
    // ATTENDANCE SESSIONS
    // ════════════════════════════════════════════════════════════════════════

    /** Create a new session and return the generated session ID. */
    suspend fun createSession(session: AttendanceSession): String {
        val ref = db.collection(COL_SESSIONS).document()
        val withId = session.copy(sessionId = ref.id)
        ref.set(withId).await()
        return ref.id
    }

    suspend fun getSession(sessionId: String): AttendanceSession? =
        db.collection(COL_SESSIONS).document(sessionId)
            .get().await()
            .toObject(AttendanceSession::class.java)

    /** Deactivate (close) an attendance session. */
    suspend fun closeSession(sessionId: String) {
        db.collection(COL_SESSIONS).document(sessionId)
            .update("isActive", false).await()
    }

    /** Update the present-count counter on a session. */
    suspend fun incrementPresentCount(sessionId: String) {
        db.collection(COL_SESSIONS).document(sessionId)
            .update("presentCount", com.google.firebase.firestore.FieldValue.increment(1))
            .await()
    }

    /** Fetch all sessions created by a teacher, most-recent first. */
    suspend fun getTeacherSessions(teacherId: String): List<AttendanceSession> =
        db.collection(COL_SESSIONS)
            .whereEqualTo("teacherId", teacherId)
            .orderBy("startTime", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(AttendanceSession::class.java)

    // ════════════════════════════════════════════════════════════════════════
    // ATTENDANCE RECORDS
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Mark a student present by calling the Cloud Function that enforces
     * token validity, GPS check, time window, and duplicate prevention.
     *
     * Returns a map: { "success": Boolean, "message": String }
     */
    suspend fun markAttendance(payload: Map<String, Any>): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        return functions
            .getHttpsCallable("verifyAndMarkAttendance")
            .call(payload).await()
            .data as Map<String, Any>
    }

    /** Retrieve attendance records for a given session (teacher view). */
    suspend fun getSessionRecords(sessionId: String): List<AttendanceRecord> =
        db.collection(COL_RECORDS)
            .whereEqualTo("sessionId", sessionId)
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .get().await()
            .toObjects(AttendanceRecord::class.java)

    /** Retrieve all records for a student (history view). */
    suspend fun getStudentRecords(studentId: String): List<AttendanceRecord> =
        db.collection(COL_RECORDS)
            .whereEqualTo("studentId", studentId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get().await()
            .toObjects(AttendanceRecord::class.java)

    /** Check whether a student already has a record for this session. */
    suspend fun hasAlreadyMarked(studentId: String, sessionId: String): Boolean {
        val snap = db.collection(COL_RECORDS)
            .whereEqualTo("studentId", studentId)
            .whereEqualTo("sessionId", sessionId)
            .limit(1)
            .get().await()
        return !snap.isEmpty
    }

    // ════════════════════════════════════════════════════════════════════════
    // CLASSES
    // ════════════════════════════════════════════════════════════════════════

    suspend fun saveClass(classRoom: ClassRoom): String {
        val ref = db.collection(COL_CLASSES).document()
        val withId = classRoom.copy(classId = ref.id)
        ref.set(withId).await()
        return ref.id
    }

    suspend fun getTeacherClasses(teacherId: String): List<ClassRoom> =
        db.collection(COL_CLASSES)
            .whereEqualTo("teacherId", teacherId)
            .get().await()
            .toObjects(ClassRoom::class.java)
}
