package com.example.data

import android.content.Context
import com.example.R
import com.example.model.AcademySettings
import com.example.model.DefaultCatalog
import com.example.model.ExamResultRecord
import com.example.model.ExaminationTiming
import com.example.model.QrVerificationRecord
import com.example.model.RegistrationRecord
import com.example.model.SubjectItem
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

data class RegistrationInput(
    val studentName: String,
    val fatherName: String,
    val className: String,
    val section: String,
    val rollNumber: String,
    val studentNumber: String,
    val registrationNumber: String,
    val timing: ExaminationTiming,
    val selectedSubjects: List<SubjectItem>,
    val termsAccepted: Boolean
)

class StudentRegistrationRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String =
        auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")

    fun observeAcademySettings(): Flow<AcademySettings> =
        db.collection("academy_settings").document("main")
            .snapshots()
            .map { snapshot ->
                if (snapshot.exists()) {
                    snapshot.toObject(AcademySettings::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        ?: AcademySettings(id = "main")
                } else {
                    AcademySettings(id = "main")
                }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.GET, "academy_settings/main")
                }
                emit(AcademySettings(id = "main"))
            }

    fun observeSubjects(): Flow<List<SubjectItem>> =
        db.collection("subjects")
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(
                    SubjectItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (list.isEmpty()) DefaultCatalog.DEFAULT_SUBJECTS
                else list.sortedBy { it.sortOrder }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "subjects")
                }
                throw error
            }

    fun observeTimings(): Flow<List<ExaminationTiming>> =
        db.collection("examination_timings")
            .snapshots()
            .map { snapshot ->
                val list = snapshot.toObjects(
                    ExaminationTiming::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
                if (list.isEmpty()) DefaultCatalog.DEFAULT_TIMINGS
                else list.sortedBy { it.slotNumber }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                if (auth.currentUser != null) {
                    handleFirestoreError(firestoreError ?: error, OperationType.LIST, "examination_timings")
                }
                throw error
            }

    fun observeMyRegistrations(userId: String): Flow<List<RegistrationRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "registrations"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        RegistrationRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeMyResults(userId: String): Flow<List<ExamResultRecord>> = flow {
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank() || uid != userId) {
            emit(emptyList())
            return@flow
        }
        val path = "results"
        emitAll(
            db.collection(path)
                .whereEqualTo("userId", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        ExamResultRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (auth.currentUser?.uid == uid) {
                        handleFirestoreError(firestoreError ?: error, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun getQrVerification(verificationId: String): Result<QrVerificationRecord?> {
        val cleanId = verificationId.removePrefix("AWA-VERIF:").trim()
        val path = "qr_verifications/$cleanId"
        return try {
            requireUserId()
            require(cleanId.isNotBlank()) { "Invalid verification code." }
            val snap = db.collection("qr_verifications").document(cleanId).get().await()
            val record = if (snap.exists()) {
                snap.toObject(QrVerificationRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else null
            Result.success(record)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, path)
            Result.failure(e)
        }
    }

    suspend fun submitRegistration(input: RegistrationInput): Result<RegistrationRecord> {
        val path = "registrations"
        return try {
            val uid = requireUserId()
            val studentName = input.studentName.trim()
            val fatherName = input.fatherName.trim()
            val className = input.className.trim()
            val section = input.section.trim()
            val rollNumber = input.rollNumber.trim()
            val studentNumber = input.studentNumber.trim()
            val registrationNumber = input.registrationNumber.trim()

            require(studentName.length in 2..100) { "Student name must be between 2 and 100 characters." }
            require(fatherName.length in 2..100) { "Father's name must be between 2 and 100 characters." }
            require(className.isNotEmpty() && className.length <= 40) { "Class is required." }
            require(section.isNotEmpty() && section.length <= 30) { "Section is required." }
            require(rollNumber.isNotEmpty() && rollNumber.length <= 40) { "Assigned Academy Roll Number is required." }
            require(studentNumber.isNotEmpty() && studentNumber.length <= 40) { "Assigned Academy Student Number is required." }
            require(registrationNumber.isNotEmpty() && registrationNumber.length <= 40) { "Assigned Academy Registration Number is required." }
            require(input.selectedSubjects.isNotEmpty() && input.selectedSubjects.size <= 20) {
                "Select between 1 and 20 subjects."
            }
            require(input.termsAccepted) { "You must accept the terms and conditions." }

            val regId = "reg_${UUID.randomUUID().toString().replace("-", "")}"
            val studentId = "stu_${UUID.randomUUID().toString().replace("-", "").take(20)}"
            val verificationId = "ver_${UUID.randomUUID().toString().replace("-", "").take(20)}"

            val regCounterRef = db.collection("counters").document("registration_2026")
            val paperCounterRef = db.collection("counters").document("paper_code_2026")

            val resultRecord = db.runTransaction { transaction ->
                // 1. ALL READS FIRST
                val regCounterSnap = transaction.get(regCounterRef)
                val paperCounterSnap = transaction.get(paperCounterRef)

                var nextRegSeq = ((regCounterSnap.getLong("currentSeq") ?: 0L) + 1L).toInt()
                var nextPaperSeq = ((paperCounterSnap.getLong("currentSeq") ?: 0L) + 1L).toInt()

                var tokenCandidate = "AWA-IMP-2026-${nextRegSeq.toString().padStart(5, '0')}"
                while (transaction.get(db.collection("registration_tokens").document(tokenCandidate)).exists()) {
                    nextRegSeq++
                    tokenCandidate = "AWA-IMP-2026-${nextRegSeq.toString().padStart(5, '0')}"
                }

                val generatedCodes = mutableListOf<String>()
                for (subject in input.selectedSubjects) {
                    val prefix = subject.codePrefix.uppercase().replace(Regex("[^A-Z0-9]"), "")
                        .ifBlank { "SUB" }.take(6)
                    var codeCandidate = "AWA-$prefix-26-${nextPaperSeq.toString().padStart(4, '0')}"
                    while (transaction.get(db.collection("paper_codes").document(codeCandidate)).exists()) {
                        nextPaperSeq++
                        codeCandidate = "AWA-$prefix-26-${nextPaperSeq.toString().padStart(4, '0')}"
                    }
                    generatedCodes.add(codeCandidate)
                    nextPaperSeq++
                }
                val finalPaperCounterSeq = nextPaperSeq - 1

                val subjectIds = input.selectedSubjects.map { it.id }
                val subjectNames = input.selectedSubjects.map { it.name }
                val timingLabel = input.timing.formattedWindow()
                val totalFeePkr = input.selectedSubjects.size * 200
                val now = FieldValue.serverTimestamp()

                // 2. ALL WRITES AFTER READS
                transaction.set(
                    regCounterRef,
                    mapOf("currentSeq" to nextRegSeq, "updatedAt" to now)
                )
                transaction.set(
                    paperCounterRef,
                    mapOf("currentSeq" to finalPaperCounterSeq, "updatedAt" to now)
                )

                transaction.set(
                    db.collection("registration_tokens").document(tokenCandidate),
                    mapOf(
                        "userId" to uid,
                        "registrationId" to regId,
                        "registrationToken" to tokenCandidate,
                        "createdAt" to now
                    )
                )

                input.selectedSubjects.forEachIndexed { index, subject ->
                    val code = generatedCodes[index]
                    transaction.set(
                        db.collection("paper_codes").document(code),
                        mapOf(
                            "userId" to uid,
                            "registrationId" to regId,
                            "registrationToken" to tokenCandidate,
                            "subjectId" to subject.id,
                            "subjectName" to subject.name,
                            "paperCode" to code,
                            "createdAt" to now
                        )
                    )
                    val itemDocId = "${regId}_${subject.id}"
                    transaction.set(
                        db.collection("registered_subjects").document(itemDocId),
                        mapOf(
                            "userId" to uid,
                            "registrationId" to regId,
                            "registrationToken" to tokenCandidate,
                            "subjectId" to subject.id,
                            "subjectName" to subject.name,
                            "paperCode" to code,
                            "className" to className,
                            "timingId" to input.timing.id,
                            "createdAt" to now
                        )
                    )
                }

                transaction.set(
                    db.collection("students").document(studentId),
                    mapOf(
                        "userId" to uid,
                        "studentName" to studentName,
                        "fatherName" to fatherName,
                        "className" to className,
                        "section" to section,
                        "rollNumber" to rollNumber,
                        "studentNumber" to studentNumber,
                        "registrationNumber" to registrationNumber,
                        "lastRegistrationToken" to tokenCandidate,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )

                transaction.set(
                    db.collection("registrations").document(regId),
                    mapOf(
                        "userId" to uid,
                        "registrationToken" to tokenCandidate,
                        "verificationId" to verificationId,
                        "studentName" to studentName,
                        "fatherName" to fatherName,
                        "className" to className,
                        "section" to section,
                        "rollNumber" to rollNumber,
                        "studentNumber" to studentNumber,
                        "registrationNumber" to registrationNumber,
                        "timingId" to input.timing.id,
                        "timingLabel" to timingLabel,
                        "subjectIds" to subjectIds,
                        "subjectNames" to subjectNames,
                        "paperCodes" to generatedCodes,
                        "subjectCount" to input.selectedSubjects.size,
                        "totalFeePkr" to totalFeePkr,
                        "termsAccepted" to true,
                        "attendanceStatus" to "PENDING",
                        "resultStatus" to "PENDING",
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )

                transaction.set(
                    db.collection("qr_verifications").document(verificationId),
                    mapOf(
                        "userId" to uid,
                        "registrationId" to regId,
                        "registrationToken" to tokenCandidate,
                        "studentName" to studentName,
                        "className" to className,
                        "section" to section,
                        "timingId" to input.timing.id,
                        "timingLabel" to timingLabel,
                        "subjectNames" to subjectNames,
                        "paperCodes" to generatedCodes,
                        "attendanceStatus" to "PENDING",
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                )

                RegistrationRecord(
                    id = regId,
                    userId = uid,
                    registrationToken = tokenCandidate,
                    verificationId = verificationId,
                    studentName = studentName,
                    fatherName = fatherName,
                    className = className,
                    section = section,
                    rollNumber = rollNumber,
                    studentNumber = studentNumber,
                    registrationNumber = registrationNumber,
                    timingId = input.timing.id,
                    timingLabel = timingLabel,
                    subjectIds = subjectIds,
                    subjectNames = subjectNames,
                    paperCodes = generatedCodes,
                    subjectCount = input.selectedSubjects.size,
                    totalFeePkr = totalFeePkr,
                    termsAccepted = true,
                    attendanceStatus = "PENDING",
                    resultStatus = "PENDING"
                )
            }.await()

            Result.success(resultRecord)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }
}
