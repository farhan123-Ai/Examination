package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.RegistrationInput
import com.example.data.StudentRegistrationRepository
import com.example.model.AcademySettings
import com.example.model.DefaultCatalog
import com.example.model.ExamResultRecord
import com.example.model.ExaminationTiming
import com.example.model.QrVerificationRecord
import com.example.model.RegistrationRecord
import com.example.model.SubjectItem
import com.example.model.UiState
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

data class StudentFormState(
    val studentName: String = "",
    val className: String = "10th",
    val section: String = "",
    val fatherName: String = "",
    val rollNumber: String = "",
    val studentNumber: String = "",
    val registrationNumber: String = "",
    val subjectSearchQuery: String = "",
    val selectedSubjectIds: Set<String> = emptySet(),
    val selectedTimingId: String = "slot_1",
    val termsAccepted: Boolean = false,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submittedRegistration: RegistrationRecord? = null,
    val verificationLookupResult: QrVerificationRecord? = null,
    val verificationError: String? = null,
    val isLookingUpQr: Boolean = false
) {
    val isFormValid: Boolean
        get() = studentName.trim().length >= 2 &&
            fatherName.trim().length >= 2 &&
            className.trim().isNotEmpty() &&
            section.trim().isNotEmpty() &&
            rollNumber.trim().isNotEmpty() &&
            studentNumber.trim().isNotEmpty() &&
            registrationNumber.trim().isNotEmpty() &&
            selectedSubjectIds.isNotEmpty() &&
            selectedTimingId.isNotEmpty() &&
            termsAccepted
}

@OptIn(ExperimentalCoroutinesApi::class)
class StudentPortalViewModel(
    private val repository: StudentRegistrationRepository
) : ViewModel() {

    private val _authUserId = MutableStateFlow<String?>(null)
    val authUserId: StateFlow<String?> = _authUserId.asStateFlow()

    fun setAuthenticatedUserId(uid: String?) {
        val previous = _authUserId.value
        _authUserId.value = uid
        if (previous != null && uid == null) {
            _formState.update {
                it.copy(
                    submittedRegistration = null,
                    verificationLookupResult = null,
                    verificationError = null,
                    submitError = null
                )
            }
        }
    }

    val academySettings: StateFlow<AcademySettings> = _authUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(AcademySettings(id = "main"))
            } else {
                repository.observeAcademySettings()
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing academy settings", firestoreError ?: error)
                        emit(AcademySettings(id = "main"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), AcademySettings(id = "main"))

    val subjectsState: StateFlow<UiState<List<SubjectItem>>> = _authUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Success(DefaultCatalog.DEFAULT_SUBJECTS))
            } else {
                repository.observeSubjects()
                    .map<List<SubjectItem>, UiState<List<SubjectItem>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing subjects", firestoreError ?: error)
                        emit(UiState.Success(DefaultCatalog.DEFAULT_SUBJECTS))
                    }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            UiState.Success(DefaultCatalog.DEFAULT_SUBJECTS)
        )

    val timingsState: StateFlow<UiState<List<ExaminationTiming>>> = _authUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Success(DefaultCatalog.DEFAULT_TIMINGS))
            } else {
                repository.observeTimings()
                    .map<List<ExaminationTiming>, UiState<List<ExaminationTiming>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing timings", firestoreError ?: error)
                        emit(UiState.Success(DefaultCatalog.DEFAULT_TIMINGS))
                    }
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000L),
            UiState.Success(DefaultCatalog.DEFAULT_TIMINGS)
        )

    val myRegistrationsState: StateFlow<UiState<List<RegistrationRecord>>> = _authUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(UiState.Success(emptyList()))
            } else {
                repository.observeMyRegistrations(uid)
                    .map<List<RegistrationRecord>, UiState<List<RegistrationRecord>>> { UiState.Success(it) }
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        val root = firestoreError ?: error
                        Log.w(TAG, "Error observing my registrations", root)
                        emit(UiState.Error(root.message ?: "Failed to load registrations"))
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Success(emptyList()))

    val myResultsState: StateFlow<List<ExamResultRecord>> = _authUserId
        .flatMapLatest { uid ->
            if (uid.isNullOrBlank()) {
                flowOf(emptyList())
            } else {
                repository.observeMyResults(uid)
                    .catch { error ->
                        val firestoreError = generateSequence(error) { it.cause }
                            .filterIsInstance<FirebaseFirestoreException>()
                            .firstOrNull()
                        if (firestoreError == null && error is CancellationException) throw error
                        Log.w(TAG, "Error observing my results", firestoreError ?: error)
                        emit(emptyList())
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    private val _formState = MutableStateFlow(StudentFormState())
    val formState: StateFlow<StudentFormState> = _formState.asStateFlow()

    fun updateStudentName(v: String) = _formState.update { it.copy(studentName = v, submitError = null) }
    fun updateClassName(v: String) = _formState.update {
        it.copy(className = v, selectedSubjectIds = emptySet(), submitError = null)
    }
    fun updateSection(v: String) = _formState.update { it.copy(section = v, submitError = null) }
    fun updateFatherName(v: String) = _formState.update { it.copy(fatherName = v, submitError = null) }
    fun updateRollNumber(v: String) = _formState.update { it.copy(rollNumber = v, submitError = null) }
    fun updateStudentNumber(v: String) = _formState.update { it.copy(studentNumber = v, submitError = null) }
    fun updateRegistrationNumber(v: String) = _formState.update { it.copy(registrationNumber = v, submitError = null) }
    fun updateSubjectSearch(v: String) = _formState.update { it.copy(subjectSearchQuery = v) }

    fun toggleSubject(subjectId: String) {
        _formState.update { state ->
            val updated = if (subjectId in state.selectedSubjectIds) {
                state.selectedSubjectIds - subjectId
            } else {
                state.selectedSubjectIds + subjectId
            }
            state.copy(selectedSubjectIds = updated, submitError = null)
        }
    }

    fun selectTiming(timingId: String) = _formState.update { it.copy(selectedTimingId = timingId, submitError = null) }
    fun setTermsAccepted(accepted: Boolean) = _formState.update { it.copy(termsAccepted = accepted, submitError = null) }

    fun resetAfterSuccess() {
        _formState.value = StudentFormState()
    }

    fun dismissError() {
        _formState.update { it.copy(submitError = null, verificationError = null) }
    }

    fun submitRegistration(
        availableSubjects: List<SubjectItem>,
        availableTimings: List<ExaminationTiming>
    ) {
        val current = _formState.value
        val selectedSubjects = availableSubjects.filter { it.id in current.selectedSubjectIds && it.active }
        val selectedTiming = availableTimings.firstOrNull { it.id == current.selectedTimingId && it.active }
            ?: availableTimings.firstOrNull { it.active }

        if (!current.isFormValid || selectedSubjects.isEmpty() || selectedTiming == null) {
            _formState.update {
                it.copy(submitError = "Please complete all required fields, select at least one subject, choose an examination timing, and accept the terms.")
            }
            return
        }

        _formState.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            val result = repository.submitRegistration(
                RegistrationInput(
                    studentName = current.studentName,
                    fatherName = current.fatherName,
                    className = current.className,
                    section = current.section,
                    rollNumber = current.rollNumber,
                    studentNumber = current.studentNumber,
                    registrationNumber = current.registrationNumber,
                    timing = selectedTiming,
                    selectedSubjects = selectedSubjects,
                    termsAccepted = current.termsAccepted
                )
            )
            result.fold(
                onSuccess = { reg ->
                    _formState.update {
                        it.copy(isSubmitting = false, submittedRegistration = reg, submitError = null)
                    }
                },
                onFailure = { e ->
                    _formState.update {
                        it.copy(isSubmitting = false, submitError = e.localizedMessage ?: "Registration failed.")
                    }
                }
            )
        }
    }

    fun lookupVerification(code: String) {
        _formState.update { it.copy(isLookingUpQr = true, verificationError = null, verificationLookupResult = null) }
        viewModelScope.launch {
            repository.getQrVerification(code).fold(
                onSuccess = { record ->
                    _formState.update {
                        it.copy(
                            isLookingUpQr = false,
                            verificationLookupResult = record,
                            verificationError = if (record == null) "No registration found for verification code." else null
                        )
                    }
                },
                onFailure = { e ->
                    _formState.update {
                        it.copy(isLookingUpQr = false, verificationError = e.localizedMessage ?: "Verification lookup failed.")
                    }
                }
            )
        }
    }

    private companion object {
        const val TAG = "StudentPortalVM"
    }
}
