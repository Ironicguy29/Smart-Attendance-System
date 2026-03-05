# 📋 Smart Attendance System — Android App

> **College Project** | Anti-proxy attendance using Dynamic QR + GPS + Time Window

---

## 🏗️ System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        SMART ATTENDANCE SYSTEM                       │
└─────────────────────────────────────────────────────────────────────┘

  ┌──────────────────┐          ┌──────────────────────────────────────┐
  │   TEACHER APP    │          │         FIREBASE BACKEND             │
  │                  │          │                                      │
  │  ┌────────────┐  │  HTTPS   │  ┌──────────────────────────────┐  │
  │  │  Login     │──┼──────────┼─▶│   Firebase Auth               │  │
  │  └────────────┘  │          │  └──────────────────────────────┘  │
  │  ┌────────────┐  │  write   │  ┌──────────────────────────────┐  │
  │  │ NewSession │──┼──────────┼─▶│   Firestore                   │  │
  │  └────────────┘  │          │  │   • users                     │  │
  │  ┌────────────┐  │          │  │   • students                  │  │
  │  │ GenerateQR │  │          │  │   • teachers                  │  │
  │  │ (10s cycle)│  │          │  │   • attendanceSessions        │  │
  │  └────────────┘  │          │  │   • attendanceRecords         │  │
  │  ┌────────────┐  │  read    │  └──────────────────────────────┘  │
  │  │AttendList  │──┼──────────┼─▶│   Cloud Functions              │  │
  │  └────────────┘  │          │  │   • verifyAndMarkAttendance    │  │
  └──────────────────┘          │  │   • autoCloseExpiredSessions   │  │
                                │  │   • getAttendanceSummary       │  │
  ┌──────────────────┐          │  └──────────────────────────────┘  │
  │   STUDENT APP    │          └──────────────────────────────────────┘
  │                  │
  │  ┌────────────┐  │
  │  │  Login     │  │  SECURITY CHECKS (Cloud Function)
  │  └────────────┘  │  ┌────────────────────────────────────────┐
  │  ┌────────────┐  │  │  1. Auth token valid?                  │
  │  │  ScanQR    │──┼──│  2. Session active + within 2 min?     │
  │  │  (ZXing)   │  │  │  3. QR token < 10 seconds old?         │
  │  └────────────┘  │  │  4. Student within 50 m radius?        │
  │  ┌────────────┐  │  │  5. Not already marked?                │
  │  │  History   │  │  │  6. Same device as registered?         │
  │  └────────────┘  │  └────────────────────────────────────────┘
  └──────────────────┘
```

---

## 🔐 Security Mechanisms

| Mechanism | Implementation |
|-----------|---------------|
| Dynamic QR | Token UUID regenerated every **10 seconds** by `QRCodeGenerator.buildToken()` |
| GPS Radius | Haversine distance ≤ **50 m** from teacher's classroom coordinate |
| Time Window | Session has configurable `endTime` (default **2 minutes**); Cloud Function rejects late scans |
| Duplicate Prevention | Firestore query for `(studentId, sessionId)` before writing record |
| Device Binding | `deviceId` stored on registration; mismatches are rejected |
| Firestore Rules | `attendanceRecords` is **write-blocked** — only Cloud Functions can insert |

---

## 📁 Project Structure

```
SmartAttendance/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/smartattendance/app/
│       │   ├── activities/
│       │   │   ├── SplashActivity.kt
│       │   │   ├── LoginActivity.kt
│       │   │   └── RegisterActivity.kt
│       │   ├── teacher/
│       │   │   ├── TeacherDashboardActivity.kt
│       │   │   ├── SessionManagerActivity.kt
│       │   │   ├── GenerateQRActivity.kt       ← Dynamic QR (10s)
│       │   │   ├── AttendanceListActivity.kt
│       │   │   └── SessionAdapter.kt
│       │   ├── student/
│       │   │   ├── StudentDashboardActivity.kt
│       │   │   ├── ScanQRActivity.kt           ← ZXing scanner
│       │   │   └── AttendanceHistoryActivity.kt
│       │   ├── models/
│       │   │   └── Models.kt                  ← All data classes
│       │   ├── firebase/
│       │   │   └── FirebaseManager.kt          ← Singleton wrapper
│       │   └── utils/
│       │       ├── Constants.kt
│       │       ├── QRCodeGenerator.kt
│       │       ├── GPSLocationHelper.kt
│       │       ├── DeviceIdHelper.kt
│       │       └── Extensions.kt
│       └── res/
│           ├── layout/       ← All XML screen layouts
│           ├── drawable/
│           └── values/       ← colors, strings, themes
├── functions/
│   └── src/index.ts          ← Cloud Functions (TypeScript)
├── firestore.rules
├── firestore.indexes.json
├── firebase.json
├── build.gradle
└── settings.gradle
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|------------|
| Language | Kotlin |
| IDE | Android Studio Hedgehog+ |
| Min SDK | API 24 (Android 7.0) |
| QR Generation | ZXing Core 3.5.3 |
| QR Scanning | journeyapps/zxing-android-embedded 4.3.0 |
| Location | Google Play Services Location 21.1 |
| Backend | Firebase (Auth + Firestore + Functions) |
| Functions | Node.js 18 + TypeScript |
| UI | Material Components 1.11 |

---

## ⚙️ Firebase Setup Guide

### Step 1 — Create Firebase Project

1. Go to [console.firebase.google.com](https://console.firebase.google.com)
2. **Add project** → name it `SmartAttendance`
3. Enable **Google Analytics** (optional)

### Step 2 — Add Android App

1. Click **Add app** → Android
2. Package name: `com.smartattendance.app`
3. Download `google-services.json`
4. Place it at: `SmartAttendance/app/google-services.json`

### Step 3 — Enable Firebase Services

```
Firebase Console → Authentication → Sign-in method → Email/Password → Enable
Firebase Console → Firestore Database → Create database → Production mode
Firebase Console → Functions → Get started (requires Blaze plan)
```

### Step 4 — Deploy Firestore Rules + Indexes

```bash
firebase login
firebase use --add          # select your project
firebase deploy --only firestore:rules,firestore:indexes
```

### Step 5 — Deploy Cloud Functions

```bash
cd functions
npm install
npm run build
cd ..
firebase deploy --only functions
```

### Step 6 — Build & Run Android App

```bash
# Open project in Android Studio
# Sync Gradle (File → Sync Project with Gradle Files)
# Connect device or start emulator
# Run → Run 'app'
```

---

## 📱 Screen Descriptions

| Screen | Role | Description |
|--------|------|-------------|
| `SplashActivity` | Both | Logo + auto-route based on auth state |
| `LoginActivity` | Both | Email/password login with Firebase Auth |
| `RegisterActivity` | Both | Student or Teacher registration with role selector |
| `TeacherDashboardActivity` | Teacher | Welcome card + recent sessions list + FAB |
| `SessionManagerActivity` | Teacher | Create session: subject, duration, GPS capture |
| `GenerateQRActivity` | Teacher | Live QR display with 10s rotation + session countdown |
| `AttendanceListActivity` | Teacher | Per-session list of present students |
| `StudentDashboardActivity` | Student | Attendance % card + recent records + Scan QR button |
| `ScanQRActivity` | Student | Full-screen ZXing scanner + result feedback |
| `AttendanceHistoryActivity` | Student | Full history with summary stats (total/present/absent/%) |

---

## 🔑 Required Android Permissions

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
```

Runtime permissions requested: `CAMERA` + `ACCESS_FINE_LOCATION`

---

## 🗄️ Firestore Database Structure

```
firestore/
├── users/{uid}
│   ├── uid, name, email, role, deviceId, createdAt
│
├── students/{uid}
│   ├── studentId, uid, name, rollNo, email, department, semester, deviceId
│
├── teachers/{uid}
│   ├── teacherId, uid, name, email, department, employeeId
│
├── classes/{classId}
│   ├── classId, subject, subjectCode, teacherId, department, semester, room
│
├── attendanceSessions/{sessionId}
│   ├── sessionId, classId, teacherId, subject
│   ├── startTime, endTime, durationMinutes   ← time window
│   ├── latitude, longitude, radiusMeters     ← GPS fence
│   ├── isActive, presentCount, totalStudents
│
└── attendanceRecords/{recordId}
    ├── recordId, studentId, studentName, rollNo
    ├── sessionId, classId, subject
    ├── timestamp, latitude, longitude, deviceId
    └── status (PRESENT/ABSENT/LATE), markedAt
```

---

## 🌐 Cloud Functions API

### `verifyAndMarkAttendance` (Callable HTTPS)

**Request payload:**
```json
{
  "sessionId":   "abc123",
  "token":       "550e8400-e29b-41d4-a716-...",
  "timestamp":   1709640000000,
  "studentId":   "uid_xyz",
  "studentName": "Rahul Sharma",
  "rollNo":      "CS2021042",
  "studentLat":  18.5204,
  "studentLon":  73.8567,
  "deviceId":    "a1b2c3d4e5f6"
}
```

**Response:**
```json
{ "success": true,  "message": "Attendance marked successfully! ✅" }
{ "success": false, "message": "QR code has expired. Ask teacher to refresh." }
{ "success": false, "message": "You are 87 m away. Must be within 50 m." }
{ "success": false, "message": "Attendance already marked for this session." }
```

**Validation sequence:**
1. Firebase Auth token check
2. Session exists + `isActive == true`
3. `now` within `[startTime, endTime]`
4. Token age ≤ 10 000 ms
5. Haversine distance ≤ `radiusMeters`
6. No existing record for `(studentId, sessionId)`
7. Device ID matches registered device

### `autoCloseExpiredSessions` (Scheduled — every 5 min)
Auto-sets `isActive = false` for all sessions past `endTime`.

### `getAttendanceSummary` (Callable HTTPS)
Returns `{ totalSessions, presentCount, percentage }` for a student.

---

## ❌ Error Handling Cases

| Error | Cause | Response |
|-------|-------|----------|
| `Session has expired` | Now > endTime | Reject scan |
| `QR code expired` | Token age > 10s | Reject scan |
| `Outside radius` | Distance > 50m | Reject + show distance |
| `Already marked` | Duplicate record found | Reject with message |
| `Device mismatch` | Different device used | Reject (proxy attempt) |
| `Session not found` | Invalid sessionId | Reject |
| `Unauthenticated` | Not signed in | HTTP 401 |
| `Location unavailable` | GPS off / stale fix | Prompt student to enable GPS |
| `Network error` | No internet | Show offline message |

---

## 🚀 Quick Start Commands

```bash
# 1. Clone / open project in Android Studio
#    Place google-services.json in app/

# 2. Deploy backend
cd SmartAttendance
firebase login
firebase use --add
firebase deploy --only firestore

cd functions && npm install && npm run build && cd ..
firebase deploy --only functions

# 3. Build APK
./gradlew assembleDebug

# 4. Run on device (USB debugging on)
./gradlew installDebug
```

---

## 👥 Team / Submission Info

| Field | Value |
|-------|-------|
| Project Name | Smart Attendance System |
| Technology | Android (Kotlin) + Firebase |
| Academic Year | 2025–26 |
| Anti-proxy Techniques | Dynamic QR · GPS Geofence · 2-min Time Window |

---

*Built with ❤️ using Kotlin, Firebase, ZXing, and Google Play Services Location.*
