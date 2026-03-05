package com.smartattendance.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.smartattendance.app.databinding.ActivityRegisterBinding
import com.smartattendance.app.firebase.FirebaseManager
import com.smartattendance.app.models.Student
import com.smartattendance.app.models.Teacher
import com.smartattendance.app.models.User
import com.smartattendance.app.models.UserRole
import com.smartattendance.app.student.StudentDashboardActivity
import com.smartattendance.app.teacher.TeacherDashboardActivity
import com.smartattendance.app.utils.DeviceIdHelper
import com.smartattendance.app.utils.toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Registration screen for both Students and Teachers.
 * Role is selected via a RadioGroup.
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Show roll-no field only for students
        binding.radioGroupRole.setOnCheckedChangeListener { _, checkedId ->
            binding.tilRollNo.visibility =
                if (checkedId == binding.rbStudent.id) View.VISIBLE else View.GONE
        }

        binding.btnRegister.setOnClickListener { attemptRegister() }
        binding.tvLogin.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        val name     = binding.etName.text.toString().trim()
        val email    = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirm  = binding.etConfirmPassword.text.toString().trim()
        val rollNo   = binding.etRollNo.text.toString().trim()
        val dept     = binding.etDepartment.text.toString().trim()
        val isTeacher = binding.rbTeacher.isChecked

        // Validation
        if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
            toast("All fields are required"); return
        }
        if (password.length < 6) {
            toast("Password must be at least 6 characters"); return
        }
        if (password != confirm) {
            toast("Passwords do not match"); return
        }
        if (!isTeacher && rollNo.isEmpty()) {
            toast("Roll number is required for students"); return
        }

        setLoading(true)

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val firebaseUser = FirebaseManager.register(email, password)
                val uid          = firebaseUser.uid
                val deviceId     = DeviceIdHelper.getDeviceId(this@RegisterActivity)
                val role         = if (isTeacher) UserRole.TEACHER.name else UserRole.STUDENT.name

                // Base user doc
                FirebaseManager.saveUser(User(uid = uid, name = name, email = email, role = role, deviceId = deviceId))

                // Role-specific doc
                if (isTeacher) {
                    FirebaseManager.saveTeacher(Teacher(teacherId = uid, uid = uid, name = name, email = email, department = dept))
                    startDashboard(TeacherDashboardActivity::class.java)
                } else {
                    FirebaseManager.saveStudent(Student(studentId = uid, uid = uid, name = name, email = email, rollNo = rollNo, department = dept, deviceId = deviceId))
                    startDashboard(StudentDashboardActivity::class.java)
                }
            } catch (e: Exception) {
                setLoading(false)
                toast("Registration failed: ${e.message}")
            }
        }
    }

    private fun startDashboard(dest: Class<*>) {
        startActivity(Intent(this, dest).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnRegister.isEnabled  = !loading
    }
}
