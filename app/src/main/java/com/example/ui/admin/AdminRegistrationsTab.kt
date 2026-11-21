package com.example.ui.admin

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.export.CsvExporter
import com.example.model.RegistrationRecord
import com.example.ui.common.QrCodeImage
import com.example.ui.common.StatusPill
import com.example.viewmodel.AdminUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminRegistrationsTab(
    registrations: List<RegistrationRecord>,
    uiState: AdminUiState,
    onSearchChange: (String) -> Unit,
    onClassFilterChange: (String) -> Unit,
    onSectionFilterChange: (String) -> Unit,
    onSubjectFilterChange: (String) -> Unit,
    onTimingFilterChange: (String) -> Unit,
    onDownloadReceipt: (RegistrationRecord) -> Unit,
    onDownloadPackage: (RegistrationRecord) -> Unit,
    onPrintPackage: (RegistrationRecord) -> Unit,
    onUpdateRegistration: (String, String, String, String, String, String, String, String) -> Unit,
    onDeleteRegistration: (RegistrationRecord) -> Unit
) {
    val context = LocalContext.current
    var inspectingReg by remember { mutableStateOf<RegistrationRecord?>(null) }
    var editingReg by remember { mutableStateOf<RegistrationRecord?>(null) }
    var deletingReg by remember { mutableStateOf<RegistrationRecord?>(null) }

    val classOptions = remember(registrations) {
        listOf("All") + registrations.map { it.className }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val sectionOptions = remember(registrations) {
        listOf("All") + registrations.map { it.section }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val subjectOptions = remember(registrations) {
        listOf("All") + registrations.flatMap { it.subjectNames }.distinct().sorted()
    }
    val timingOptions = remember(registrations) {
        listOf("All") + registrations.map { it.timingLabel }.filter { it.isNotBlank() }.distinct().sorted()
    }

    val filtered = remember(registrations, uiState) {
        registrations.filter { reg ->
            val q = uiState.searchQuery.trim()
            val matchesSearch = q.isBlank() ||
                reg.studentName.contains(q, true) ||
                reg.fatherName.contains(q, true) ||
                reg.registrationToken.contains(q, true) ||
                reg.rollNumber.contains(q, true) ||
                reg.studentNumber.contains(q, true) ||
                reg.registrationNumber.contains(q, true) ||
                reg.paperCodes.any { it.contains(q, true) }
            val matchesClass = uiState.classFilter == "All" || reg.className == uiState.classFilter
            val matchesSection = uiState.sectionFilter == "All" || reg.section == uiState.sectionFilter
            val matchesSubject = uiState.subjectFilter == "All" || reg.subjectNames.contains(uiState.subjectFilter)
            val matchesTiming = uiState.timingFilter == "All" || reg.timingLabel == uiState.timingFilter
            matchesSearch && matchesClass && matchesSection && matchesSubject && matchesTiming
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.US) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Registrations (${filtered.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Search, filter, print, edit, or export records",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    val file = CsvExporter.exportRegistrationsCsv(context, filtered)
                    CsvExporter.shareCsvFile(context, file, "Export Registrations CSV")
                },
                enabled = filtered.isNotEmpty(),
                modifier = Modifier.testTag("export_registrations_csv_button")
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Export CSV")
            }
        }

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Search name, token, roll no, paper code...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_registration_search_input")
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterDropdown("Class", uiState.classFilter, classOptions, onClassFilterChange)
            FilterDropdown("Section", uiState.sectionFilter, sectionOptions, onSectionFilterChange)
            FilterDropdown("Subject", uiState.subjectFilter, subjectOptions, onSubjectFilterChange)
            FilterDropdown("Timing", uiState.timingFilter, timingOptions, onTimingFilterChange)
        }

        if (filtered.isEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "No registrations match the current search or filter criteria.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                items(filtered, key = { it.id }) { reg ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = reg.registrationToken,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            fontFamily = MaterialTheme.typography.labelSmall.fontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${reg.studentName} (S/D of ${reg.fatherName})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                StatusPill(reg.attendanceStatus)
                            }

                            Text(
                                text = "Class: ${reg.className}-${reg.section} | Roll: ${reg.rollNumber} | Stu#: ${reg.studentNumber} | Reg#: ${reg.registrationNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Timing: ${reg.timingLabel} • Date: ${dateFormatter.format(reg.createdAt?.toDate() ?: Date())}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = reg.subjectNames.zip(reg.paperCodes)
                                    .joinToString(" | ") { (s, c) -> "$s [$c]" },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            HorizontalDivider()

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(onClick = { inspectingReg = reg }) {
                                    Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("View")
                                }
                                OutlinedButton(onClick = { onDownloadReceipt(reg) }) {
                                    Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Receipt")
                                }
                                Button(onClick = { onDownloadPackage(reg) }) {
                                    Icon(Icons.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("14-Pg PDF")
                                }
                                IconButton(onClick = { onPrintPackage(reg) }) {
                                    Icon(Icons.Filled.Print, contentDescription = "Print Application")
                                }
                                IconButton(onClick = { editingReg = reg }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit Student Details")
                                }
                                IconButton(onClick = { deletingReg = reg }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete Registration",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingReg?.let { reg ->
        AlertDialog(
            onDismissRequest = { inspectingReg = null },
            title = { Text("Application: ${reg.registrationToken}") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text("Student: ${reg.studentName}", fontWeight = FontWeight.Bold)
                    Text("Father's Name: ${reg.fatherName}")
                    Text("Class & Section: ${reg.className} - ${reg.section}")
                    Text("Roll No: ${reg.rollNumber}")
                    Text("Student No: ${reg.studentNumber}")
                    Text("Registration No: ${reg.registrationNumber}")
                    Text("Timing: ${reg.timingLabel}")
                    Text("Total Fee Covered: PKR ${reg.totalFeePkr}")
                    Text("Attendance: ${reg.attendanceStatus} • Result: ${reg.resultStatus}")
                    HorizontalDivider()
                    reg.subjectNames.zip(reg.paperCodes).forEach { (s, c) ->
                        Text("• $s -> $c", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        QrCodeImage(content = "AWA-VERIF:${reg.verificationId}", sizeDp = 120.dp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingReg = null }) { Text("Close") }
            }
        )
    }

    editingReg?.let { reg ->
        EditRegistrationDialog(
            registration = reg,
            onDismiss = { editingReg = null },
            onSave = { name, father, cls, sec, roll, stuNo, regNo ->
                onUpdateRegistration(reg.id, name, father, cls, sec, roll, stuNo, regNo)
                editingReg = null
            }
        )
    }

    deletingReg?.let { reg ->
        AlertDialog(
            onDismissRequest = { deletingReg = null },
            title = { Text("Delete Registration?") },
            text = {
                Text("Permanently remove ${reg.studentName} (${reg.registrationToken}) and release its paper codes?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteRegistration(reg)
                        deletingReg = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingReg = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.width(150.dp)
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            singleLine = true,
            modifier = Modifier.menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(opt) },
                    onClick = {
                        onSelect(opt)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun EditRegistrationDialog(
    registration: RegistrationRecord,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String, String) -> Unit
) {
    var studentName by remember { mutableStateOf(registration.studentName) }
    var fatherName by remember { mutableStateOf(registration.fatherName) }
    var className by remember { mutableStateOf(registration.className) }
    var section by remember { mutableStateOf(registration.section) }
    var rollNumber by remember { mutableStateOf(registration.rollNumber) }
    var studentNumber by remember { mutableStateOf(registration.studentNumber) }
    var regNumber by remember { mutableStateOf(registration.registrationNumber) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${registration.registrationToken}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(studentName, { studentName = it }, label = { Text("Student Name") }, singleLine = true)
                OutlinedTextField(fatherName, { fatherName = it }, label = { Text("Father's Name") }, singleLine = true)
                OutlinedTextField(className, { className = it }, label = { Text("Class") }, singleLine = true)
                OutlinedTextField(section, { section = it }, label = { Text("Section") }, singleLine = true)
                OutlinedTextField(rollNumber, { rollNumber = it }, label = { Text("Roll Number") }, singleLine = true)
                OutlinedTextField(studentNumber, { studentNumber = it }, label = { Text("Student Number") }, singleLine = true)
                OutlinedTextField(regNumber, { regNumber = it }, label = { Text("Registration Number") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(studentName, fatherName, className, section, rollNumber, studentNumber, regNumber)
                },
                enabled = studentName.trim().length >= 2 && fatherName.trim().length >= 2
            ) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
