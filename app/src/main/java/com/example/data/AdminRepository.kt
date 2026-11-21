package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AcademySettings
import com.example.model.AdministratorRecord
import com.example.model.AttendanceRecord
import com.example.model.DefaultCatalog
import com.example.model.ExamResultRecord
import com.example.model.ExaminationTiming
import com.example.model.QrVerificationRecord
import com.example.model.RegistrationRecord
import com.example.model.SubjectItem
import com.example.model.SubjectResultEntry
import com.example.security.AdminSecurity
import com.example.security.AdminSession
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class AdminRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireAdminUserId(): String {
        val user = auth.currentUser
            ?: throw IllegalStateException("Google authentication required before accessing admin operations.")
        return user.uid
    }

    suspend fun checkCurrentUserHasAdminAccess(): Boolean {
        val user = auth.currentUser ?: return false
        if (user.email.equals(DefaultCatalog.BOOTSTRAP_ADMIN_EMAIL, ignoreCase = true) && user.isEmailVerified) {
            return true
        }
        return try {
            db.collection("admins").document(user.uid).get().await().exists()
        } catch (_: Exception) {
            false
        }
    }

    suspend fun ensureBootstrapAdminInitialized(): Result<AdministratorRecord> {
        val docPath = "administrators/primary"
        return try {
            requireAdminUserId()
            val ref = db.collection("administrators").document("primary")
            val snap = ref.get().await()
            if (!snap.exists()) {
                val now = FieldValue.serverTimestamp()
                ref.set(
                    mapOf(
                        "email" to DefaultCatalog.BOOTSTRAP_ADMIN_EMAIL,
                        "passwordHash" to DefaultCatalog.BOOTSTRAP_ADMIN_HASH,
                        "passwordSalt" to DefaultCatalog.BOOTSTRAP_ADMIN_SALT,
                        "iterations" to DefaultCatalog.BOOTSTRAP_ADMIN_ITERATIONS,
                        "failedAttempts" to 0,
                        "lockedUntilMillis" to 0L,
                        "sessionVersion" to 1,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                ).await()
            }
            val updatedSnap = ref.get().await()
            val record = updatedSnap.toObject(
                AdministratorRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: error("Failed to load administrator record.")
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun seedInitialAcademyDataIfEmpty(): Result<Unit> {
        return try {
            val uid = requireAdminUserId()
            val now = FieldValue.serverTimestamp()

            val settingsRef = db.collection("academy_settings").document("main")
            if (!settingsRef.get().await().exists()) {
                settingsRef.set(
                    mapOf(
                        "academyName" to "AWAN ACADEMY",
                        "examHeading" to "Improvement Crash Half Length Registration",
                        "instructions" to DefaultCatalog.DEFAULT_EXAM_INSTRUCTIONS,
                        "updatedByUid" to uid,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                ).await()
            }

            val subjectsSnap = db.collection("subjects").limit(1).get().await()
            if (subjectsSnap.isEmpty) {
                val batch = db.batch()
                DefaultCatalog.DEFAULT_SUBJECTS.forEach { subject ->
                    batch.set(
                        db.collection("subjects").document(subject.id),
                        mapOf(
                            "name" to subject.name,
                            "codePrefix" to subject.codePrefix,
                            "assignedClasses" to subject.assignedClasses,
                            "active" to subject.active,
                            "sortOrder" to subject.sortOrder,
                            "createdAt" to now,
                            "updatedAt" to now
                        )
                    )
                }
                batch.commit().await()
            }

            val timingsSnap = db.collection("examination_timings").limit(1).get().await()
            if (timingsSnap.isEmpty) {
                val batch = db.batch()
                DefaultCatalog.DEFAULT_TIMINGS.forEach { timing ->
                    batch.set(
                        db.collection("examination_timings").document(timing.id),
                        mapOf(
                            "slotNumber" to timing.slotNumber,
                            "label" to timing.label,
                            "startTime" to timing.startTime,
                            "endTime" to timing.endTime,
                            "durationMinutes" to timing.durationMinutes,
                            "instructions" to timing.instructions,
                            "active" to timing.active,
                            "createdAt" to now,
                            "updatedAt" to now
                        )
                    )
                }
                batch.commit().await()
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, "academy_settings/main")
            Result.failure(e)
        }
    }

    suspend fun authenticateAdmin(username: String, password: String): Result<AdminSession> {
        val docPath = "administrators/primary"
        return try {
            val uid = requireAdminUserId()
            val hasAccess = checkCurrentUserHasAdminAccess()
            if (!hasAccess) {
                return Result.failure(
                    SecurityException(
                        "Unauthorized Google account. Sign in with ${DefaultCatalog.BOOTSTRAP_ADMIN_EMAIL} to access the Admin Portal."
                    )
                )
            }

            val adminRecord = ensureBootstrapAdminInitialized().getOrThrow()
            val nowMillis = System.currentTimeMillis()
            if (adminRecord.lockedUntilMillis > nowMillis) {
                val remainingSec = ((adminRecord.lockedUntilMillis - nowMillis) / 1000L).coerceAtLeast(1L)
                return Result.failure(
                    IllegalStateException("Too many failed login attempts. Try again in ${remainingSec}s.")
                )
            }

            val cleanEmail = username.trim().lowercase()
            val emailMatches = cleanEmail == adminRecord.email.lowercase()
            val passwordMatches = AdminSecurity.verifyPassword(
                password = password,
                saltHex = adminRecord.passwordSalt,
                expectedHashHex = adminRecord.passwordHash,
                iterations = adminRecord.iterations
            )

            val ref = db.collection("administrators").document("primary")
            if (!emailMatches || !passwordMatches) {
                val nextAttempts = adminRecord.failedAttempts + 1
                val lockUntil = if (nextAttempts >= AdminSecurity.MAX_FAILED_ATTEMPTS) {
                    nowMillis + AdminSecurity.LOCKOUT_DURATION_MS
                } else {
                    0L
                }
                ref.update(
                    mapOf(
                        "failedAttempts" to nextAttempts,
                        "lockedUntilMillis" to lockUntil,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
                val msg = if (lockUntil > 0L) {
                    "Too many failed attempts. Admin login locked for 5 minutes."
                } else {
                    val left = (AdminSecurity.MAX_FAILED_ATTEMPTS - nextAttempts).coerceAtLeast(0)
                    "Invalid administrator credentials. $left attempt(s) remaining before lockout."
                }
                return Result.failure(IllegalArgumentException(msg))
            }

            // Reset rate-limit counter on successful login and seed default catalog if empty
            ref.update(
                mapOf(
                    "failedAttempts" to 0,
                    "lockedUntilMillis" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            seedInitialAcademyDataIfEmpty()

            // Ensure current admin UID is registered in /admins/{uid}
            val adminGrantRef = db.collection("admins").document(uid)
            if (!adminGrantRef.get().await().exists()) {
                adminGrantRef.set(
                    mapOf(
                        "uid" to uid,
                        "email" to (auth.currentUser?.email ?: adminRecord.email),
                        "grantedByUid" to uid,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val session = AdminSession(
                sessionToken = AdminSecurity.generateSessionToken(),
                adminEmail = adminRecord.email,
                sessionVersion = adminRecord.sessionVersion,
                createdAtMillis = nowMillis,
                expiresAtMillis = nowMillis + AdminSecurity.SESSION_DURATION_MS
            )
            Result.success(session)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun changeAdminPassword(currentPassword: String, newPassword: String): Result<Unit> {
        val docPath = "administrators/primary"
        return try {
            requireAdminUserId()
            require(newPassword.length >= 6) { "New password must be at least 6 characters." }
            val ref = db.collection("administrators").document("primary")
            val record = ref.get().await().toObject(
                AdministratorRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ) ?: error("Administrator record not found.")

            val valid = AdminSecurity.verifyPassword(
                password = currentPassword,
                saltHex = record.passwordSalt,
                expectedHashHex = record.passwordHash,
                iterations = record.iterations
            )
            if (!valid) {
                return Result.failure(IllegalArgumentException("Current password is incorrect."))
            }

            val newSalt = AdminSecurity.generateSaltHex()
            val newHash = AdminSecurity.hashPasswordPbkdf2(newPassword, newSalt, AdminSecurity.DEFAULT_ITERATIONS)
            ref.update(
                mapOf(
                    "passwordSalt" to newSalt,
                    "passwordHash" to newHash,
                    "iterations" to AdminSecurity.DEFAULT_ITERATIONS,
                    "sessionVersion" to record.sessionVersion + 1,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeAllRegistrations(): Flow<List<RegistrationRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("registrations")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        RegistrationRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "registrations")
                    }
                    throw error
                }
        )
    }

    fun observeAllAttendance(): Flow<List<AttendanceRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("attendance")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        AttendanceRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "attendance")
                    }
                    throw error
                }
        )
    }

    fun observeAllResults(): Flow<List<ExamResultRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            emit(emptyList())
            return@flow
        }
        emitAll(
            db.collection("results")
                .snapshots()
                .map { snap ->
                    snap.toObjects(
                        ExamResultRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser != null) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, "results")
                    }
                    throw error
                }
        )
    }

    suspend fun updateRegistrationDetails(
        registrationId: String,
        studentName: String,
        fatherName: String,
        className: String,
        section: String,
        rollNumber: String,
        studentNumber: String,
        registrationNumber: String
    ): Result<Unit> {
        val path = "registrations/$registrationId"
        return try {
            requireAdminUserId()
            require(registrationId.isNotBlank())
            require(studentName.trim().length in 2..100)
            require(fatherName.trim().length in 2..100)
            db.collection("registrations").document(registrationId).update(
                mapOf(
                    "studentName" to studentName.trim(),
                    "fatherName" to fatherName.trim(),
                    "className" to className.trim(),
                    "section" to section.trim(),
                    "rollNumber" to rollNumber.trim(),
                    "studentNumber" to studentNumber.trim(),
                    "registrationNumber" to registrationNumber.trim(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteRegistration(registration: RegistrationRecord): Result<Unit> {
        val path = "registrations/${registration.id}"
        return try {
            requireAdminUserId()
            val batch = db.batch()
            batch.delete(db.collection("registrations").document(registration.id))
            if (registration.verificationId.isNotBlank()) {
                batch.delete(db.collection("qr_verifications").document(registration.verificationId))
            }
            if (registration.registrationToken.isNotBlank()) {
                batch.delete(db.collection("registration_tokens").document(registration.registrationToken))
            }
            registration.paperCodes.forEach { code ->
                batch.delete(db.collection("paper_codes").document(code))
            }
            registration.subjectIds.forEach { subId ->
                batch.delete(db.collection("registered_subjects").document("${registration.id}_$subId"))
            }
            batch.delete(db.collection("results").document(registration.id))
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun upsertSubject(subject: SubjectItem, isNew: Boolean): Result<Unit> {
        val id = if (isNew || subject.id.isBlank()) {
            "sub_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        } else subject.id
        val path = "subjects/$id"
        return try {
            requireAdminUserId()
            val cleanPrefix = subject.codePrefix.trim().uppercase().replace(Regex("[^A-Z0-9]"), "")
            require(subject.name.trim().isNotEmpty() && subject.name.trim().length <= 80) {
                "Subject name must be 1–80 characters."
            }
            require(cleanPrefix.length in 2..10) {
                "Paper code prefix must be 2–10 uppercase alphanumeric characters."
            }
            val now = FieldValue.serverTimestamp()
            val docRef = db.collection("subjects").document(id)
            if (isNew || !docRef.get().await().exists()) {
                docRef.set(
                    mapOf(
                        "name" to subject.name.trim(),
                        "codePrefix" to cleanPrefix,
                        "assignedClasses" to subject.assignedClasses.take(20),
                        "active" to subject.active,
                        "sortOrder" to subject.sortOrder.coerceIn(0, 1000),
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                ).await()
            } else {
                docRef.update(
                    mapOf(
                        "name" to subject.name.trim(),
                        "codePrefix" to cleanPrefix,
                        "assignedClasses" to subject.assignedClasses.take(20),
                        "active" to subject.active,
                        "sortOrder" to subject.sortOrder.coerceIn(0, 1000),
                        "updatedAt" to now
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun deleteSubject(subjectId: String): Result<Unit> {
        val path = "subjects/$subjectId"
        return try {
            requireAdminUserId()
            db.collection("subjects").document(subjectId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, path)
            Result.failure(e)
        }
    }

    suspend fun saveTiming(timing: ExaminationTiming): Result<Unit> {
        val id = timing.id.ifBlank { "slot_${timing.slotNumber}" }
        val path = "examination_timings/$id"
        return try {
            requireAdminUserId()
            require(timing.label.trim().isNotEmpty()) { "Timing label is required." }
            require(timing.startTime.trim().isNotEmpty() && timing.endTime.trim().isNotEmpty()) {
                "Start and end times are required."
            }
            val now = FieldValue.serverTimestamp()
            val ref = db.collection("examination_timings").document(id)
            if (!ref.get().await().exists()) {
                ref.set(
                    mapOf(
                        "slotNumber" to timing.slotNumber.coerceIn(1, 2),
                        "label" to timing.label.trim(),
                        "startTime" to timing.startTime.trim(),
                        "endTime" to timing.endTime.trim(),
                        "durationMinutes" to timing.durationMinutes.coerceIn(15, 240),
                        "instructions" to timing.instructions.trim().take(1000),
                        "active" to timing.active,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                ).await()
            } else {
                ref.update(
                    mapOf(
                        "slotNumber" to timing.slotNumber.coerceIn(1, 2),
                        "label" to timing.label.trim(),
                        "startTime" to timing.startTime.trim(),
                        "endTime" to timing.endTime.trim(),
                        "durationMinutes" to timing.durationMinutes.coerceIn(15, 240),
                        "instructions" to timing.instructions.trim().take(1000),
                        "active" to timing.active,
                        "updatedAt" to now
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun updateAcademySettings(settings: AcademySettings): Result<Unit> {
        val path = "academy_settings/main"
        return try {
            val uid = requireAdminUserId()
            val now = FieldValue.serverTimestamp()
            val ref = db.collection("academy_settings").document("main")
            val payload = mutableMapOf<String, Any?>(
                "academyName" to settings.academyName.trim().ifEmpty { "AWAN ACADEMY" }.take(120),
                "examHeading" to settings.examHeading.trim()
                    .ifEmpty { "Improvement Crash Half Length Registration" }.take(160),
                "instructions" to settings.instructions.trim().take(4000),
                "logoBase64" to settings.logoBase64?.take(380000),
                "stampBase64" to settings.stampBase64?.take(380000),
                "signatureBase64" to settings.signatureBase64?.take(380000),
                "updatedByUid" to uid,
                "updatedAt" to now
            )
            if (!ref.get().await().exists()) {
                payload["createdAt"] = now
                @Suppress("UNCHECKED_CAST")
                ref.set(payload.filterValues { it != null } as Map<String, Any>).await()
            } else {
                @Suppress("UNCHECKED_CAST")
                ref.update(payload.filterValues { it != null } as Map<String, Any>).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun findRegistrationByQrOrToken(code: String): Result<RegistrationRecord?> {
        val clean = code.removePrefix("AWA-VERIF:").trim()
        return try {
            requireAdminUserId()
            if (clean.isBlank()) return Result.success(null)

            // 1. Try qr_verifications/{clean}
            val qrSnap = db.collection("qr_verifications").document(clean).get().await()
            if (qrSnap.exists()) {
                val regId = qrSnap.getString("registrationId")
                if (!regId.isNullOrBlank()) {
                    val regSnap = db.collection("registrations").document(regId).get().await()
                    return Result.success(
                        regSnap.toObject(
                            RegistrationRecord::class.java,
                            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                        )
                    )
                }
            }

            // 2. Fallback: search by registrationToken
            val query = db.collection("registrations")
                .whereEqualTo("registrationToken", clean.uppercase())
                .limit(1)
                .get()
                .await()
            val reg = query.documents.firstOrNull()?.toObject(
                RegistrationRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
            Result.success(reg)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "qr_verifications/$clean")
            Result.failure(e)
        }
    }

    suspend fun markAttendance(
        registration: RegistrationRecord,
        status: String,
        allowUpdateExisting: Boolean = false
    ): Result<Unit> {
        val attendanceId = "${registration.id}_${registration.timingId}"
        val path = "attendance/$attendanceId"
        return try {
            val uid = requireAdminUserId()
            require(status in listOf("PRESENT", "ABSENT", "LATE")) { "Invalid attendance status." }
            val attRef = db.collection("attendance").document(attendanceId)
            val existing = attRef.get().await()
            if (existing.exists() && !allowUpdateExisting) {
                val prevStatus = existing.getString("status") ?: "MARKED"
                return Result.failure(
                    IllegalStateException(
                        "Attendance already recorded as $prevStatus for ${registration.registrationToken} (${registration.timingLabel})."
                    )
                )
            }

            val now = FieldValue.serverTimestamp()
            val batch = db.batch()
            if (!existing.exists()) {
                batch.set(
                    attRef,
                    mapOf(
                        "registrationId" to registration.id,
                        "registrationToken" to registration.registrationToken,
                        "verificationId" to registration.verificationId,
                        "studentName" to registration.studentName,
                        "className" to registration.className,
                        "section" to registration.section,
                        "timingId" to registration.timingId,
                        "timingLabel" to registration.timingLabel,
                        "status" to status,
                        "markedByUid" to uid,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )
            } else {
                batch.update(
                    attRef,
                    mapOf(
                        "status" to status,
                        "markedByUid" to uid,
                        "updatedAt" to now
                    )
                )
            }

            batch.update(
                db.collection("registrations").document(registration.id),
                mapOf("attendanceStatus" to status, "updatedAt" to now)
            )
            if (registration.verificationId.isNotBlank()) {
                batch.update(
                    db.collection("qr_verifications").document(registration.verificationId),
                    mapOf("attendanceStatus" to status, "updatedAt" to now)
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun saveExamResult(
        registration: RegistrationRecord,
        subjectResults: List<SubjectResultEntry>,
        remarks: String,
        resultStatus: String
    ): Result<ExamResultRecord> {
        val path = "results/${registration.id}"
        return try {
            val uid = requireAdminUserId()
            require(subjectResults.isNotEmpty()) { "At least one subject result is required." }
            val totalMax = subjectResults.sumOf { it.maxMarks.coerceAtLeast(0.0) }
            val totalObtained = subjectResults.sumOf { it.obtainedMarks.coerceAtLeast(0.0) }
            require(totalObtained <= totalMax) {
                "Obtained marks ($totalObtained) cannot exceed total marks ($totalMax)."
            }
            val pct = if (totalMax > 0.0) {
                ((totalObtained / totalMax) * 100.0).coerceIn(0.0, 100.0)
            } else 0.0

            val ref = db.collection("results").document(registration.id)
            val exists = ref.get().await().exists()
            val now = FieldValue.serverTimestamp()

            val serializedEntries = subjectResults.map { entry ->
                mapOf(
                    "subjectId" to entry.subjectId,
                    "subjectName" to entry.subjectName,
                    "paperCode" to entry.paperCode,
                    "maxMarks" to entry.maxMarks,
                    "obtainedMarks" to entry.obtainedMarks
                )
            }

            val batch = db.batch()
            if (!exists) {
                batch.set(
                    ref,
                    mapOf(
                        "registrationId" to registration.id,
                        "registrationToken" to registration.registrationToken,
                        "userId" to registration.userId,
                        "studentName" to registration.studentName,
                        "fatherName" to registration.fatherName,
                        "className" to registration.className,
                        "section" to registration.section,
                        "rollNumber" to registration.rollNumber,
                        "studentNumber" to registration.studentNumber,
                        "registrationNumber" to registration.registrationNumber,
                        "subjectResults" to serializedEntries,
                        "totalMaxMarks" to totalMax,
                        "totalObtainedMarks" to totalObtained,
                        "percentage" to pct,
                        "remarks" to remarks.trim().take(500),
                        "resultStatus" to resultStatus,
                        "updatedByUid" to uid,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )
            } else {
                batch.update(
                    ref,
                    mapOf(
                        "subjectResults" to serializedEntries,
                        "totalMaxMarks" to totalMax,
                        "totalObtainedMarks" to totalObtained,
                        "percentage" to pct,
                        "remarks" to remarks.trim().take(500),
                        "resultStatus" to resultStatus,
                        "updatedByUid" to uid,
                        "updatedAt" to now
                    )
                )
            }
            batch.update(
                db.collection("registrations").document(registration.id),
                mapOf("resultStatus" to resultStatus, "updatedAt" to now)
            )
            batch.commit().await()

            Result.success(
                ExamResultRecord(
                    id = registration.id,
                    registrationId = registration.id,
                    registrationToken = registration.registrationToken,
                    userId = registration.userId,
                    studentName = registration.studentName,
                    fatherName = registration.fatherName,
                    className = registration.className,
                    section = registration.section,
                    rollNumber = registration.rollNumber,
                    studentNumber = registration.studentNumber,
                    registrationNumber = registration.registrationNumber,
                    subjectResults = subjectResults,
                    totalMaxMarks = totalMax,
                    totalObtainedMarks = totalObtained,
                    percentage = pct,
                    remarks = remarks.trim().take(500),
                    resultStatus = resultStatus,
                    updatedByUid = uid
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }
}
