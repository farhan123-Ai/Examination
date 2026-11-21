package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@Keep
@IgnoreExtraProperties
data class AdministratorRecord(
    @DocumentId val id: String = "",
    val email: String = "",
    val passwordHash: String = "",
    val passwordSalt: String = "",
    val iterations: Int = 100_000,
    val failedAttempts: Int = 0,
    val lockedUntilMillis: Long = 0L,
    val sessionVersion: Int = 1,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AcademySettings(
    @DocumentId val id: String = "",
    val academyName: String = "AWAN ACADEMY",
    val examHeading: String = "Improvement Crash Half Length Registration",
    val instructions: String = DefaultCatalog.DEFAULT_EXAM_INSTRUCTIONS,
    val logoBase64: String? = null,
    val stampBase64: String? = null,
    val signatureBase64: String? = null,
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubjectItem(
    @DocumentId val id: String = "",
    val name: String = "",
    val codePrefix: String = "",
    val assignedClasses: List<String> = emptyList(),
    val active: Boolean = true,
    val sortOrder: Int = 0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExaminationTiming(
    @DocumentId val id: String = "",
    val slotNumber: Int = 1,
    val label: String = "",
    val startTime: String = "",
    val endTime: String = "",
    val durationMinutes: Int = 60,
    val instructions: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun formattedWindow(): String = "$label ($startTime - $endTime)"
}

@Keep
@IgnoreExtraProperties
data class RegistrationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val registrationToken: String = "",
    val verificationId: String = "",
    val studentName: String = "",
    val fatherName: String = "",
    val className: String = "",
    val section: String = "",
    val rollNumber: String = "",
    val studentNumber: String = "",
    val registrationNumber: String = "",
    val timingId: String = "",
    val timingLabel: String = "",
    val subjectIds: List<String> = emptyList(),
    val subjectNames: List<String> = emptyList(),
    val paperCodes: List<String> = emptyList(),
    val subjectCount: Int = 0,
    val totalFeePkr: Int = 0,
    val termsAccepted: Boolean = false,
    val attendanceStatus: String = "PENDING",
    val resultStatus: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun feeStatement(): String =
        if (subjectCount == 1) {
            "Your fee of PKR 200 has been paid by academy."
        } else {
            "Your improvement examination fee of PKR $totalFeePkr has been paid by Awan Academy."
        }
}

@Keep
@IgnoreExtraProperties
data class QrVerificationRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val registrationId: String = "",
    val registrationToken: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val timingId: String = "",
    val timingLabel: String = "",
    val subjectNames: List<String> = emptyList(),
    val paperCodes: List<String> = emptyList(),
    val attendanceStatus: String = "PENDING",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class AttendanceRecord(
    @DocumentId val id: String = "",
    val registrationId: String = "",
    val registrationToken: String = "",
    val verificationId: String = "",
    val studentName: String = "",
    val className: String = "",
    val section: String = "",
    val timingId: String = "",
    val timingLabel: String = "",
    val status: String = "PRESENT",
    val markedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class SubjectResultEntry(
    val subjectId: String = "",
    val subjectName: String = "",
    val paperCode: String = "",
    val maxMarks: Double = 0.0,
    val obtainedMarks: Double = 0.0
)

@Keep
@IgnoreExtraProperties
data class ExamResultRecord(
    @DocumentId val id: String = "",
    val registrationId: String = "",
    val registrationToken: String = "",
    val userId: String = "",
    val studentName: String = "",
    val fatherName: String = "",
    val className: String = "",
    val section: String = "",
    val rollNumber: String = "",
    val studentNumber: String = "",
    val registrationNumber: String = "",
    val subjectResults: List<SubjectResultEntry> = emptyList(),
    val totalMaxMarks: Double = 0.0,
    val totalObtainedMarks: Double = 0.0,
    val percentage: Double = 0.0,
    val remarks: String = "",
    val resultStatus: String = "PENDING",
    val updatedByUid: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

object DefaultCatalog {
    const val BOOTSTRAP_ADMIN_EMAIL = "muhammadqasim9021@gmail.com"
    const val BOOTSTRAP_ADMIN_SALT = "b87b1c97340c77a31de2b78c1fded40b"
    const val BOOTSTRAP_ADMIN_HASH = "62f0a199a6b945619feb89427d859e6631d75d6aefa3ea271f94f117cf96109f"
    const val BOOTSTRAP_ADMIN_ITERATIONS = 100_000

    const val EXAM_PACKAGE_HEADING = "IMPROVEMENT CRASH HALF LENGTH EXAMINATION"
    const val EXAM_POLICY_NOTICE =
        "One or more improvement examinations may be conducted together. The duration for completing each examination session is ONE HOUR."
    const val PARENT_CONSENT_STATEMENT =
        "I acknowledge that I have reviewed my child's examination performance and accept the examination review and result recorded by Awan Academy."
    const val DEFAULT_EXAM_INSTRUCTIONS =
        "1. Bring your printed Hall Ticket and Answer Sheet to the examination hall.\n" +
            "2. Arrive at least 15 minutes before the selected examination timing.\n" +
            "3. One or more improvement examinations may be conducted together within the 1-hour session.\n" +
            "4. Fill OMR bubbles completely using black or blue ballpoint pen only.\n" +
            "5. Follow all invigilator and Awan Academy examination instructions strictly."

    val AVAILABLE_CLASSES = listOf("9th", "10th", "11th", "12th")

    val TERMS_AND_CONDITIONS = listOf(
        "Students must provide accurate personal and academy information.",
        "Students must appear at the assigned examination time.",
        "One or more improvement examinations may be conducted together.",
        "The examination completion time is one hour.",
        "Students must bring their downloaded hall ticket and answer sheet.",
        "Students must follow academy examination instructions.",
        "The academy reserves the right to verify submitted registration details."
    )

    val DEFAULT_SUBJECTS: List<SubjectItem> = listOf(
        SubjectItem("sub_eng", "English", "ENG", listOf("9th", "10th", "11th", "12th"), true, 1),
        SubjectItem("sub_urd", "Urdu", "URD", listOf("9th", "10th", "11th", "12th"), true, 2),
        SubjectItem("sub_mth", "Mathematics", "MTH", listOf("9th", "10th", "11th", "12th"), true, 3),
        SubjectItem("sub_phy", "Physics", "PHY", listOf("9th", "10th", "11th", "12th"), true, 4),
        SubjectItem("sub_chm", "Chemistry", "CHM", listOf("9th", "10th", "11th", "12th"), true, 5),
        SubjectItem("sub_bio", "Biology", "BIO", listOf("9th", "10th", "11th", "12th"), true, 6),
        SubjectItem("sub_csc", "Computer Science", "CSC", listOf("9th", "10th", "11th", "12th"), true, 7),
        SubjectItem("sub_it", "Information Technology", "IT", listOf("9th", "10th", "11th", "12th"), true, 8),
        SubjectItem("sub_pst", "Pakistan Studies", "PST", listOf("9th", "10th", "11th", "12th"), true, 9),
        SubjectItem("sub_isl", "Islamic Studies", "ISL", listOf("9th", "10th", "11th", "12th"), true, 10),
        SubjectItem("sub_ttq", "Tarjuma-tul-Quran", "TTQ", listOf("9th", "10th", "11th", "12th"), true, 11),
        SubjectItem("sub_gsc", "General Science", "GSC", listOf("9th", "10th"), true, 12),
        SubjectItem("sub_gmt", "General Mathematics", "GMT", listOf("9th", "10th"), true, 13),
        SubjectItem("sub_sta", "Statistics", "STA", listOf("11th", "12th"), true, 14),
        SubjectItem("sub_eco", "Economics", "ECO", listOf("11th", "12th"), true, 15),
        SubjectItem("sub_acc", "Accounting", "ACC", listOf("11th", "12th"), true, 16),
        SubjectItem("sub_bus", "Business Studies", "BUS", listOf("11th", "12th"), true, 17)
    )

    val DEFAULT_TIMINGS: List<ExaminationTiming> = listOf(
        ExaminationTiming(
            id = "slot_1",
            slotNumber = 1,
            label = "Morning Examination Session",
            startTime = "09:00 AM",
            endTime = "10:00 AM",
            durationMinutes = 60,
            instructions = EXAM_POLICY_NOTICE,
            active = true
        ),
        ExaminationTiming(
            id = "slot_2",
            slotNumber = 2,
            label = "Afternoon Examination Session",
            startTime = "02:00 PM",
            endTime = "03:00 PM",
            durationMinutes = 60,
            instructions = EXAM_POLICY_NOTICE,
            active = true
        )
    )
}
