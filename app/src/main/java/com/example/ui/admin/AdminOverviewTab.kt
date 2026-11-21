package com.example.ui.admin

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CobaltAccent
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.NavyPrimary
import com.example.ui.theme.TealTertiary
import com.example.viewmodel.AdminDashboardStats

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminOverviewTab(stats: AdminDashboardStats) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Examination Overview",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            maxItemsInEachRow = 3
        ) {
            KpiStatCard(
                title = "Total Registrations",
                value = "${stats.totalRegistrations}",
                icon = Icons.Filled.Assignment,
                accent = NavyPrimary,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Today's Registrations",
                value = "${stats.todayRegistrations}",
                icon = Icons.Filled.Today,
                accent = CobaltAccent,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Registered Students",
                value = "${stats.totalUniqueStudents}",
                icon = Icons.Filled.Groups,
                accent = TealTertiary,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Selected Examinations",
                value = "${stats.totalSelectedExaminations}",
                icon = Icons.Filled.MenuBook,
                accent = CobaltAccent,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Fee Covered by Academy",
                value = "PKR ${stats.totalFeeCoveredPkr}",
                icon = Icons.Filled.AttachMoney,
                accent = EmeraldSuccess,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Present Students",
                value = "${stats.presentCount}",
                icon = Icons.Filled.CheckCircle,
                accent = EmeraldSuccess,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
            KpiStatCard(
                title = "Absent Students",
                value = "${stats.absentCount}",
                icon = Icons.Filled.Cancel,
                accent = CrimsonError,
                modifier = Modifier.weight(1f, fill = false).width(165.dp)
            )
        }

        BarBreakdownCard(
            title = "Class-wise Registration Statistics",
            data = stats.classWiseCounts,
            barColor = CobaltAccent
        )

        BarBreakdownCard(
            title = "Subject-wise Registration Statistics",
            data = stats.subjectWiseCounts,
            barColor = TealTertiary
        )
    }
}

@Composable
private fun KpiStatCard(
    title: String,
    value: String,
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                color = accent.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accent,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(20.dp)
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BarBreakdownCard(
    title: String,
    data: Map<String, Int>,
    barColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            if (data.isEmpty()) {
                Text(
                    text = "No registrations recorded yet. Statistics will appear here automatically as students register.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val maxVal = (data.values.maxOrNull() ?: 1).coerceAtLeast(1)
                val trackColor = MaterialTheme.colorScheme.surfaceVariant
                data.entries.forEach { (label, count) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                            Text("$count", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                        ) {
                            drawRoundRect(
                                color = trackColor,
                                cornerRadius = CornerRadius(8f, 8f),
                                size = size
                            )
                            val fraction = count.toFloat() / maxVal.toFloat()
                            drawRoundRect(
                                color = barColor,
                                cornerRadius = CornerRadius(8f, 8f),
                                topLeft = Offset.Zero,
                                size = Size(width = size.width * fraction, height = size.height)
                            )
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }
        }
    }
}
