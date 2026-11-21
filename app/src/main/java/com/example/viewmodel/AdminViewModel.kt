package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AdminRepository
import com.example.model.AttendanceRecord
import com.example.model.ExamResultRecord
import com.example.model.ExaminationTiming
import com.example.model.RegistrationRecord
import com.example.model.SubjectItem
import com.example.model.SubjectResultEntry
import com.example.model.UiState
import com.example.security.AdminSession
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar

data class AdminDashboardStats(
    val totalRegistrations: Int = 0,
    val todayRegistrations: Int = 0,
    val totalUniqueStudents: Int = 0,
    val totalSelectedExaminations: Int = 0,
    val totalFeeCoveredPkr: Int = 0,
    val presentCount: Int = 0,
    val absentCount: Int = 0,
    val lateCount: Int = 0,
    val classWiseCounts: Map<String, Int> = emptyMap(),
    val subjectWiseCounts: Map<String, Int> = emptyMap()
)

data class AdminUiState(
    val isLoggingIn: Boolean = false,
    val loginError: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
    val isBusy: Boolean = false,
    val searchQuery: String = "",
    val classFilter: String = "All",
    val sectionFilter: String = "All",
    val subjectFilter: String = "All",
    val timingFilter: String = "All",
    val scannedRegistration: RegistrationRecord? = null,
    val scannerMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class AdminViewModel(
    private val adminRepository: AdminRepository
) : ViewModel() {

    private val _session = MutableStateFlow<AdminSession?>(null)
    val session: StateFlow<AdminSession?> = _session.asStateFlow()

    private val _uiState = MutableStateFlow(AdminUiState())
    val uiState: StateFlow<AdminUiState> = _uiState.asStateFlow()

    val allRegistrationsState: StateFlow<UiState<List<RegistrationRecord>>> = _session
        .flatMapLatest { activeSession ->
            if (activeSession == null || !activeSession.isValid()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                adminRepository.observeAllRegistrations()
                    .map<List<RegistrationRecord>, UiState<List<RegistrationRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing registrations", root)
                        emit(UiState.Error(root.message ?: "Failed to load registrations"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val allAttendanceState: StateFlow<List<AttendanceRecord>> = _session
        .flatMapLatest { activeSession ->
            if (activeSession == null || !activeSession.isValid()) {
                flowOf(emptyList())
            } else {
                adminRepository.observeAllAttendance()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing attendance", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val allResultsState: StateFlow<List<ExamResultRecord>> = _session
        .flatMapLatest { activeSession ->
            if (activeSession == null || !activeSession.isValid()) {
                flowOf(emptyList())
            } else {
                adminRepository.observeAllResults()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing results", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val stats: StateFlow<AdminDashboardStats> = allRegistrationsState
        .map { state ->
            val list = (state as? UiState.Success)?.data ?: emptyList()
            computeStats(list)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AdminDashboardStats())

    private fun computeStats(list: List<RegistrationRecord>): AdminDashboardStats {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDayMillis = cal.timeInMillis

        val todayCount = list.count { reg ->
            (reg.createdAt?.toDate()?.time ?: 0L) >= startOfDayMillis
        }
        val uniqueStudents = list.map { it.registrationNumber.trim().lowercase() }.toSet().size
        val totalExams = list.sumOf { it.subjectCount }
        val totalFee = list.sumOf { it.totalFeePkr }
        val present = list.count { it.attendanceStatus == "PRESENT" }
        val absent = list.count { it.attendanceStatus == "ABSENT" }
        val late = list.count { it.attendanceStatus == "LATE" }

        val byClass = list.groupingBy { it.className.ifBlank { "Other" } }.eachCount()
        val bySubject = mutableMapOf<String, Int>()
        list.forEach { reg ->
            reg.subjectNames.forEach { subj ->
                bySubject[subj] = (bySubject[subj] ?: 0) + 1
            }
        }
        return AdminDashboardStats(
            totalRegistrations = list.size,
            todayRegistrations = todayCount,
            totalUniqueStudents = uniqueStudents,
            totalSelectedExaminations = totalExams,
            totalFeeCoveredPkr = totalFee,
            presentCount = present,
            absentCount = absent,
            lateCount = late,
            classWiseCounts = byClass,
            subjectWiseCounts = bySubject.entries.sortedByDescending { it.value }.associate { it.toPair() }
        )
    }

    fun login(username: String, password: String, onSuccess: () -> Unit) {
        _uiState.update { it.copy(isLoggingIn = true, loginError = null) }
        viewModelScope.launch {
            adminRepository.authenticateAdmin(username, password).fold(
                onSuccess = { session ->
                    _session.value = session
                    _uiState.update { it.copy(isLoggingIn = false, loginError = null) }
                    onSuccess()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isLoggingIn = false, loginError = e.localizedMessage ?: "Authentication failed.")
                    }
                }
            )
        }
    }

    fun logoutAdmin() {
        _session.value = null
        _uiState.value = AdminUiState()
    }

    fun clearFeedback() = _uiState.update {
        it.copy(actionMessage = null, actionError = null, loginError = null, scannerMessage = null)
    }

    fun updateSearch(q: String) = _uiState.update { it.copy(searchQuery = q) }
    fun updateClassFilter(v: String) = _uiState.update { it.copy(classFilter = v) }
    fun updateSectionFilter(v: String) = _uiState.update { it.copy(sectionFilter = v) }
    fun updateSubjectFilter(v: String) = _uiState.update { it.copy(subjectFilter = v) }
    fun updateTimingFilter(v: String) = _uiState.update { it.copy(timingFilter = v) }

    fun updateRegistration(
        regId: String,
        studentName: String,
        fatherName: String,
        className: String,
        section: String,
        rollNumber: String,
        studentNumber: String,
        registrationNumber: String
    ) {
        runAction("Student details updated.") {
            adminRepository.updateRegistrationDetails(
                regId, studentName, fatherName, className, section, rollNumber, studentNumber, registrationNumber
            )
        }
    }

    fun deleteRegistration(reg: RegistrationRecord) {
        runAction("Registration ${reg.registrationToken} deleted.") {
            adminRepository.deleteRegistration(reg)
        }
    }

    fun saveSubject(subject: SubjectItem, isNew: Boolean) {
        runAction(if (isNew) "Subject '${subject.name}' added." else "Subject '${subject.name}' updated.") {
            adminRepository.upsertSubject(subject, isNew)
        }
    }

    fun toggleSubjectActive(subject: SubjectItem) {
        runAction("Subject '${subject.name}' ${if (!subject.active) "activated" else "deactivated"}.") {
            adminRepository.upsertSubject(subject.copy(active = !subject.active), isNew = false)
        }
    }

    fun deleteSubject(subjectId: String) {
        runAction("Subject deleted.") { adminRepository.deleteSubject(subjectId) }
    }

    fun saveTiming(timing: ExaminationTiming) {
        runAction("Timing Slot ${timing.slotNumber} saved.") { adminRepository.saveTiming(timing) }
    }

    fun updateSettings(settings: com.example.model.AcademySettings) {
        runAction("Academy settings and branding updated.") {
            adminRepository.updateAcademySettings(settings)
        }
    }

    fun changePassword(currentPwd: String, newPwd: String) {
        runAction("Administrator password updated securely.") {
            adminRepository.changeAdminPassword(currentPwd, newPwd)
        }
    }

    fun lookupQrOrToken(code: String) {
        _uiState.update { it.copy(isBusy = true, scannerMessage = null, scannedRegistration = null) }
        viewModelScope.launch {
            adminRepository.findRegistrationByQrOrToken(code).fold(
                onSuccess = { reg ->
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            scannedRegistration = reg,
                            scannerMessage = if (reg == null) "No registration found for '$code'." else null
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isBusy = false, scannerMessage = e.localizedMessage ?: "Lookup failed.")
                    }
                }
            )
        }
    }

    fun markAttendance(reg: RegistrationRecord, status: String, allowUpdate: Boolean = false) {
        _uiState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            adminRepository.markAttendance(reg, status, allowUpdate).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            scannedRegistration = reg.copy(attendanceStatus = status),
                            actionMessage = "Attendance marked $status for ${reg.registrationToken}."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isBusy = false, actionError = e.localizedMessage ?: "Attendance failed.")
                    }
                }
            )
        }
    }

    fun saveExamResult(
        reg: RegistrationRecord,
        entries: List<SubjectResultEntry>,
        remarks: String,
        status: String
    ) {
        _uiState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            adminRepository.saveExamResult(reg, entries, remarks, status).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isBusy = false,
                            actionMessage = "Result saved for ${reg.studentName} (${reg.registrationToken})."
                        )
                    }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isBusy = false, actionError = e.localizedMessage ?: "Failed to save result.")
                    }
                }
            )
        }
    }

    private fun runAction(successMessage: String, block: suspend () -> Result<Unit>) {
        _uiState.update { it.copy(isBusy = true, actionError = null, actionMessage = null) }
        viewModelScope.launch {
            block().fold(
                onSuccess = {
                    _uiState.update { it.copy(isBusy = false, actionMessage = successMessage) }
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(isBusy = false, actionError = e.localizedMessage ?: "Operation failed.")
                    }
                }
            )
        }
    }

    private companion object {
        const val TAG = "AdminVM"
    }
}
