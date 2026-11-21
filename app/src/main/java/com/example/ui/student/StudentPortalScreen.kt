package com.example.ui.student

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.AcademySettings
import com.example.model.DefaultCatalog
import com.example.model.ExaminationTiming
import com.example.model.RegistrationRecord
import com.example.model.SubjectItem
import com.example.model.UiState
import com.example.pdf.ExamPdfGenerator
import com.example.pdf.QrAndImageUtils
import com.example.ui.common.AcademyLogoImage
import com.example.ui.common.RegistrationSuccessCard
import com.example.ui.common.SectionHeading
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.NavyDeep
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.OnAmberContainer
import com.example.ui.theme.OnEmeraldContainer
import com.example.viewmodel.StudentPortalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StudentPortalScreen(
    viewModel: StudentPortalViewModel,
    isSignedIn: Boolean,
    onRequireAuth: (reason: String, onAuthenticated: () -> Unit) -> Unit,
    onOpenAdminLogin: () -> Unit,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val settings by viewModel.academySettings.collectAsStateWithLifecycle()
    val subjectsState by viewModel.subjectsState.collectAsStateWithLifecycle()
    val timingsState by viewModel.timingsState.collectAsStateWithLifecycle()
    val myRegistrationsState by viewModel.myRegistrationsState.collectAsStateWithLifecycle()
    val myResults by viewModel.myResultsState.collectAsStateWithLifecycle()
    val form by viewModel.formState.collectAsStateWithLifecycle()

    val allSubjects = (subjectsState as? UiState.Success)?.data ?: DefaultCatalog.DEFAULT_SUBJECTS
    val allTimings = (timingsState as? UiState.Success)?.data ?: DefaultCatalog.DEFAULT_TIMINGS
    val myRegistrations = (myRegistrationsState as? UiState.Success)?.data ?: emptyList()

    var showMyApplicationsSheet by remember { mutableStateOf(false) }
    var showQrLookupSheet by remember { mutableStateOf(false) }

    val activeTimings = remember(allTimings) {
        allTimings.filter { it.active }.sortedBy { it.slotNumber }.take(2)
    }

    val filteredSubjects = remember(allSubjects, form.className, form.subjectSearchQuery) {
        allSubjects.filter { subject ->
            subject.active &&
                (subject.assignedClasses.isEmpty() ||
                    subject.assignedClasses.any { it.equals(form.className.trim(), ignoreCase = true) }) &&
                (form.subjectSearchQuery.isBlank() ||
                    subject.name.contains(form.subjectSearchQuery, ignoreCase = true) ||
                    subject.codePrefix.contains(form.subjectSearchQuery, ignoreCase = true))
        }
    }

    val selectedSubjectsList = remember(allSubjects, form.selectedSubjectIds) {
        allSubjects.filter { it.id in form.selectedSubjectIds && it.active }
    }

    fun exportPdfAction(
        reg: RegistrationRecord,
        isPackage: Boolean,
        printOnly: Boolean = false
    ) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                if (isPackage) ExamPdfGenerator.generateCompleteExamPackagePdf(context, reg, settings)
                else ExamPdfGenerator.generateReceiptPdf(context, reg, settings)
            }
            if (printOnly) {
                QrAndImageUtils.printPdfFile(context, file, "AwanAcademy_${reg.registrationToken}")
            } else {
                val dlName = if (isPackage) "AwanAcademy_ExamPackage_${reg.registrationToken}.pdf"
                else "AwanAcademy_Receipt_${reg.registrationToken}.pdf"
                QrAndImageUtils.saveToPublicDownloads(context, file, dlName).onSuccess { savedPath ->
                    Toast.makeText(context, "Saved to $savedPath", Toast.LENGTH_LONG).show()
                }
                QrAndImageUtils.shareOrOpenPdf(context, file, dlName)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AcademyLogoImage(settings = settings, size = 38.dp)
                        Column {
                            Text(
                                text = settings.academyName.ifBlank { "AWAN ACADEMY" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Education & Learning",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.78f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isSignedIn) {
                                showQrLookupSheet = true
                            } else {
                                onRequireAuth("Sign in with Google to verify your QR registration token.") {
                                    showQrLookupSheet = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("open_qr_lookup_button")
                    ) {
                        Icon(Icons.Filled.QrCode2, contentDescription = "Verify QR Registration")
                    }
                    IconButton(
                        onClick = {
                            if (isSignedIn) {
                                showMyApplicationsSheet = true
                            } else {
                                onRequireAuth("Sign in with Google to view your saved registrations, receipts, and results.") {
                                    showMyApplicationsSheet = true
                                }
                            }
                        },
                        modifier = Modifier.testTag("my_registrations_button")
                    ) {
                        BadgedBox(
                            badge = {
                                if (myRegistrations.isNotEmpty()) {
                                    Badge { Text("${myRegistrations.size}") }
                                }
                            }
                        ) {
                            Icon(Icons.Filled.FolderShared, contentDescription = "My Saved Applications")
                        }
                    }
                    if (isSignedIn) {
                        IconButton(
                            onClick = onSignOut,
                            modifier = Modifier.testTag("student_sign_out_button")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign Out")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 840.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Hero Banner
                Surface(
                    shape = MaterialTheme.shapes.large,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NavyDeep, NavyPrimary, CobaltAccent)
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            AcademyLogoImage(settings = settings, size = 64.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = settings.examHeading.ifBlank {
                                        "Improvement Crash Half Length Registration"
                                    },
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Complete your registration to receive an instant Registration Token, Subject Paper Codes, Fee Receipt & 14-Page Printable Exam Package.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.88f)
                                )
                            }
                        }
                    }
                }

                // Important Notice Banner
                Surface(
                    color = AmberContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = OnAmberContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "IMPORTANT EXAMINATION NOTICE",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = OnAmberContainer
                            )
                            Text(
                                text = DefaultCatalog.EXAM_POLICY_NOTICE,
                                style = MaterialTheme.typography.bodySmall,
                                color = OnAmberContainer
                            )
                        }
                    }
                }

                // Success View OR Registration Form
                val submitted = form.submittedRegistration
                if (submitted != null) {
                    RegistrationSuccessCard(
                        registration = submitted,
                        onDownloadReceipt = { exportPdfAction(submitted, isPackage = false) },
                        onDownloadPackage = { exportPdfAction(submitted, isPackage = true) },
                        onPrintPackage = { exportPdfAction(submitted, isPackage = true, printOnly = true) },
                        onRegisterAnother = { viewModel.resetAfterSuccess() }
                    )
                } else {
                    // SECTION 1: STUDENT & ACADEMY DETAILS
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeading(
                                icon = Icons.Filled.Person,
                                title = "1. Student & Academy Information",
                                subtitle = "All 7 identification fields are mandatory for Hall Ticket generation"
                            )

                            OutlinedTextField(
                                value = form.studentName,
                                onValueChange = viewModel::updateStudentName,
                                label = { Text("1. Student Name *") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("student_name_input")
                            )

                            OutlinedTextField(
                                value = form.fatherName,
                                onValueChange = viewModel::updateFatherName,
                                label = { Text("4. Father's Name *") },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("father_name_input")
                            )

                            Text(
                                text = "2. Select Class *",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                DefaultCatalog.AVAILABLE_CLASSES.forEach { cls ->
                                    FilterChip(
                                        selected = form.className == cls,
                                        onClick = { viewModel.updateClassName(cls) },
                                        label = { Text(cls) },
                                        modifier = Modifier.testTag("class_chip_$cls")
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = form.className,
                                    onValueChange = viewModel::updateClassName,
                                    label = { Text("Class *") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("class_input")
                                )
                                OutlinedTextField(
                                    value = form.section,
                                    onValueChange = viewModel::updateSection,
                                    label = { Text("3. Section *") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("section_input")
                                )
                            }

                            OutlinedTextField(
                                value = form.rollNumber,
                                onValueChange = viewModel::updateRollNumber,
                                label = { Text("5. Assigned Academy Roll Number *") },
                                leadingIcon = { Icon(Icons.Filled.Badge, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("roll_number_input")
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = form.studentNumber,
                                    onValueChange = viewModel::updateStudentNumber,
                                    label = { Text("6. Student No. *") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("student_number_input")
                                )
                                OutlinedTextField(
                                    value = form.registrationNumber,
                                    onValueChange = viewModel::updateRegistrationNumber,
                                    label = { Text("7. Academy Reg No. *") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("registration_number_input")
                                )
                            }
                        }
                    }

                    // SECTION 2: DYNAMIC SUBJECT SELECTION & PAPER CODE PREVIEW
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeading(
                                icon = Icons.Filled.MenuBook,
                                title = "2. Subject Selection & Automatic Paper Codes",
                                subtitle = "Select one or multiple subjects for Class ${form.className.ifBlank { "All" }}"
                            )

                            OutlinedTextField(
                                value = form.subjectSearchQuery,
                                onValueChange = viewModel::updateSubjectSearch,
                                label = { Text("Search subjects...") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("subject_search_input")
                            )

                            if (filteredSubjects.isEmpty()) {
                                Text(
                                    text = "No matching active subjects for the selected class or filter.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    filteredSubjects.forEach { subject ->
                                        val isSelected = subject.id in form.selectedSubjectIds
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = { viewModel.toggleSubject(subject.id) },
                                            leadingIcon = if (isSelected) {
                                                {
                                                    Icon(
                                                        Icons.Filled.Check,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            } else null,
                                            label = { Text("${subject.name} (${subject.codePrefix})") },
                                            modifier = Modifier.testTag("subject_chip_${subject.codePrefix}")
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(visible = selectedSubjectsList.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    HorizontalDivider()
                                    Text(
                                        text = "Immediate Paper Code Allocation Preview (${selectedSubjectsList.size} selected)",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    selectedSubjectsList.forEachIndexed { idx, subj ->
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                                            shape = MaterialTheme.shapes.small,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(subj.name, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    text = "AWA-${subj.codePrefix}-26-AUTO#${idx + 1}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }

                                    val totalFee = selectedSubjectsList.size * 200
                                    Surface(
                                        color = EmeraldContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = if (selectedSubjectsList.size == 1) {
                                                "Total Fee: PKR 200 • Your fee of PKR 200 has been paid by academy."
                                            } else {
                                                "Total Fee: PKR $totalFee • Your improvement examination fee of PKR $totalFee has been paid by Awan Academy."
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = OnEmeraldContainer,
                                            modifier = Modifier.padding(10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // SECTION 3: EXAMINATION TIMINGS (EXACTLY TWO SLOTS)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            SectionHeading(
                                icon = Icons.Filled.AccessTime,
                                title = "3. Examination Timing Selection",
                                subtitle = "Choose one of the two available examination sessions"
                            )

                            activeTimings.forEach { timing ->
                                val selected = form.selectedTimingId == timing.id
                                Surface(
                                    shape = MaterialTheme.shapes.medium,
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(
                                            width = if (selected) 2.dp else 1.dp,
                                            color = if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outlineVariant,
                                            shape = MaterialTheme.shapes.medium
                                        )
                                        .clickable { viewModel.selectTiming(timing.id) }
                                        .minimumInteractiveComponentSize()
                                        .testTag("timing_slot_${timing.slotNumber}")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        RadioButton(
                                            selected = selected,
                                            onClick = { viewModel.selectTiming(timing.id) }
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = timing.label,
                                                style = MaterialTheme.typography.titleSmall,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "${timing.startTime} – ${timing.endTime} (${timing.durationMinutes} mins)",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(visible = selectedSubjectsList.size > 1) {
                                Surface(
                                    color = AmberContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Multi-Subject Scheduling Policy: ${DefaultCatalog.EXAM_POLICY_NOTICE}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = OnAmberContainer,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // SECTION 4: TERMS AND CONDITIONS + SUBMIT
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            SectionHeading(
                                icon = Icons.Filled.Gavel,
                                title = "4. Terms & Conditions",
                                subtitle = "Please review and accept before submitting your registration"
                            )

                            DefaultCatalog.TERMS_AND_CONDITIONS.forEachIndexed { idx, term ->
                                Text(
                                    text = "${idx + 1}. $term",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.setTermsAccepted(!form.termsAccepted) }
                                    .minimumInteractiveComponentSize()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = form.termsAccepted,
                                    onCheckedChange = viewModel::setTermsAccepted,
                                    modifier = Modifier.testTag("terms_checkbox")
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "I have read and agree to the terms and conditions.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (!form.submitError.isNullOrBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = form.submitError.orEmpty(),
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            Button(
                                onClick = {
                                    if (isSignedIn) {
                                        viewModel.submitRegistration(allSubjects, activeTimings)
                                    } else {
                                        onRequireAuth(
                                            "Sign in with Google to submit your registration, generate your permanent token and paper codes, and save your official receipt."
                                        ) {
                                            viewModel.submitRegistration(allSubjects, activeTimings)
                                        }
                                    }
                                },
                                enabled = form.isFormValid && !form.isSubmitting,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("submit_registration_button"),
                                shape = MaterialTheme.shapes.medium
                            ) {
                                if (form.isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(22.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.5.dp
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Generating Token, Paper Codes & PDF...")
                                } else {
                                    Icon(Icons.Filled.AssignmentTurnedIn, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = "SUBMIT REGISTRATION",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                            }
                        }
                    }
                }

                // FOOTER WITH HIDDEN "!" ADMIN ACCESS BUTTON
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "© 2026 ${settings.academyName.ifBlank { "AWAN ACADEMY" }} • Examination Management Portal",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    TextButton(
                        onClick = {
                            if (isSignedIn) {
                                onOpenAdminLogin()
                            } else {
                                onRequireAuth("Sign in with the authorized administrator Google account to unlock the Admin Portal.") {
                                    onOpenAdminLogin()
                                }
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("hidden_admin_button")
                    ) {
                        Text(
                            text = "!",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }

    if (showMyApplicationsSheet) {
        StudentRegistrationsSheet(
            registrations = myRegistrations,
            results = myResults,
            onDismiss = { showMyApplicationsSheet = false },
            onDownloadReceipt = { reg -> exportPdfAction(reg, isPackage = false) },
            onDownloadPackage = { reg -> exportPdfAction(reg, isPackage = true) },
            onPrintPackage = { reg -> exportPdfAction(reg, isPackage = true, printOnly = true) },
            onDownloadResultCard = { reg, res ->
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        ExamPdfGenerator.generateFilledResultCardPdf(context, reg, res, settings)
                    }
                    QrAndImageUtils.saveToPublicDownloads(context, file, "ResultCard_${reg.registrationToken}.pdf")
                    QrAndImageUtils.shareOrOpenPdf(context, file, "ResultCard_${reg.registrationToken}.pdf")
                }
            }
        )
    }

    if (showQrLookupSheet) {
        StudentQrVerificationSheet(
            verificationRecord = form.verificationLookupResult,
            isLoading = form.isLookingUpQr,
            errorMessage = form.verificationError,
            onLookup = { viewModel.lookupVerification(it) },
            onDismiss = {
                viewModel.dismissError()
                showQrLookupSheet = false
            }
        )
    }
}
