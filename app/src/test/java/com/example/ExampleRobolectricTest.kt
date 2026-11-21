package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.export.CsvExporter
import com.example.model.AttendanceRecord
import com.example.model.DefaultCatalog
import com.example.model.RegistrationRecord
import com.example.pdf.QrAndImageUtils
import com.example.security.AdminSecurity
import com.example.viewmodel.StudentFormState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun appNameAndDefaultCatalogMatchRequirements() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Awan Academy", context.getString(R.string.app_name))
        assertEquals(17, DefaultCatalog.DEFAULT_SUBJECTS.size)
        assertEquals(2, DefaultCatalog.DEFAULT_TIMINGS.size)
        assertEquals(7, DefaultCatalog.TERMS_AND_CONDITIONS.size)
    }

    @Test
    fun adminPbkdf2VerificationWorks() {
        assertTrue(
            AdminSecurity.verifyPassword(
                password = "academy1",
                saltHex = DefaultCatalog.BOOTSTRAP_ADMIN_SALT,
                expectedHashHex = DefaultCatalog.BOOTSTRAP_ADMIN_HASH,
                iterations = DefaultCatalog.BOOTSTRAP_ADMIN_ITERATIONS
            )
        )
        assertFalse(
            AdminSecurity.verifyPassword(
                password = "wrongpassword",
                saltHex = DefaultCatalog.BOOTSTRAP_ADMIN_SALT,
                expectedHashHex = DefaultCatalog.BOOTSTRAP_ADMIN_HASH,
                iterations = DefaultCatalog.BOOTSTRAP_ADMIN_ITERATIONS
            )
        )

        val salt = AdminSecurity.generateSaltHex()
        val hash = AdminSecurity.hashPasswordPbkdf2("NewStrongPass99!", salt)
        assertTrue(AdminSecurity.verifyPassword("NewStrongPass99!", salt, hash))
    }

    @Test
    fun feeStatementAndFormValidationWork() {
        val single = RegistrationRecord(subjectCount = 1, totalFeePkr = 200)
        assertEquals("Your fee of PKR 200 has been paid by academy.", single.feeStatement())

        val triple = RegistrationRecord(subjectCount = 3, totalFeePkr = 600)
        assertEquals(
            "Your improvement examination fee of PKR 600 has been paid by Awan Academy.",
            triple.feeStatement()
        )

        val incomplete = StudentFormState(
            studentName = "Zainab Ali",
            fatherName = "Muhammad Ali",
            className = "10th",
            section = "A",
            rollNumber = "1024",
            studentNumber = "STU-551",
            registrationNumber = "REG-2026-88",
            selectedSubjectIds = setOf("sub_mth"),
            termsAccepted = false
        )
        assertFalse(incomplete.isFormValid)

        val complete = incomplete.copy(termsAccepted = true)
        assertTrue(complete.isFormValid)
    }

    @Test
    fun qrCodeBitmapAndCsvExportsWork() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val qrBitmap = QrAndImageUtils.generateQrBitmap("AWA-VERIF:ver_test12345", 200)
        assertEquals(200, qrBitmap.width)
        assertEquals(200, qrBitmap.height)

        val reg = RegistrationRecord(
            id = "reg_1",
            registrationToken = "AWA-IMP-2026-00001",
            studentName = "Zainab Ali",
            fatherName = "Muhammad Ali",
            className = "10th",
            section = "A",
            rollNumber = "1024",
            studentNumber = "STU-551",
            registrationNumber = "REG-2026-88",
            timingLabel = "Morning Examination Session (09:00 AM - 10:00 AM)",
            subjectNames = listOf("Mathematics", "Physics"),
            paperCodes = listOf("AWA-MTH-26-0001", "AWA-PHY-26-0002"),
            subjectCount = 2,
            totalFeePkr = 400,
            attendanceStatus = "PRESENT",
            resultStatus = "PENDING"
        )
        val regCsv = CsvExporter.exportRegistrationsCsv(context, listOf(reg))
        assertTrue(regCsv.exists())
        val regText = regCsv.readText()
        assertTrue(regText.contains("AWA-IMP-2026-00001"))
        assertTrue(regText.contains("AWA-MTH-26-0001"))

        val attCsv = CsvExporter.exportAttendanceCsv(
            context,
            listOf(
                AttendanceRecord(
                    registrationToken = "AWA-IMP-2026-00001",
                    studentName = "Zainab Ali",
                    className = "10th",
                    section = "A",
                    timingLabel = "Morning",
                    status = "PRESENT"
                )
            )
        )
        assertTrue(attCsv.exists())
        assertTrue(attCsv.readText().contains("PRESENT"))
    }
}
