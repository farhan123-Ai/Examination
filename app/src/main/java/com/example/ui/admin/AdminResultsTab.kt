package com.example.ui.admin

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Grade
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.ExamResultRecord
import com.example.model.RegistrationRecord
import com.example.model.SubjectResultEntry
import com.example.ui.common.StatusPill
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminResultsTab(
    registrations: List<RegistrationRecord>,
    results: List<ExamResultRecord>,
    onSaveResult: (RegistrationRecord, List<SubjectResultEntry>, String, String) -> Unit,
    onDownloadResultPdf: (RegistrationRecord, ExamResultRecord) -> Unit,
    onPrintResultPdf: (RegistrationRecord, ExamResultRecord) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedRegId by remember { mutableStateOf<String?>(null) }

    val filteredRegs = remember(registrations, search) {
        registrations.filter {
            search.isBlank() ||
                it.studentName.contains(search, true) ||
                it.registrationToken.contains(search, true) ||
                it.rollNumber.contains(search, true)
        }
    }

    val selectedReg = remember(registrations, selectedRegId) {
        registrations.firstOrNull { it.id == selectedRegId }
    }
    val existingResult = remember(results, selectedReg) {
        selectedReg?.let { reg -> results.firstOrNull { it.registrationId == reg.id } }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = "Result Management (Manual Grading)",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Select a registered student to manually record subject-wise marks. Percentages calculate automatically from entered marks.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            label = { Text("Search student by name, token, or roll no") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("results_student_search_input")
        )

        if (selectedReg == null) {
            filteredRegs.forEach { reg ->
                val res = results.firstOrNull { it.registrationId == reg.id }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedRegId = reg.id },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reg.registrationToken,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${reg.studentName} • ${reg.className}-${reg.section}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "${reg.subjectCount} subject(s) • Roll: ${reg.rollNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StatusPill(res?.resultStatus ?: reg.resultStatus)
                    }
                }
            }
        } else {
            val initialEntries = remember(selectedReg, existingResult) {
                selectedReg.subjectIds.mapIndexed { idx, subId ->
                    val name = selectedReg.subjectNames.getOrElse(idx) { "Subject" }
                    val code = selectedReg.paperCodes.getOrElse(idx) { "" }
                    val found = existingResult?.subjectResults?.firstOrNull {
                        it.subjectId == subId || it.paperCode == code
                    }
                    SubjectResultEntry(
                        subjectId = subId,
                        subjectName = name,
                        paperCode = code,
                        maxMarks = found?.maxMarks ?: 50.0,
                        obtainedMarks = found?.obtainedMarks ?: 0.0
                    )
                }
            }
            var entries by remember(selectedReg.id, existingResult) { mutableStateOf(initialEntries) }
            var remarks by remember(selectedReg.id, existingResult) { mutableStateOf(existingResult?.remarks ?: "") }
            var status by remember(selectedReg.id, existingResult) {
                mutableStateOf(existingResult?.resultStatus ?: "PASS")
            }

            val totalMax = entries.sumOf { it.maxMarks.coerceAtLeast(0.0) }
            val totalObtained = entries.sumOf { it.obtainedMarks.coerceAtLeast(0.0) }
            val pct = if (totalMax > 0.0) (totalObtained / totalMax) * 100.0 else 0.0
            val marksValid = entries.all { it.maxMarks >= 0.0 && it.obtainedMarks >= 0.0 && it.obtainedMarks <= it.maxMarks }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = selectedReg.studentName,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${selectedReg.registrationToken} • ${selectedReg.className}-${selectedReg.section} • Roll ${selectedReg.rollNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(onClick = { selectedRegId = null }) {
                            Text("Change Student")
                        }
                    }

                    HorizontalDivider()

                    entries.forEachIndexed { idx, entry ->
                        var maxText by remember(selectedReg.id, idx) {
                            mutableStateOf(if (entry.maxMarks > 0) entry.maxMarks.toInt().toString() else "")
                        }
                        var obtText by remember(selectedReg.id, idx) {
                            mutableStateOf(if (entry.obtainedMarks > 0) entry.obtainedMarks.toString().removeSuffix(".0") else "")
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(entry.subjectName, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        entry.paperCode,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedTextField(
                                        value = maxText,
                                        onValueChange = { txt ->
                                            maxText = txt
                                            val v = txt.toDoubleOrNull() ?: 0.0
                                            entries = entries.toMutableList().also {
                                                it[idx] = it[idx].copy(maxMarks = v)
                                            }
                                        },
                                        label = { Text("Max Marks") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = obtText,
                                        onValueChange = { txt ->
                                            obtText = txt
                                            val v = txt.toDoubleOrNull() ?: 0.0
                                            entries = entries.toMutableList().also {
                                                it[idx] = it[idx].copy(obtainedMarks = v)
                                            }
                                        },
                                        label = { Text("Obtained Marks") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Calculated Summary", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    text = "Total: ${String.format(Locale.US, "%.1f", totalObtained)} / ${String.format(Locale.US, "%.1f", totalMax)}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Text(
                                text = String.format(Locale.US, "%.2f%%", pct),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text("Result Status", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("PASS", "FAIL", "SUPPLEMENTARY", "PENDING").forEach { st ->
                            FilterChip(
                                selected = status == st,
                                onClick = { status = st },
                                label = { Text(st) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = remarks,
                        onValueChange = { remarks = it },
                        label = { Text("Examiner Remarks") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (!marksValid) {
                        Text(
                            text = "Obtained marks cannot exceed maximum marks.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { onSaveResult(selectedReg, entries, remarks, status) },
                        enabled = marksValid && totalMax > 0.0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_exam_result_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Save Exam Result")
                    }

                    val printableResult = existingResult ?: ExamResultRecord(
                        registrationId = selectedReg.id,
                        registrationToken = selectedReg.registrationToken,
                        userId = selectedReg.userId,
                        studentName = selectedReg.studentName,
                        fatherName = selectedReg.fatherName,
                        className = selectedReg.className,
                        section = selectedReg.section,
                        rollNumber = selectedReg.rollNumber,
                        studentNumber = selectedReg.studentNumber,
                        registrationNumber = selectedReg.registrationNumber,
                        subjectResults = entries,
                        totalMaxMarks = totalMax,
                        totalObtainedMarks = totalObtained,
                        percentage = pct,
                        remarks = remarks,
                        resultStatus = status
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onDownloadResultPdf(selectedReg, printableResult) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Download PDF")
                        }
                        OutlinedButton(
                            onClick = { onPrintResultPdf(selectedReg, printableResult) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Print Card")
                        }
                    }
                }
            }
        }
    }
}
