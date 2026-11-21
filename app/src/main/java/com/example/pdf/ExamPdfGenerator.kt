package com.example.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.model.AcademySettings
import com.example.model.DefaultCatalog
import com.example.model.ExamResultRecord
import com.example.model.RegistrationRecord
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExamPdfGenerator {
    private const val PAGE_WIDTH = 595 // A4 width in points (72 dpi)
    private const val PAGE_HEIGHT = 842 // A4 height in points (72 dpi)
    private const val MARGIN = 28f

    private val navyColor = Color.rgb(10, 37, 66)
    private val cobaltColor = Color.rgb(29, 78, 216)
    private val lightBg = Color.rgb(241, 245, 249)
    private val borderGray = Color.rgb(148, 163, 184)
    private val ruleGray = Color.rgb(180, 190, 204)
    private val darkText = Color.rgb(15, 23, 42)
    private val mutedText = Color.rgb(71, 85, 105)

    private fun formatDate(reg: RegistrationRecord): String {
        val date = reg.createdAt?.toDate() ?: Date()
        return SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US).format(date)
    }

    private fun preparePdfFile(context: Context, fileName: String): File {
        val dir = File(context.cacheDir, "pdfs")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, fileName)
    }

    fun generateReceiptPdf(
        context: Context,
        registration: RegistrationRecord,
        settings: AcademySettings
    ): File {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        val logoBitmap = QrAndImageUtils.decodeLogoBitmap(context, settings.logoBase64)
        val qrBitmap = QrAndImageUtils.generateQrBitmap("AWA-VERIF:${registration.verificationId}", 240)

        drawHeaderBanner(
            canvas = canvas,
            logo = logoBitmap,
            academyName = settings.academyName.ifBlank { "AWAN ACADEMY" },
            subtitle = "IMPROVEMENT CRASH HALF LENGTH — REGISTRATION & FEE RECEIPT",
            token = registration.registrationToken
        )

        var y = 114f
        y = drawSectionBar(canvas, y, "STUDENT & ACADEMY IDENTIFICATION")
        y = drawKeyValueGrid(
            canvas = canvas,
            startY = y + 6f,
            rows = listOf(
                "Student Name" to registration.studentName,
                "Father's Name" to registration.fatherName,
                "Class & Section" to "${registration.className} - ${registration.section}",
                "Academy Roll Number" to registration.rollNumber,
                "Academy Student Number" to registration.studentNumber,
                "Academy Registration No." to registration.registrationNumber,
                "Registration Token" to registration.registrationToken,
                "Registration Date" to formatDate(registration)
            )
        )

        y += 14f
        y = drawSectionBar(canvas, y, "REGISTERED SUBJECTS & PAPER CODES")
        y = drawSubjectsTable(canvas, y + 6f, registration.subjectNames, registration.paperCodes)

        y += 14f
        y = drawSectionBar(canvas, y, "EXAMINATION TIMING & FEE CONFIRMATION")
        y = drawKeyValueGrid(
            canvas = canvas,
            startY = y + 6f,
            rows = listOf(
                "Examination Timing" to registration.timingLabel,
                "Number of Examinations" to "${registration.subjectCount}",
                "Total Examination Fee" to "PKR ${registration.totalFeePkr} (PKR 200 × ${registration.subjectCount})",
                "Session Duration" to "ONE HOUR (1 Hour)"
            )
        )

        // Fee payment statement highlight box
        y += 12f
        val feeBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 48f)
        val boxPaint = Paint().apply {
            color = Color.rgb(209, 250, 229)
            style = Paint.Style.FILL
        }
        val boxBorder = Paint().apply {
            color = Color.rgb(5, 150, 105)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        canvas.drawRoundRect(feeBox, 6f, 6f, boxPaint)
        canvas.drawRoundRect(feeBox, 6f, 6f, boxBorder)
        val feeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(6, 78, 59)
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(registration.feeStatement(), MARGIN + 12f, y + 21f, feeTextPaint)
        val subFeePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(6, 95, 70)
            textSize = 9.5f
        }
        canvas.drawText(
            "No online payment is required. Please keep this receipt for verification.",
            MARGIN + 12f,
            y + 37f,
            subFeePaint
        )

        // Policy + QR Code at bottom
        y += 66f
        val qrSize = 96f
        canvas.drawBitmap(qrBitmap, null, RectF(PAGE_WIDTH - MARGIN - qrSize, y, PAGE_WIDTH - MARGIN, y + qrSize), null)
        val notePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 9.5f
        }
        drawMultilineText(
            canvas,
            "IMPORTANT POLICY NOTICE:\n${DefaultCatalog.EXAM_POLICY_NOTICE}\n\nVerification Ref: ${registration.verificationId}",
            MARGIN,
            y + 12f,
            PAGE_WIDTH - MARGIN * 2 - qrSize - 16f,
            13f,
            notePaint
        )

        drawFooter(canvas, "Official Computer-Generated Receipt • Awan Academy", 1, 1)
        doc.finishPage(page)

        val outFile = preparePdfFile(context, "Receipt_${registration.registrationToken}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateCompleteExamPackagePdf(
        context: Context,
        registration: RegistrationRecord,
        settings: AcademySettings
    ): File {
        val doc = PdfDocument()
        val logoBitmap = QrAndImageUtils.decodeLogoBitmap(context, settings.logoBase64)
        val stampBitmap = QrAndImageUtils.decodeBase64Bitmap(settings.stampBase64)
        val sigBitmap = QrAndImageUtils.decodeBase64Bitmap(settings.signatureBase64)
        val qrBitmap = QrAndImageUtils.generateQrBitmap("AWA-VERIF:${registration.verificationId}", 260)

        // PAGE 1: STUDENT HALL TICKET
        run {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
            drawHallTicketPage(page.canvas, registration, settings, logoBitmap, qrBitmap, stampBitmap, sigBitmap)
            doc.finishPage(page)
        }

        // PAGES 2-12: 11 ANSWER SHEETS
        for (pageNum in 2..12) {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNum).create())
            drawAnswerSheetPage(page.canvas, pageNum, registration, settings, logoBitmap)
            doc.finishPage(page)
        }

        // PAGE 13: MCQ OMR (30), 8 SHORT QUESTIONS (15 LINES EACH), 1 LONG QUESTION (46 LINES), MARKS SECTION
        run {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 13).create())
            drawOmrAndSubjectivePage(page.canvas, registration, settings, logoBitmap, qrBitmap)
            doc.finishPage(page)
        }

        // PAGE 14: RESULT CARD (BLANK INITIAL FIELDS)
        run {
            val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 14).create())
            drawResultCardPage(
                canvas = page.canvas,
                registration = registration,
                settings = settings,
                logo = logoBitmap,
                stamp = stampBitmap,
                signature = sigBitmap,
                result = null,
                pageNum = 14,
                totalPages = 14
            )
            doc.finishPage(page)
        }

        val outFile = preparePdfFile(context, "ExamPackage_${registration.registrationToken}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    fun generateFilledResultCardPdf(
        context: Context,
        registration: RegistrationRecord,
        result: ExamResultRecord,
        settings: AcademySettings
    ): File {
        val doc = PdfDocument()
        val logoBitmap = QrAndImageUtils.decodeLogoBitmap(context, settings.logoBase64)
        val stampBitmap = QrAndImageUtils.decodeBase64Bitmap(settings.stampBase64)
        val sigBitmap = QrAndImageUtils.decodeBase64Bitmap(settings.signatureBase64)

        val page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create())
        drawResultCardPage(
            canvas = page.canvas,
            registration = registration,
            settings = settings,
            logo = logoBitmap,
            stamp = stampBitmap,
            signature = sigBitmap,
            result = result,
            pageNum = 1,
            totalPages = 1
        )
        doc.finishPage(page)

        val outFile = preparePdfFile(context, "ResultCard_${registration.registrationToken}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }

    private fun drawHallTicketPage(
        canvas: Canvas,
        reg: RegistrationRecord,
        settings: AcademySettings,
        logo: Bitmap,
        qr: Bitmap,
        stamp: Bitmap?,
        sig: Bitmap?
    ) {
        drawHeaderBanner(
            canvas = canvas,
            logo = logo,
            academyName = settings.academyName.ifBlank { "AWAN ACADEMY" },
            subtitle = DefaultCatalog.EXAM_PACKAGE_HEADING,
            token = reg.registrationToken
        )

        // Prominent HALL TICKET ribbon
        var y = 104f
        val ribbonPaint = Paint().apply { color = cobaltColor }
        canvas.drawRoundRect(RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 26f), 4f, 4f, ribbonPaint)
        val ribbonText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("OFFICIAL STUDENT HALL TICKET", PAGE_WIDTH / 2f, y + 17f, ribbonText)

        y += 38f
        // QR Code on right, Student info on left
        val qrSize = 102f
        val qrLeft = PAGE_WIDTH - MARGIN - qrSize
        val qrBorder = Paint().apply {
            color = navyColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRect(qrLeft - 4f, y, PAGE_WIDTH - MARGIN, y + qrSize + 18f, qrBorder)
        canvas.drawBitmap(qr, null, RectF(qrLeft, y + 3f, PAGE_WIDTH - MARGIN - 4f, y + qrSize - 1f), null)
        val qrCaption = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("SCAN FOR ATTENDANCE", qrLeft + qrSize / 2f - 2f, y + qrSize + 12f, qrCaption)

        val leftWidth = qrLeft - MARGIN - 14f
        y = drawKeyValueColumn(
            canvas = canvas,
            x = MARGIN,
            startY = y,
            width = leftWidth,
            rows = listOf(
                "Registration Token" to reg.registrationToken,
                "Student Name" to reg.studentName,
                "Father's Name" to reg.fatherName,
                "Class & Section" to "${reg.className} — Section ${reg.section}",
                "Academy Roll No." to reg.rollNumber,
                "Academy Student No." to reg.studentNumber,
                "Academy Reg. No." to reg.registrationNumber,
                "Examination Timing" to reg.timingLabel
            )
        )

        y = maxOf(y, 274f) + 14f
        y = drawSectionBar(canvas, y, "REGISTERED SUBJECTS & INDIVIDUAL PAPER CODES")
        y = drawSubjectsTable(canvas, y + 6f, reg.subjectNames, reg.paperCodes)

        y += 14f
        y = drawSectionBar(canvas, y, "EXAMINATION INSTRUCTIONS & POLICY")
        y += 10f
        val instrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 9.5f
        }
        val fullInstructions = "${settings.instructions.ifBlank { DefaultCatalog.DEFAULT_EXAM_INSTRUCTIONS }}\n\nPolicy: ${DefaultCatalog.EXAM_POLICY_NOTICE}"
        y = drawMultilineText(canvas, fullInstructions, MARGIN + 4f, y + 6f, PAGE_WIDTH - MARGIN * 2 - 8f, 13.5f, instrPaint)

        // Stamp & Authorized Signature area
        val sigY = PAGE_HEIGHT - 96f
        drawSignatureAndStampRow(canvas, sigY, stamp, sig, includeStudentAndParent = false)
        drawFooter(canvas, "Hall Ticket • ${reg.registrationToken}", 1, 14)
    }

    private fun drawAnswerSheetPage(
        canvas: Canvas,
        pageNum: Int,
        reg: RegistrationRecord,
        settings: AcademySettings,
        logo: Bitmap
    ) {
        // Compact consistent top header
        val headerBorder = Paint().apply {
            color = navyColor
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRect(MARGIN, 20f, PAGE_WIDTH - MARGIN, 94f, headerBorder)
        canvas.drawBitmap(logo, null, RectF(MARGIN + 6f, 24f, MARGIN + 42f, 60f), null)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 11.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "${settings.academyName.ifBlank { "AWAN ACADEMY" }} — EXAMINATION ANSWER SHEET",
            MARGIN + 50f,
            37f,
            titlePaint
        )
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cobaltColor
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "Token: ${reg.registrationToken}   |   Student: ${reg.studentName}   |   Class: ${reg.className}-${reg.section}",
            MARGIN + 50f,
            51f,
            subPaint
        )

        val idPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.2f
        }
        canvas.drawText(
            "Roll No: ${reg.rollNumber}   •   Student No: ${reg.studentNumber}   •   Reg No: ${reg.registrationNumber}   •   Timing: ${reg.timingLabel}",
            MARGIN + 8f,
            72f,
            idPaint
        )
        val subjSummary = reg.subjectNames.zip(reg.paperCodes)
            .joinToString(" | ") { (s, c) -> "$s [$c]" }
        canvas.drawText(
            "Subject / Paper Code(s): $subjSummary",
            MARGIN + 8f,
            85f,
            idPaint
        )

        // Right page badge
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Sheet ${pageNum - 1}/11 (Page $pageNum)", PAGE_WIDTH - MARGIN - 8f, 37f, badgePaint)

        // Concise instructions strip
        val instrBg = Paint().apply { color = lightBg }
        canvas.drawRect(MARGIN, 98f, PAGE_WIDTH - MARGIN, 114f, instrBg)
        val instrPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.6f
        }
        canvas.drawText(
            "Instructions: Write question numbers in the left margin. Write neatly inside lines. Do not tear any page from the booklet.",
            MARGIN + 6f,
            109f,
            instrPaint
        )

        // Left margin vertical line + horizontal writing lines
        val marginLineX = MARGIN + 36f
        val vLinePaint = Paint().apply {
            color = Color.rgb(203, 120, 120)
            strokeWidth = 0.8f
        }
        canvas.drawLine(marginLineX, 122f, marginLineX, PAGE_HEIGHT - 38f, vLinePaint)

        val hLinePaint = Paint().apply {
            color = ruleGray
            strokeWidth = 0.6f
        }
        val qPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.5f
        }
        canvas.drawText("Q.No", MARGIN + 8f, 134f, qPaint)

        var lineY = 140f
        val step = 20.5f
        while (lineY <= PAGE_HEIGHT - 44f) {
            canvas.drawLine(MARGIN, lineY, PAGE_WIDTH - MARGIN, lineY, hLinePaint)
            lineY += step
        }

        drawFooter(canvas, "${reg.registrationToken} • Answer Sheet", pageNum, 14)
    }

    private fun drawOmrAndSubjectivePage(
        canvas: Canvas,
        reg: RegistrationRecord,
        settings: AcademySettings,
        logo: Bitmap,
        qr: Bitmap
    ) {
        // Scanner alignment corner fiducial squares (high-contrast black)
        val fidPaint = Paint().apply { color = Color.BLACK; style = Paint.Style.FILL }
        canvas.drawRect(14f, 14f, 22f, 22f, fidPaint)
        canvas.drawRect(PAGE_WIDTH - 22f, 14f, PAGE_WIDTH - 14f, 22f, fidPaint)
        canvas.drawRect(14f, PAGE_HEIGHT - 22f, 22f, PAGE_HEIGHT - 14f, fidPaint)
        canvas.drawRect(PAGE_WIDTH - 22f, PAGE_HEIGHT - 22f, PAGE_WIDTH - 14f, PAGE_HEIGHT - 14f, fidPaint)

        // Top compact OMR header (y = 18..68)
        val borderBlack = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }
        canvas.drawRect(MARGIN, 18f, PAGE_WIDTH - MARGIN, 68f, borderBlack)
        canvas.drawBitmap(logo, null, RectF(MARGIN + 5f, 23f, MARGIN + 41f, 59f), null)
        canvas.drawBitmap(qr, null, RectF(PAGE_WIDTH - MARGIN - 45f, 21f, PAGE_WIDTH - MARGIN - 3f, 63f), null)

        val hTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 10.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(
            "${settings.academyName.ifBlank { "AWAN ACADEMY" }} — MCQ OMR & SUBJECTIVE SHEET",
            MARGIN + 46f,
            33f,
            hTitle
        )
        val hMeta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.2f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("TOKEN: ${reg.registrationToken}  |  ROLL: ${reg.rollNumber}", MARGIN + 46f, 47f, hMeta)
        val hSub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.8f
        }
        val paperList = reg.paperCodes.joinToString(", ")
        canvas.drawText("Student: ${reg.studentName} (${reg.className}-${reg.section}) | Codes: $paperList", MARGIN + 46f, 60f, hSub)

        // SECTION A: OMR SHEET - 30 MCQs (y = 74..220)
        drawOmrSectionHeader(canvas, 74f, "A. OMR SHEET — 30 MCQs (High-Contrast Scanner Layout • Shade One Bubble A / B / C / D)")
        val omrBoxTop = 90f
        val omrBoxBottom = 222f
        canvas.drawRect(MARGIN, omrBoxTop, PAGE_WIDTH - MARGIN, omrBoxBottom, borderBlack)

        val colWidth = (PAGE_WIDTH - MARGIN * 2) / 3f
        val qNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 8.2f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val bubbleStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val bubbleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 6.8f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }

        val options = listOf("A", "B", "C", "D")
        for (col in 0 until 3) {
            val colX = MARGIN + col * colWidth
            if (col > 0) {
                canvas.drawLine(colX, omrBoxTop, colX, omrBoxBottom, borderBlack)
            }
            for (row in 0 until 10) {
                val qNumber = col * 10 + row + 1
                val cy = omrBoxTop + 9f + row * 12.6f
                canvas.drawText(qNumber.toString().padStart(2, '0'), colX + 10f, cy + 3f, qNumPaint)
                options.forEachIndexed { optIdx, opt ->
                    val cx = colX + 44f + optIdx * 32f
                    canvas.drawCircle(cx, cy, 4.7f, bubbleStroke)
                    canvas.drawText(opt, cx, cy + 2.4f, bubbleText)
                }
            }
        }

        // SECTION B: 8 SHORT QUESTIONS, EACH WITH 15 WRITING LINES (y = 228..500)
        drawOmrSectionHeader(canvas, 228f, "B. SHORT QUESTIONS (Q1 – Q8 | 15 Writing Lines Per Question)")
        val shortTop = 244f
        val shortCols = 2
        val shortGapX = 10f
        val shortColW = (PAGE_WIDTH - MARGIN * 2 - shortGapX) / shortCols
        val shortBoxH = 61f
        val shortGapY = 4f
        val shortLinePaint = Paint().apply { color = Color.rgb(110, 110, 110); strokeWidth = 0.5f }
        val qLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.5f
            typeface = Typeface.DEFAULT_BOLD
        }

        for (i in 0 until 8) {
            val col = i / 4
            val row = i % 4
            val left = MARGIN + col * (shortColW + shortGapX)
            val top = shortTop + row * (shortBoxH + shortGapY)
            canvas.drawRect(left, top, left + shortColW, top + shortBoxH, borderBlack)
            canvas.drawText("Q${i + 1}.", left + 4f, top + 8.5f, qLabelPaint)
            // Exactly 15 horizontal writing lines inside each short-question box
            val firstLineY = top + 11.5f
            val lineStep = 3.4f
            for (lineIdx in 0 until 15) {
                val ly = firstLineY + lineIdx * lineStep
                canvas.drawLine(left + 4f, ly, left + shortColW - 4f, ly, shortLinePaint)
            }
        }

        // SECTION C: LONG QUESTION WITH EXACTLY 46 WRITING LINES (y = 506..748)
        drawOmrSectionHeader(canvas, 506f, "C. LONG QUESTION (46 Writing Lines)")
        val longTop = 522f
        val longBottom = 748f
        canvas.drawRect(MARGIN, longTop, PAGE_WIDTH - MARGIN, longBottom, borderBlack)
        val longMidX = PAGE_WIDTH / 2f
        canvas.drawLine(longMidX, longTop, longMidX, longBottom, borderBlack)

        val lineNumPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 90, 90)
            textSize = 6.2f
        }
        val longLineStep = 9.3f
        val longStartY = longTop + 13f
        // 23 lines in left column (1..23) + 23 lines in right column (24..46) = 46 lines exact
        for (idx in 0 until 23) {
            val ly = longStartY + idx * longLineStep
            canvas.drawText("${idx + 1}", MARGIN + 4f, ly - 1f, lineNumPaint)
            canvas.drawLine(MARGIN + 18f, ly, longMidX - 6f, ly, shortLinePaint)

            canvas.drawText("${idx + 24}", longMidX + 4f, ly - 1f, lineNumPaint)
            canvas.drawLine(longMidX + 18f, ly, PAGE_WIDTH - MARGIN - 6f, ly, shortLinePaint)
        }

        // SECTION D: MARKS SECTION (y = 754..816)
        drawOmrSectionHeader(canvas, 754f, "D. MARKS SECTION (For Official Examiner Use Only — Do Not Write Here)")
        val marksTop = 770f
        val marksBottom = 816f
        canvas.drawRect(MARGIN, marksTop, PAGE_WIDTH - MARGIN, marksBottom, borderBlack)
        val headers = listOf(
            "MCQs Marks",
            "Short Questions Marks",
            "Long Question Marks",
            "Total Marks",
            "Examiner Signature"
        )
        val mColW = (PAGE_WIDTH - MARGIN * 2) / headers.size
        val mHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 7.4f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        canvas.drawLine(MARGIN, marksTop + 16f, PAGE_WIDTH - MARGIN, marksTop + 16f, borderBlack)
        headers.forEachIndexed { idx, label ->
            val x0 = MARGIN + idx * mColW
            if (idx > 0) canvas.drawLine(x0, marksTop, x0, marksBottom, borderBlack)
            canvas.drawText(label, x0 + mColW / 2f, marksTop + 11f, mHeaderPaint)
        }

        drawFooter(canvas, "${reg.registrationToken} • Page 13 OMR & Subjective", 13, 14)
    }

    private fun drawResultCardPage(
        canvas: Canvas,
        registration: RegistrationRecord,
        settings: AcademySettings,
        logo: Bitmap,
        stamp: Bitmap?,
        signature: Bitmap?,
        result: ExamResultRecord?,
        pageNum: Int,
        totalPages: Int
    ) {
        drawHeaderBanner(
            canvas = canvas,
            logo = logo,
            academyName = settings.academyName.ifBlank { "AWAN ACADEMY" },
            subtitle = "OFFICIAL EXAMINATION RESULT CARD — IMPROVEMENT CRASH HALF LENGTH",
            token = registration.registrationToken
        )

        var y = 110f
        y = drawSectionBar(canvas, y, "CANDIDATE PARTICULARS")
        y = drawKeyValueGrid(
            canvas = canvas,
            startY = y + 6f,
            rows = listOf(
                "Student Name" to registration.studentName,
                "Father's Name" to registration.fatherName,
                "Class & Section" to "${registration.className} — Section ${registration.section}",
                "Academy Roll Number" to registration.rollNumber,
                "Academy Student Number" to registration.studentNumber,
                "Academy Registration No." to registration.registrationNumber,
                "Registration Token" to registration.registrationToken,
                "Examination Timing" to registration.timingLabel
            )
        )

        y += 16f
        y = drawSectionBar(canvas, y, "SUBJECT-WISE EXAMINATION PERFORMANCE")
        y += 6f

        // Table Header
        val tableBorder = Paint().apply {
            color = navyColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        val headerBg = Paint().apply { color = navyColor }
        val cols = listOf(
            "Subject" to 155f,
            "Paper Code" to 115f,
            "Total Marks" to 70f,
            "Obtained" to 70f,
            "Percentage" to 65f,
            "Status" to 64f
        )
        val rowHeight = 24f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, headerBg)
        val hText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        var cx = MARGIN
        cols.forEach { (title, width) ->
            canvas.drawText(title, cx + 6f, y + 15f, hText)
            cx += width
        }
        y += rowHeight

        val rowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.8f
        }
        val subjectEntries = registration.subjectNames.zip(registration.paperCodes)
        subjectEntries.forEachIndexed { idx, (subjectName, paperCode) ->
            val matched = result?.subjectResults?.firstOrNull {
                it.paperCode == paperCode || it.subjectName == subjectName
            }
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, Paint().apply { color = lightBg })
            }
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowHeight, tableBorder)
            var rx = MARGIN
            val pctText = if (matched != null && matched.maxMarks > 0.0) {
                String.format(Locale.US, "%.1f%%", (matched.obtainedMarks / matched.maxMarks) * 100.0)
            } else ""
            val statusText = if (matched != null && matched.maxMarks > 0.0) {
                if ((matched.obtainedMarks / matched.maxMarks) * 100.0 >= 40.0) "PASS" else "FAIL"
            } else ""
            val cells = listOf(
                subjectName,
                paperCode,
                matched?.let { if (it.maxMarks > 0) formatNum(it.maxMarks) else "" } ?: "",
                matched?.let { if (it.maxMarks > 0) formatNum(it.obtainedMarks) else "" } ?: "",
                pctText,
                statusText
            )
            cols.forEachIndexed { colIdx, (_, width) ->
                if (colIdx > 0) canvas.drawLine(rx, y, rx, y + rowHeight, tableBorder)
                canvas.drawText(cells[colIdx], rx + 6f, y + 15f, rowPaint)
                rx += width
            }
            y += rowHeight
        }

        // Aggregate Summary Box (blank if result == null)
        y += 14f
        y = drawSectionBar(canvas, y, "OVERALL RESULT SUMMARY")
        y = drawKeyValueGrid(
            canvas = canvas,
            startY = y + 6f,
            rows = listOf(
                "Total Marks" to (result?.let { if (it.totalMaxMarks > 0) formatNum(it.totalMaxMarks) else "" } ?: ""),
                "Obtained Marks" to (result?.let { if (it.totalMaxMarks > 0) formatNum(it.totalObtainedMarks) else "" } ?: ""),
                "Percentage" to (result?.let { if (it.totalMaxMarks > 0) String.format(Locale.US, "%.2f%%", it.percentage) else "" } ?: ""),
                "Result Status" to (result?.takeIf { it.resultStatus != "PENDING" }?.resultStatus ?: ""),
                "Examiner Remarks" to (result?.remarks ?: "")
            )
        )

        // Parent Consent Statement
        y += 16f
        val consentBox = RectF(MARGIN, y, PAGE_WIDTH - MARGIN, y + 54f)
        canvas.drawRoundRect(consentBox, 6f, 6f, Paint().apply { color = lightBg })
        canvas.drawRoundRect(consentBox, 6f, 6f, tableBorder)
        val consentTitle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 9f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("PARENT / GUARDIAN ACKNOWLEDGEMENT & CONSENT", MARGIN + 10f, y + 15f, consentTitle)
        val consentBody = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8.8f
        }
        drawMultilineText(
            canvas,
            DefaultCatalog.PARENT_CONSENT_STATEMENT,
            MARGIN + 10f,
            y + 29f,
            PAGE_WIDTH - MARGIN * 2 - 20f,
            12f,
            consentBody
        )

        // Official Stamp, Academy Signature, Parent Signature, Student Signature
        val sigY = PAGE_HEIGHT - 118f
        drawSignatureAndStampRow(canvas, sigY, stamp, signature, includeStudentAndParent = true)

        drawFooter(canvas, "Official Result Card • ${registration.registrationToken}", pageNum, totalPages)
    }

    private fun drawSignatureAndStampRow(
        canvas: Canvas,
        y: Float,
        stamp: Bitmap?,
        signature: Bitmap?,
        includeStudentAndParent: Boolean
    ) {
        val boxBorder = Paint().apply {
            color = borderGray
            style = Paint.Style.STROKE
            strokeWidth = 0.9f
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
        }
        val labels = if (includeStudentAndParent) {
            listOf(
                "Academy Authorized Signature",
                "Academy Official Stamp",
                "Parent / Guardian Signature",
                "Student Signature"
            )
        } else {
            listOf("Academy Authorized Signature", "Academy Official Stamp")
        }

        val count = labels.size
        val gap = 10f
        val boxW = (PAGE_WIDTH - MARGIN * 2 - gap * (count - 1)) / count
        val boxH = 72f

        labels.forEachIndexed { idx, label ->
            val left = MARGIN + idx * (boxW + gap)
            canvas.drawRect(left, y, left + boxW, y + boxH, boxBorder)
            if (idx == 0 && signature != null) {
                canvas.drawBitmap(signature, null, RectF(left + 12f, y + 6f, left + boxW - 12f, y + 48f), null)
            } else if (idx == 1 && stamp != null) {
                canvas.drawBitmap(stamp, null, RectF(left + (boxW - 46f) / 2f, y + 4f, left + (boxW + 46f) / 2f, y + 50f), null)
            }
            canvas.drawLine(left + 8f, y + 54f, left + boxW - 8f, y + 54f, Paint().apply {
                color = borderGray
                strokeWidth = 0.7f
            })
            canvas.drawText(label, left + boxW / 2f, y + 66f, labelPaint)
        }
    }

    private fun drawHeaderBanner(
        canvas: Canvas,
        logo: Bitmap,
        academyName: String,
        subtitle: String,
        token: String
    ) {
        val bannerPaint = Paint().apply { color = navyColor }
        canvas.drawRect(MARGIN, 22f, PAGE_WIDTH - MARGIN, 94f, bannerPaint)

        // White rounded square behind logo for crisp printing
        val logoBg = Paint().apply { color = Color.WHITE }
        canvas.drawRoundRect(RectF(MARGIN + 10f, 30f, MARGIN + 66f, 86f), 6f, 6f, logoBg)
        canvas.drawBitmap(logo, null, RectF(MARGIN + 13f, 33f, MARGIN + 63f, 83f), null)

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(academyName.uppercase(Locale.US), MARGIN + 78f, 49f, titlePaint)

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(186, 230, 253)
            textSize = 9.2f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(subtitle, MARGIN + 78f, 65f, subPaint)

        val tokenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 9.5f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        canvas.drawText("TOKEN: $token", MARGIN + 78f, 81f, tokenPaint)
    }

    private fun drawSectionBar(canvas: Canvas, y: Float, title: String): Float {
        val bg = Paint().apply { color = lightBg }
        val accent = Paint().apply { color = cobaltColor }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 20f, bg)
        canvas.drawRect(MARGIN, y, MARGIN + 4f, y + 20f, accent)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = navyColor
            textSize = 9.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title, MARGIN + 10f, y + 13.5f, textPaint)
        return y + 20f
    }

    private fun drawOmrSectionHeader(canvas: Canvas, y: Float, title: String) {
        val bg = Paint().apply { color = Color.BLACK }
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 15f, bg)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.2f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText(title, MARGIN + 6f, y + 10.5f, textPaint)
    }

    private fun drawKeyValueGrid(canvas: Canvas, startY: Float, rows: List<Pair<String, String>>): Float {
        val colW = (PAGE_WIDTH - MARGIN * 2 - 12f) / 2f
        val leftRows = rows.filterIndexed { i, _ -> i % 2 == 0 }
        val rightRows = rows.filterIndexed { i, _ -> i % 2 == 1 }
        val y1 = drawKeyValueColumn(canvas, MARGIN, startY, colW, leftRows)
        val y2 = drawKeyValueColumn(canvas, MARGIN + colW + 12f, startY, colW, rightRows)
        return maxOf(y1, y2)
    }

    private fun drawKeyValueColumn(
        canvas: Canvas,
        x: Float,
        startY: Float,
        width: Float,
        rows: List<Pair<String, String>>
    ): Float {
        var y = startY
        val border = Paint().apply { color = borderGray; style = Paint.Style.STROKE; strokeWidth = 0.7f }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.8f
            typeface = Typeface.DEFAULT_BOLD
        }
        val valPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = darkText
            textSize = 9.2f
            typeface = Typeface.DEFAULT_BOLD
        }
        rows.forEach { (label, value) ->
            canvas.drawRect(x, y, x + width, y + 26f, border)
            canvas.drawText(label.uppercase(Locale.US), x + 6f, y + 10f, labelPaint)
            val truncated = if (value.length > 38) value.take(36) + ".." else value
            canvas.drawText(truncated, x + 6f, y + 21.5f, valPaint)
            y += 26f
        }
        return y
    }

    private fun drawSubjectsTable(
        canvas: Canvas,
        startY: Float,
        subjects: List<String>,
        paperCodes: List<String>
    ): Float {
        var y = startY
        val headerBg = Paint().apply { color = navyColor }
        val border = Paint().apply { color = borderGray; style = Paint.Style.STROKE; strokeWidth = 0.8f }
        val rowH = 20f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowH, headerBg)
        val hPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
        }
        canvas.drawText("#", MARGIN + 8f, y + 13.5f, hPaint)
        canvas.drawText("SUBJECT NAME", MARGIN + 34f, y + 13.5f, hPaint)
        canvas.drawText("UNIQUE PAPER CODE", MARGIN + 310f, y + 13.5f, hPaint)
        y += rowH

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = darkText; textSize = 9f }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = cobaltColor
            textSize = 9f
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        subjects.zip(paperCodes).forEachIndexed { idx, (name, code) ->
            if (idx % 2 == 1) {
                canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowH, Paint().apply { color = lightBg })
            }
            canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + rowH, border)
            canvas.drawText("${idx + 1}", MARGIN + 8f, y + 13.5f, cellPaint)
            canvas.drawText(name, MARGIN + 34f, y + 13.5f, cellPaint)
            canvas.drawText(code, MARGIN + 310f, y + 13.5f, codePaint)
            y += rowH
        }
        return y
    }

    private fun drawMultilineText(
        canvas: Canvas,
        text: String,
        x: Float,
        startY: Float,
        maxWidth: Float,
        lineHeight: Float,
        paint: Paint
    ): Float {
        var y = startY
        text.split("\n").forEach { paragraph ->
            if (paragraph.isBlank()) {
                y += lineHeight * 0.5f
                return@forEach
            }
            val words = paragraph.split(" ")
            var line = ""
            for (word in words) {
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (paint.measureText(candidate) <= maxWidth) {
                    line = candidate
                } else {
                    canvas.drawText(line, x, y, paint)
                    y += lineHeight
                    line = word
                }
            }
            if (line.isNotEmpty()) {
                canvas.drawText(line, x, y, paint)
                y += lineHeight
            }
        }
        return y
    }

    private fun drawFooter(canvas: Canvas, leftText: String, pageNum: Int, totalPages: Int) {
        val y = PAGE_HEIGHT - 16f
        canvas.drawLine(MARGIN, y - 10f, PAGE_WIDTH - MARGIN, y - 10f, Paint().apply {
            color = borderGray
            strokeWidth = 0.6f
        })
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = mutedText; textSize = 7.8f }
        canvas.drawText(leftText, MARGIN, y, p)
        val rightP = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedText
            textSize = 7.8f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Page $pageNum of $totalPages", PAGE_WIDTH - MARGIN, y, rightP)
    }

    private fun formatNum(value: Double): String =
        if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
}
