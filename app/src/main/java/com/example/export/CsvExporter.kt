package com.example.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.model.AttendanceRecord
import com.example.model.RegistrationRecord
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CsvExporter {

    private fun escapeCsv(value: String): String {
        val needsQuotes = value.contains(',') || value.contains('"') || value.contains('\n')
        val escaped = value.replace("\"", "\"\"")
        return if (needsQuotes) "\"$escaped\"" else escaped
    }

    fun exportRegistrationsCsv(context: Context, registrations: List<RegistrationRecord>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "AwanAcademy_Registrations_${System.currentTimeMillis()}.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine(
            "Registration Token,Student Name,Father Name,Class,Section,Roll Number,Student Number,Registration Number,Subjects,Paper Codes,Exam Timing,Total Fee (PKR),Attendance,Result Status,Registration Date"
        )
        registrations.forEach { reg ->
            val dateStr = dateFormat.format(reg.createdAt?.toDate() ?: Date())
            val row = listOf(
                reg.registrationToken,
                reg.studentName,
                reg.fatherName,
                reg.className,
                reg.section,
                reg.rollNumber,
                reg.studentNumber,
                reg.registrationNumber,
                reg.subjectNames.joinToString(" | "),
                reg.paperCodes.joinToString(" | "),
                reg.timingLabel,
                reg.totalFeePkr.toString(),
                reg.attendanceStatus,
                reg.resultStatus,
                dateStr
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(row)
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun exportAttendanceCsv(context: Context, records: List<AttendanceRecord>): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "AwanAcademy_Attendance_${System.currentTimeMillis()}.csv")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)

        val sb = StringBuilder()
        sb.appendLine("Registration Token,Student Name,Class,Section,Examination Timing,Status,Marked At")
        records.forEach { rec ->
            val dateStr = dateFormat.format(rec.updatedAt?.toDate() ?: Date())
            val row = listOf(
                rec.registrationToken,
                rec.studentName,
                rec.className,
                rec.section,
                rec.timingLabel,
                rec.status,
                dateStr
            ).joinToString(",") { escapeCsv(it) }
            sb.appendLine(row)
        }
        file.writeText(sb.toString(), Charsets.UTF_8)
        return file
    }

    fun shareCsvFile(context: Context, csvFile: File, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", csvFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }
}
