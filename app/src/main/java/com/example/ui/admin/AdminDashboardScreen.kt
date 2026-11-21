package com.example.ui.admin

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FactCheck
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.DefaultCatalog
import com.example.model.ExamResultRecord
import com.example.model.RegistrationRecord
import com.example.model.UiState
import com.example.pdf.ExamPdfGenerator
import com.example.pdf.QrAndImageUtils
import com.example.ui.common.AcademyLogoImage
import com.example.ui.theme.NavyPrimary
import com.example.viewmodel.AdminViewModel
import com.example.viewmodel.StudentPortalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AdminSection(val label: String, val icon: ImageVector) {
    OVERVIEW("Overview", Icons.Filled.Dashboard),
    REGISTRATIONS("Applications", Icons.Filled.Assignment),
    ATTENDANCE("Attendance", Icons.Filled.FactCheck),
    RESULTS("Results", Icons.Filled.Grade),
    SUBJECTS("Subjects", Icons.Filled.MenuBook),
    TIMINGS("Timings", Icons.Filled.AccessTime),
    BRANDING("Branding", Icons.Filled.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(
    adminViewModel: AdminViewModel,
    studentViewModel: StudentPortalViewModel,
    onExitToStudentPortal: () -> Unit,
    onAdminLogout: () -> Unit
) {
    BackHandler(onBack = onExitToStudentPortal)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by adminViewModel.uiState.collectAsStateWithLifecycle()
    val stats by adminViewModel.stats.collectAsStateWithLifecycle()
    val regsState by adminViewModel.allRegistrationsState.collectAsStateWithLifecycle()
    val attendanceRecords by adminViewModel.allAttendanceState.collectAsStateWithLifecycle()
    val results by adminViewModel.allResultsState.collectAsStateWithLifecycle()

    val settings by studentViewModel.academySettings.collectAsStateWithLifecycle()
    val subjectsState by studentViewModel.subjectsState.collectAsStateWithLifecycle()
    val timingsState by studentViewModel.timingsState.collectAsStateWithLifecycle()

    val registrations = (regsState as? UiState.Success)?.data ?: emptyList()
    val subjects = (subjectsState as? UiState.Success)?.data ?: DefaultCatalog.DEFAULT_SUBJECTS
    val timings = (timingsState as? UiState.Success)?.data ?: DefaultCatalog.DEFAULT_TIMINGS

    var currentSection by remember { mutableStateOf(AdminSection.OVERVIEW) }

    LaunchedEffect(uiState.actionMessage, uiState.actionError) {
        val msg = uiState.actionMessage ?: uiState.actionError
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            adminViewModel.clearFeedback()
        }
    }

    fun exportRegPdf(reg: RegistrationRecord, isPackage: Boolean, printOnly: Boolean) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                if (isPackage) ExamPdfGenerator.generateCompleteExamPackagePdf(context, reg, settings)
                else ExamPdfGenerator.generateReceiptPdf(context, reg, settings)
            }
            if (printOnly) {
                QrAndImageUtils.printPdfFile(context, file, "AwanAcademy_${reg.registrationToken}")
            } else {
                val name = if (isPackage) "ExamPackage_${reg.registrationToken}.pdf"
                else "Receipt_${reg.registrationToken}.pdf"
                QrAndImageUtils.saveToPublicDownloads(context, file, name).onSuccess {
                    Toast.makeText(context, "Saved to $it", Toast.LENGTH_SHORT).show()
                }
                QrAndImageUtils.shareOrOpenPdf(context, file, name)
            }
        }
    }

    fun exportResultPdf(reg: RegistrationRecord, res: ExamResultRecord, printOnly: Boolean) {
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                ExamPdfGenerator.generateFilledResultCardPdf(context, reg, res, settings)
            }
            if (printOnly) {
                QrAndImageUtils.printPdfFile(context, file, "ResultCard_${reg.registrationToken}")
            } else {
                val name = "ResultCard_${reg.registrationToken}.pdf"
                QrAndImageUtils.saveToPublicDownloads(context, file, name).onSuccess {
                    Toast.makeText(context, "Saved to $it", Toast.LENGTH_SHORT).show()
                }
                QrAndImageUtils.shareOrOpenPdf(context, file, name)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = NavyPrimary,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onExitToStudentPortal,
                        modifier = Modifier.testTag("admin_back_to_portal_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Student Portal")
                    }
                },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AcademyLogoImage(settings = settings, size = 34.dp)
                        Column {
                            Text(
                                text = "Admin Dashboard",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = settings.academyName.ifBlank { "AWAN ACADEMY" },
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.75f)
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAdminLogout,
                        modifier = Modifier.testTag("admin_logout_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Lock Admin Session")
                    }
                }
            )
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val useRail = maxWidth >= 720.dp

            if (useRail) {
                Row(modifier = Modifier.fillMaxSize()) {
                    NavigationRail(modifier = Modifier.fillMaxHeight()) {
                        AdminSection.entries.forEach { section ->
                            NavigationRailItem(
                                selected = currentSection == section,
                                onClick = { currentSection = section },
                                icon = { Icon(section.icon, contentDescription = section.label) },
                                label = { Text(section.label) },
                                modifier = Modifier.testTag("admin_nav_${section.name.lowercase()}")
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        AdminTabContent(
                            section = currentSection,
                            adminViewModel = adminViewModel,
                            uiState = uiState,
                            stats = stats,
                            registrations = registrations,
                            attendanceRecords = attendanceRecords,
                            results = results,
                            subjects = subjects,
                            timings = timings,
                            settings = settings,
                            onExportRegPdf = ::exportRegPdf,
                            onExportResultPdf = ::exportResultPdf
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    ScrollableTabRow(
                        selectedTabIndex = currentSection.ordinal,
                        edgePadding = 8.dp
                    ) {
                        AdminSection.entries.forEach { section ->
                            Tab(
                                selected = currentSection == section,
                                onClick = { currentSection = section },
                                text = { Text(section.label) },
                                icon = { Icon(section.icon, contentDescription = null) },
                                modifier = Modifier.testTag("admin_tab_${section.name.lowercase()}")
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                        AdminTabContent(
                            section = currentSection,
                            adminViewModel = adminViewModel,
                            uiState = uiState,
                            stats = stats,
                            registrations = registrations,
                            attendanceRecords = attendanceRecords,
                            results = results,
                            subjects = subjects,
                            timings = timings,
                            settings = settings,
                            onExportRegPdf = ::exportRegPdf,
                            onExportResultPdf = ::exportResultPdf
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminTabContent(
    section: AdminSection,
    adminViewModel: AdminViewModel,
    uiState: com.example.viewmodel.AdminUiState,
    stats: com.example.viewmodel.AdminDashboardStats,
    registrations: List<RegistrationRecord>,
    attendanceRecords: List<com.example.model.AttendanceRecord>,
    results: List<ExamResultRecord>,
    subjects: List<com.example.model.SubjectItem>,
    timings: List<com.example.model.ExaminationTiming>,
    settings: com.example.model.AcademySettings,
    onExportRegPdf: (RegistrationRecord, Boolean, Boolean) -> Unit,
    onExportResultPdf: (RegistrationRecord, ExamResultRecord, Boolean) -> Unit
) {
    when (section) {
        AdminSection.OVERVIEW -> AdminOverviewTab(stats = stats)
        AdminSection.REGISTRATIONS -> AdminRegistrationsTab(
            registrations = registrations,
            uiState = uiState,
            onSearchChange = adminViewModel::updateSearch,
            onClassFilterChange = adminViewModel::updateClassFilter,
            onSectionFilterChange = adminViewModel::updateSectionFilter,
            onSubjectFilterChange = adminViewModel::updateSubjectFilter,
            onTimingFilterChange = adminViewModel::updateTimingFilter,
            onDownloadReceipt = { onExportRegPdf(it, false, false) },
            onDownloadPackage = { onExportRegPdf(it, true, false) },
            onPrintPackage = { onExportRegPdf(it, true, true) },
            onUpdateRegistration = adminViewModel::updateRegistration,
            onDeleteRegistration = adminViewModel::deleteRegistration
        )
        AdminSection.ATTENDANCE -> AdminAttendanceTab(
            scannedRegistration = uiState.scannedRegistration,
            scannerMessage = uiState.scannerMessage,
            attendanceRecords = attendanceRecords,
            onLookupCode = adminViewModel::lookupQrOrToken,
            onMarkAttendance = adminViewModel::markAttendance
        )
        AdminSection.RESULTS -> AdminResultsTab(
            registrations = registrations,
            results = results,
            onSaveResult = adminViewModel::saveExamResult,
            onDownloadResultPdf = { reg, res -> onExportResultPdf(reg, res, false) },
            onPrintResultPdf = { reg, res -> onExportResultPdf(reg, res, true) }
        )
        AdminSection.SUBJECTS -> AdminSubjectsTab(
            subjects = subjects,
            onSaveSubject = adminViewModel::saveSubject,
            onToggleActive = adminViewModel::toggleSubjectActive,
            onDeleteSubject = adminViewModel::deleteSubject
        )
        AdminSection.TIMINGS -> AdminTimingsTab(
            timings = timings,
            settings = settings,
            onSaveTiming = adminViewModel::saveTiming,
            onSaveInstructions = adminViewModel::updateSettings
        )
        AdminSection.BRANDING -> AdminBrandingSecurityTab(
            settings = settings,
            onUpdateSettings = adminViewModel::updateSettings,
            onChangePassword = adminViewModel::changePassword
        )
    }
}
