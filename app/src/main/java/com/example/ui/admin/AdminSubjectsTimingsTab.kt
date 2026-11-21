package com.example.ui.admin

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.AcademySettings
import com.example.model.DefaultCatalog
import com.example.model.ExaminationTiming
import com.example.model.SubjectItem

@Composable
fun AdminSubjectsTab(
    subjects: List<SubjectItem>,
    onSaveSubject: (SubjectItem, Boolean) -> Unit,
    onToggleActive: (SubjectItem) -> Unit,
    onDeleteSubject: (String) -> Unit
) {
    var editingSubject by remember { mutableStateOf<SubjectItem?>(null) }
    var isCreatingNew by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    text = "Subject Management (${subjects.size})",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Configure active subjects, class mappings, and paper code formats",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = {
                    isCreatingNew = true
                    editingSubject = SubjectItem(
                        name = "",
                        codePrefix = "",
                        assignedClasses = DefaultCatalog.AVAILABLE_CLASSES,
                        active = true,
                        sortOrder = subjects.size + 1
                    )
                },
                modifier = Modifier.testTag("add_subject_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add Subject")
            }
        }

        subjects.forEach { subject ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = subject.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Code Format: AWA-${subject.codePrefix}-26-XXXX",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "Classes: ${if (subject.assignedClasses.isEmpty()) "All" else subject.assignedClasses.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = subject.active,
                            onCheckedChange = { onToggleActive(subject) }
                        )
                        IconButton(onClick = {
                            isCreatingNew = false
                            editingSubject = subject
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit ${subject.name}")
                        }
                        IconButton(onClick = { onDeleteSubject(subject.id) }) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete ${subject.name}",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    editingSubject?.let { subject ->
        SubjectEditorDialog(
            initial = subject,
            isNew = isCreatingNew,
            onDismiss = { editingSubject = null },
            onSave = { updated ->
                onSaveSubject(updated, isCreatingNew)
                editingSubject = null
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectEditorDialog(
    initial: SubjectItem,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (SubjectItem) -> Unit
) {
    var name by remember { mutableStateOf(initial.name) }
    var prefix by remember { mutableStateOf(initial.codePrefix) }
    var assignedClasses by remember { mutableStateOf(initial.assignedClasses.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "Add Subject" else "Edit ${initial.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { prefix = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                    label = { Text("Paper Code Prefix (2-8 chars, e.g., MTH)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Assign to Classes", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultCatalog.AVAILABLE_CLASSES.forEach { cls ->
                        val selected = cls in assignedClasses
                        FilterChip(
                            selected = selected,
                            onClick = {
                                assignedClasses = if (selected) assignedClasses - cls else assignedClasses + cls
                            },
                            label = { Text(cls) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        initial.copy(
                            name = name.trim(),
                            codePrefix = prefix.trim(),
                            assignedClasses = assignedClasses.toList()
                        )
                    )
                },
                enabled = name.trim().isNotEmpty() && prefix.trim().length >= 2
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun AdminTimingsTab(
    timings: List<ExaminationTiming>,
    settings: AcademySettings,
    onSaveTiming: (ExaminationTiming) -> Unit,
    onSaveInstructions: (AcademySettings) -> Unit
) {
    val slot1 = timings.firstOrNull { it.slotNumber == 1 } ?: DefaultCatalog.DEFAULT_TIMINGS[0]
    val slot2 = timings.firstOrNull { it.slotNumber == 2 } ?: DefaultCatalog.DEFAULT_TIMINGS[1]

    var instructions by remember(settings.instructions) { mutableStateOf(settings.instructions) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Examination Timing Settings",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        TimingSlotCard(slot = slot1, onSave = onSaveTiming)
        TimingSlotCard(slot = slot2, onSave = onSaveTiming)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Global Examination Instructions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Instructions printed on Hall Tickets") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = { onSaveInstructions(settings.copy(instructions = instructions)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Save Examination Instructions")
                }
            }
        }
    }
}

@Composable
private fun TimingSlotCard(
    slot: ExaminationTiming,
    onSave: (ExaminationTiming) -> Unit
) {
    var label by remember(slot) { mutableStateOf(slot.label) }
    var startTime by remember(slot) { mutableStateOf(slot.startTime) }
    var endTime by remember(slot) { mutableStateOf(slot.endTime) }
    var duration by remember(slot) { mutableStateOf(slot.durationMinutes.toString()) }
    var active by remember(slot) { mutableStateOf(slot.active) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Slot ${slot.slotNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(6.dp))
                    Switch(checked = active, onCheckedChange = { active = it })
                }
            }
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Slot Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = startTime,
                    onValueChange = { startTime = it },
                    label = { Text("Start Time") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = endTime,
                    onValueChange = { endTime = it },
                    label = { Text("End Time") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = duration,
                    onValueChange = { duration = it.filter { c -> c.isDigit() } },
                    label = { Text("Mins") },
                    singleLine = true,
                    modifier = Modifier.width(84.dp)
                )
            }
            HorizontalDivider()
            Button(
                onClick = {
                    onSave(
                        slot.copy(
                            label = label,
                            startTime = startTime,
                            endTime = endTime,
                            durationMinutes = duration.toIntOrNull()?.coerceIn(15, 240) ?: 60,
                            active = active
                        )
                    )
                },
                enabled = label.isNotBlank() && startTime.isNotBlank() && endTime.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Save, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Update Slot ${slot.slotNumber}")
            }
        }
    }
}
