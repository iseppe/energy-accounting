package com.empathie.energyaccounting.data.export

import com.empathie.energyaccounting.domain.model.DailyAccount
import com.empathie.energyaccounting.domain.model.PeriodInsights
import com.empathie.energyaccounting.domain.model.TimeSlot
import com.empathie.energyaccounting.domain.service.EnergyFormatter
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.Locale

/**
 * Renders a printable A4 report shaped after Maja Toudal's *Daily Energy Account Form*.
 *
 * The date range's entries are drawn as a bordered four-column table (header shading,
 * cell borders, wrapped cells, daily subtotal rows); the period summary and the
 * dimension / time-slot breakdowns follow as formatted sections.
 *
 * Emits a plain PDF 1.4 document directly (Helvetica text and vector lines), so it needs
 * no Context, no third-party library and no Android graphics runtime.
 */
class PractitionerPdfGenerator {

    fun generateReport(
        outputStream: OutputStream,
        startDate: LocalDate,
        endDate: LocalDate,
        accounts: List<DailyAccount>,
        insights: PeriodInsights,
        labels: PdfLabels = PdfLabels.english,
    ) {
        val report = PdfReportBuilder(labels)

        report.title(labels.title)
        report.paragraph(
            "${labels.periodLabel}: ${EnergyFormatter.formatDate(startDate)} - " +
                EnergyFormatter.formatDate(endDate)
        )
        report.spacer(8f)

        report.tableHeader()
        accounts.forEach { account ->
            TimeSlot.entries.forEach { timeSlot ->
                val ledger = account.ledgerFor(timeSlot)
                ledger.entries.forEach { entry ->
                    val note = entry.situationalNote?.let { " - $it" }.orEmpty()
                    report.tableRow(
                        listOf(
                            "${EnergyFormatter.formatDate(account.date)}\n" +
                                (labels.timeSlotNames[timeSlot] ?: timeSlot.name),
                            "${entry.activity}$note",
                            labels.resolveDimensionName(entry.dimension),
                            EnergyFormatter.formatSignedValue(entry.direction, entry.energyValue),
                        )
                    )
                }
            }
            report.dailyTotalsRow(
                "${labels.dailyTotals}: ${labels.gain} +${account.totalGain} | " +
                    "${labels.spend} -${account.totalSpend} | " +
                    "${labels.net} ${EnergyFormatter.formatNetBalance(account.netBalance)}"
            )
        }
        report.spacer(12f)

        report.section(labels.periodSummary)
        report.paragraph("${labels.daysLogged}: ${insights.activeDaysCount}")
        report.paragraph("${labels.totalGain}: +${insights.totalGain}")
        report.paragraph("${labels.totalSpend}: -${insights.totalSpend}")
        report.paragraph("${labels.netBalance}: ${EnergyFormatter.formatNetBalance(insights.netBalance)}")
        report.paragraph(
            "${labels.averageDailyNet}: ${EnergyFormatter.formatNetBalance(insights.averageDailyNet)}"
        )
        report.spacer(6f)

        report.section(labels.spendByPrimaryDimension)
        val spendDimensions = insights.byDimension.filter { it.spend > 0 }
        if (spendDimensions.isEmpty()) {
            report.paragraph("-")
        } else {
            spendDimensions.forEach {
                report.bullet(
                    "${labels.resolveDimensionName(it.dimension)}: ${it.spend} " +
                        "(${it.spendSharePercent}%)"
                )
            }
        }

        report.section(labels.gainByPrimaryDimension)
        val gainDimensions = insights.byDimension.filter { it.gain > 0 }
        if (gainDimensions.isEmpty()) {
            report.paragraph("-")
        } else {
            gainDimensions.forEach {
                report.bullet(
                    "${labels.resolveDimensionName(it.dimension)}: ${it.gain} " +
                        "(${it.gainSharePercent}%)"
                )
            }
        }

        report.section(labels.byTimeSlot)
        insights.byTimeSlot.forEach {
            report.bullet(
                "${labels.timeSlotNames[it.timeSlot] ?: it.timeSlot.name}: " +
                    "${labels.gain} +${it.totalGain} | ${labels.spend} -${it.totalSpend} | " +
                    "${labels.net} ${EnergyFormatter.formatNetBalance(it.netBalance)}"
            )
        }

        report.write(outputStream)
    }
}

/**
 * Minimal A4 PDF 1.4 page builder: Helvetica text runs, filled rectangles and stroked
 * table lines, with automatic pagination and footers.
 */
private class PdfReportBuilder(private val labels: PdfLabels) {

    private val pages = mutableListOf<StringBuilder>()
    private var y = 0f
    private var current = StringBuilder()

    init {
        newPage()
    }

    fun title(text: String) {
        ensureSpace(28f)
        drawText(text, MARGIN_LEFT, y, size = 17f, bold = true)
        y -= 26f
        current.append("0 0 0 rg\n")
    }

    fun section(title: String) {
        ensureSpace(26f)
        drawRect(MARGIN_LEFT, y - 18f, TABLE_WIDTH, 18f, gray = "0.93")
        drawText(title, MARGIN_LEFT + 5f, y - 12.5f, size = 10f, bold = true)
        y -= 26f
    }

    fun paragraph(text: String) {
        val lines = wrapWords(text, MAX_CHARS)
        ensureSpace(lines.size * LINE_HEIGHT + 4f)
        lines.forEach { line ->
            drawText(line, MARGIN_LEFT, y, size = 9f)
            y -= LINE_HEIGHT
        }
    }

    fun bullet(text: String) {
        val lines = wrapWords(text, MAX_CHARS - 4)
        ensureSpace(lines.size * LINE_HEIGHT + 3f)
        lines.forEachIndexed { index, line ->
            drawText(
                if (index == 0) "- $line" else "  $line",
                MARGIN_LEFT + 6f,
                y,
                size = 9f,
            )
            y -= LINE_HEIGHT
        }
    }

    fun spacer(height: Float) {
        ensureSpace(height)
        y -= height
    }

    fun tableHeader() {
        ensureSpace(20f)
        val top = y
        drawRect(MARGIN_LEFT, top - HEADER_HEIGHT, TABLE_WIDTH, HEADER_HEIGHT, gray = "0.90")
        var x = MARGIN_LEFT
        val columnTitles = listOf(
            labels.columnDateSlot,
            labels.columnActivity,
            labels.columnDimension,
            labels.columnValue,
        )
        columnTitles.forEachIndexed { index, title ->
            val textX = if (index == VALUE_COLUMN_INDEX) {
                x + COLUMN_WIDTHS[index] - CELL_PADDING - approxTextWidth(title, 8f)
            } else {
                x + CELL_PADDING
            }
            drawText(title, textX, top - 12.5f, size = 8f, bold = true)
            x += COLUMN_WIDTHS[index]
        }
        drawTableBorders(top, HEADER_HEIGHT)
        y -= HEADER_HEIGHT
    }

    fun tableRow(cells: List<String>) {
        val cellLines = cells.mapIndexed { index, text ->
            wrapWords(text, charsForWidth(COLUMN_WIDTHS[index]))
        }
        val lineCount = cellLines.maxOf { it.size }
        val height = lineCount * CELL_LINE_HEIGHT + CELL_PADDING * 2
        ensureSpace(height)
        val top = y

        var x = MARGIN_LEFT
        cellLines.forEachIndexed { index, lines ->
            lines.forEachIndexed { lineIndex, line ->
                val textX = if (index == VALUE_COLUMN_INDEX) {
                    x + COLUMN_WIDTHS[index] - CELL_PADDING - approxTextWidth(line, 8f)
                } else {
                    x + CELL_PADDING
                }
                drawText(
                    line,
                    textX,
                    top - CELL_PADDING - 7f - lineIndex * CELL_LINE_HEIGHT,
                    size = 8f,
                )
            }
            x += COLUMN_WIDTHS[index]
        }

        drawTableBorders(top, height)
        y -= height
    }

    fun dailyTotalsRow(text: String) {
        ensureSpace(18f)
        val top = y
        drawRect(MARGIN_LEFT, top - 17f, TABLE_WIDTH, 17f, gray = "0.95")
        drawText(text, MARGIN_LEFT + CELL_PADDING, top - 11.5f, size = 8.5f, bold = true)
        drawTableBorders(top, 17f)
        y -= 17f
    }

    fun write(outputStream: OutputStream) {
        if (current.isNotEmpty()) {
            pages += current
            current = StringBuilder()
        }

        document.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))

        val pageContents = pages.mapIndexed { index, builder ->
            builder.append("BT\n/F1 8 Tf\n")
            builder.append(
                String.format(
                    Locale.US,
                    "1 0 0 1 %.2f %.2f Tm\n",
                    PAGE_WIDTH - MARGIN_LEFT - 80f,
                    MARGIN_BOTTOM - 12f,
                )
            )
            builder.append("(Page ${index + 1} of ${pages.size}) Tj\nET\n")
            builder.toString().toByteArray(Charsets.ISO_8859_1)
        }

        val objects = buildObjects(pageContents)
        val offsets = IntArray(objects.size)
        objects.forEachIndexed { index, body ->
            offsets[index] = document.size()
            document.write("${index + 1} 0 obj\n".toByteArray(Charsets.ISO_8859_1))
            document.write(body)
            document.write("\nendobj\n".toByteArray(Charsets.ISO_8859_1))
        }

        val xrefOffset = document.size()
        document.write("xref\n0 ${objects.size + 1}\n".toByteArray(Charsets.ISO_8859_1))
        document.write("0000000000 65535 f \n".toByteArray(Charsets.ISO_8859_1))
        offsets.forEach { offset ->
            document.write(
                String.format(Locale.US, "%010d 00000 n \n", offset)
                    .toByteArray(Charsets.ISO_8859_1)
            )
        }
        document.write(
            (
                "trailer\n<< /Size ${objects.size + 1} /Root 1 0 R >>\n" +
                    "startxref\n$xrefOffset\n%%EOF\n"
                ).toByteArray(Charsets.ISO_8859_1)
        )

        document.writeTo(outputStream)
        outputStream.flush()
    }

    // ---- internals ---------------------------------------------------------

    private val document = ByteArrayOutputStream()

    private fun newPage() {
        if (current.isNotEmpty()) pages += current
        current = StringBuilder()
        current.append("0 0 0 rg\n")
        y = PAGE_HEIGHT - MARGIN_TOP
    }

    private fun ensureSpace(height: Float) {
        if (y - height < MARGIN_BOTTOM) newPage()
    }

    private fun drawText(text: String, x: Float, baseline: Float, size: Float, bold: Boolean = false) {
        if (text.isEmpty()) return
        val font = if (bold) "F2" else "F1"
        current.append("BT\n")
        current.append("/$font $size Tf\n")
        current.append(
            String.format(Locale.US, "1 0 0 1 %.2f %.2f Tm\n", x, baseline)
        )
        current.append("(${escape(text)}) Tj\n")
        current.append("ET\n")
    }

    private fun drawRect(x: Float, bottom: Float, width: Float, height: Float, gray: String) {
        current.append("$gray $gray $gray rg\n")
        current.append(
            String.format(Locale.US, "%.2f %.2f %.2f %.2f re\n", x, bottom, width, height)
        )
        current.append("f\n")
        current.append("0 0 0 rg\n")
    }

    private fun drawTableBorders(top: Float, height: Float) {
        val bottom = top - height
        current.append("0.6 w\n")
        current.append("0.68 0.68 0.68 RG\n")

        current.append(String.format(Locale.US, "%.2f %.2f m %.2f %.2f l S\n", MARGIN_LEFT, top, MARGIN_LEFT + TABLE_WIDTH, top))
        current.append(String.format(Locale.US, "%.2f %.2f m %.2f %.2f l S\n", MARGIN_LEFT, bottom, MARGIN_LEFT + TABLE_WIDTH, bottom))

        var x = MARGIN_LEFT
        current.append(String.format(Locale.US, "%.2f %.2f m %.2f %.2f l S\n", x, top, x, bottom))
        COLUMN_WIDTHS.forEach { width ->
            x += width
            current.append(String.format(Locale.US, "%.2f %.2f m %.2f %.2f l S\n", x, top, x, bottom))
        }

        current.append("0 RG\n")
    }

    private fun charsForWidth(width: Float): Int =
        ((width - CELL_PADDING * 2) / (FONT_SIZE * 0.54f)).toInt().coerceAtLeast(4)

    private fun approxTextWidth(text: String, size: Float): Float =
        text.length * size * 0.5f

    private fun wrapWords(text: String, maxChars: Int): List<String> {
        val result = mutableListOf<String>()
        text.split('\n').forEach { paragraph ->
            if (paragraph.isBlank()) {
                result += ""
                return@forEach
            }
            var currentLine = StringBuilder()
            paragraph.split(' ').forEach { word ->
                val candidate = if (currentLine.isEmpty()) word else "$currentLine $word"
                if (candidate.length <= maxChars) {
                    currentLine = StringBuilder(candidate)
                } else {
                    if (currentLine.isNotEmpty()) result += currentLine.toString()
                    currentLine = StringBuilder(word)
                }
            }
            if (currentLine.isNotEmpty()) result += currentLine.toString()
        }
        return result.ifEmpty { listOf("") }
    }

    private fun buildObjects(pageContents: List<ByteArray>): List<ByteArray> {
        val objects = mutableListOf<ByteArray>()

        objects += "<< /Type /Catalog /Pages 2 0 R >>".toByteArray(Charsets.ISO_8859_1)
        val kids = pageContents.indices.joinToString(" ") { "${FIRST_PAGE_OBJECT + it * 2} 0 R" }
        objects += "<< /Type /Pages /Kids [$kids] /Count ${pageContents.size} >>"
            .toByteArray(Charsets.ISO_8859_1)
        objects += (
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
            ).toByteArray(Charsets.ISO_8859_1)
        objects += (
            "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
            ).toByteArray(Charsets.ISO_8859_1)

        pageContents.forEachIndexed { index, content ->
            val pageObjectNumber = FIRST_PAGE_OBJECT + index * 2
            val contentObjectNumber = pageObjectNumber + 1

            objects += (
                "<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                    "/Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> " +
                    "/Contents $contentObjectNumber 0 R >>"
                ).toByteArray(Charsets.ISO_8859_1)

            val header = "<< /Length ${content.size} >>\nstream\n".toByteArray(Charsets.ISO_8859_1)
            val footer = "\nendstream".toByteArray(Charsets.ISO_8859_1)
            objects += header + content + footer
        }

        return objects
    }

    private fun escape(text: String): String = buildString {
        text.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '(' -> append("\\(")
                ')' -> append("\\)")
                '\n', '\r' -> append(' ')
                else -> when {
                    character.code in 32..126 -> append(character)
                    // WinAnsi high range: Italian accents (à è é ì ò ù) and the degree sign.
                    character.code in 160..255 -> append(character)
                    character == '—' || character == '–' -> append('-')
                    character == '’' -> append('\'')
                    character == '“' || character == '”' -> append('"')
                    character == '…' -> append("...")
                    else -> append('?')
                }
            }
        }
    }

    private companion object {
        const val PAGE_WIDTH = 595
        const val PAGE_HEIGHT = 842
        const val MARGIN_LEFT = 32f
        const val MARGIN_TOP = 44f
        const val MARGIN_BOTTOM = 40f
        const val LINE_HEIGHT = 12f
        const val CELL_LINE_HEIGHT = 10f
        const val CELL_PADDING = 4f
        const val HEADER_HEIGHT = 20f
        const val FONT_SIZE = 8f
        const val MAX_CHARS = 100

        const val FIRST_PAGE_OBJECT = 5
        const val VALUE_COLUMN_INDEX = 3

        val COLUMN_WIDTHS = listOf(115f, 230f, 95f, 75f)
        val TABLE_WIDTH = COLUMN_WIDTHS.sum()
    }
}
