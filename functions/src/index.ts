import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

admin.initializeApp();

const db = admin.firestore();

// ── Constants ────────────────────────────────────────────────────────────────
const QR_TOKEN_VALIDITY_MS  = 10_000;   // 10 seconds
const DEFAULT_RADIUS_METERS = 50;

// ════════════════════════════════════════════════════════════════════════════
// verifyAndMarkAttendance
//
// Called by the student's ScanQRActivity after a successful QR scan.
//
// Expected payload:
// {
//   sessionId   : string,
//   token       : string,   // UUID inside QR
//   timestamp   : number,   // epoch ms when token was generated
//   studentId   : string,
//   studentName : string,
//   rollNo      : string,
//   studentLat  : number,
//   studentLon  : number,
//   deviceId    : string
// }
//
// Returns:
// { success: boolean, message: string }
// ════════════════════════════════════════════════════════════════════════════
export const verifyAndMarkAttendance = functions
  .region("us-central1")
  .https.onCall(async (data: Record<string, unknown>, context: functions.https.CallableContext) => {

    // ── Auth check ──────────────────────────────────────────────────────────
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Must be signed in.");
    }

    const {
      sessionId, token, timestamp,
      studentId, studentName, rollNo,
      studentLat, studentLon, deviceId,
    } = data as {
      sessionId: string; token: string; timestamp: number;
      studentId: string; studentName: string; rollNo: string;
      studentLat: number; studentLon: number; deviceId: string;
    };

    // ── Basic field validation ──────────────────────────────────────────────
    if (!sessionId || !token || !studentId || !studentLat || !studentLon) {
      throw new functions.https.HttpsError("invalid-argument", "Missing required fields.");
    }

    // ── Fetch session document ──────────────────────────────────────────────
    const sessionRef  = db.collection("attendanceSessions").doc(sessionId);
    const sessionSnap = await sessionRef.get();

    if (!sessionSnap.exists) {
      return { success: false, message: "Session not found." };
    }

    const session = sessionSnap.data()!;

    // ── Check session is still active ───────────────────────────────────────
    if (!session.isActive) {
      return { success: false, message: "Session has been manually closed." };
    }

    // ── Check attendance window (2 minutes) ─────────────────────────────────
    const now = Date.now();
    if (now < session.startTime || now > session.endTime) {
      return { success: false, message: "Attendance window has expired." };
    }

    // ── Validate QR token expiry (10-second rotation) ───────────────────────
    const tokenAge = now - timestamp;
    if (tokenAge > QR_TOKEN_VALIDITY_MS || tokenAge < 0) {
      return { success: false, message: "QR code has expired. Ask teacher to refresh." };
    }

    // ── Validate GPS location ────────────────────────────────────────────────
    const radiusMeters = session.radiusMeters ?? DEFAULT_RADIUS_METERS;
    const distance     = haversineMeters(
      studentLat, studentLon,
      session.latitude, session.longitude
    );

    if (distance > radiusMeters) {
      return {
        success: false,
        message: `You are ${Math.round(distance)} m away. Must be within ${radiusMeters} m.`,
      };
    }

    // ── Duplicate prevention ─────────────────────────────────────────────────
    const duplicateQuery = await db.collection("attendanceRecords")
      .where("studentId",  "==", studentId)
      .where("sessionId",  "==", sessionId)
      .limit(1)
      .get();

    if (!duplicateQuery.empty) {
      return { success: false, message: "Attendance already marked for this session." };
    }

    // ── Device ID check (optional secondary check) ───────────────────────────
    const studentDoc = await db.collection("students").doc(studentId).get();
    if (studentDoc.exists) {
      const storedDevice = studentDoc.data()?.deviceId;
      if (storedDevice && storedDevice !== deviceId) {
        return {
          success: false,
          message: "Device mismatch. Attendance must be marked from your registered device.",
        };
      }
    }

    // ── All checks passed → write attendance record ──────────────────────────
    const batch = db.batch();

    const recordRef = db.collection("attendanceRecords").doc();
    batch.set(recordRef, {
      recordId:    recordRef.id,
      studentId,
      studentName,
      rollNo,
      sessionId,
      classId:     session.classId ?? "",
      subject:     session.subject ?? "",
      timestamp:   now,
      latitude:    studentLat,
      longitude:   studentLon,
      deviceId,
      status:      "PRESENT",
      markedAt:    admin.firestore.FieldValue.serverTimestamp(),
    });

    // Increment present counter on session
    batch.update(sessionRef, {
      presentCount: admin.firestore.FieldValue.increment(1),
    });

    await batch.commit();

    functions.logger.info(`Attendance marked: student=${studentId} session=${sessionId}`);

    return { success: true, message: "Attendance marked successfully! ✅" };
  });

// ════════════════════════════════════════════════════════════════════════════
// autoCloseExpiredSessions
// Scheduled every 5 minutes — closes sessions whose endTime has passed.
// ════════════════════════════════════════════════════════════════════════════
export const autoCloseExpiredSessions = functions
  .region("us-central1")
  .pubsub.schedule("every 5 minutes")
  .onRun(async () => {
    const now = Date.now();

    const expiredSnap = await db.collection("attendanceSessions")
      .where("isActive", "==", true)
      .where("endTime",  "<=", now)
      .get();

    if (expiredSnap.empty) return null;

    const batch = db.batch();
    expiredSnap.docs.forEach((doc: admin.firestore.QueryDocumentSnapshot) => {
      batch.update(doc.ref, { isActive: false });
    });

    await batch.commit();
    functions.logger.info(`Auto-closed ${expiredSnap.size} expired session(s).`);
    return null;
  });

// ════════════════════════════════════════════════════════════════════════════
// getAttendanceSummary (callable)
// Returns { totalSessions, presentCount, percentage } for a student.
// ════════════════════════════════════════════════════════════════════════════
export const getAttendanceSummary = functions
  .region("us-central1")
  .https.onCall(async (data: Record<string, unknown>, context: functions.https.CallableContext) => {
    if (!context.auth) {
      throw new functions.https.HttpsError("unauthenticated", "Sign in required.");
    }

    const { studentId, classId } = data as { studentId: string; classId?: string };

    let query: admin.firestore.Query = db.collection("attendanceRecords")
      .where("studentId", "==", studentId);

    if (classId) query = query.where("classId", "==", classId);

    const snap    = await query.get();
    const total   = snap.size;
    const present = snap.docs.filter((d: admin.firestore.QueryDocumentSnapshot) => d.data().status === "PRESENT").length;
    const pct     = total > 0 ? (present * 100) / total : 0;

    return { totalSessions: total, presentCount: present, percentage: pct };
  });

// ════════════════════════════════════════════════════════════════════════════
// Haversine helper
// ════════════════════════════════════════════════════════════════════════════
function haversineMeters(
  lat1: number, lon1: number,
  lat2: number, lon2: number
): number {
  const R     = 6_371_000;
  const dLat  = toRad(lat2 - lat1);
  const dLon  = toRad(lon2 - lon1);
  const a     =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLon / 2) ** 2;
  const c     = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

function toRad(deg: number): number {
  return (deg * Math.PI) / 180;
}
