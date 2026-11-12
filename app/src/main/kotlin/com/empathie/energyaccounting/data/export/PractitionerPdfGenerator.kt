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
 * Renders a printable A4 report shaped after Maja Toudal's *Daily Energy Account Form*:
 * one block per day with one row per entry, then the period summary and both Insights
 * breakdowns.
 *
 * Emits a plain PDF 1.4 document directly (Helvetica text), so it needs no Context, no
 * third-party library and no Android graphics runtime. `android.graphics.pdf.PdfDocument`
 * was the original plan, but it requires a native PDF backend that unit tests on the JVM
 * do not have, which made the mandated test impossible.
 *
 * The report copy is intentionally English clinical wording so it can be handed to a
 * practitioner regardless of the app's UI language.
 */
class PractitionerPdfGenerator {

    fun generateReport(
        outputStream: OutputStream,
        startDate: LocalDate,
        endDate: LocalDate,
        accounts: List<DailyAccount>,
        insights: PeriodInsights,
    ) {
        val lines = buildLines(startDate, endDate, accounts, insights)
        PdfTextDocument.write(outputStream, lines)
    }

    private fun buildLines(
        startDate: LocalDate,
        endDate: LocalDate,
        accounts: List<DailyAccount>,
        insights: PeriodInsights,
    ): List<PdfLine> {
        val lines = mutableListOf<PdfLine>()

        fun add(text: String, style: PdfStyle = PdfStyle.BODY) {
            wrap(text, maxChars = MAX_BODY_CHARS).forEach { lines += PdfLine(it, style) }
        }

        add("Daily Energy Account", PdfStyle.TITLE)
        add("Period: ${EnergyFormatter.formatDate(startDate)} - ${EnergyFormatter.formatDate(endDate)}")
        lines += PdfLine("")

        accounts.forEach { account ->
            add(EnergyFormatter.formatDate(account.date), PdfStyle.HEADING)
            TimeSlot.entries.forEach { timeSlot ->
                val ledger = account.ledgerFor(timeSlot)
                if (ledger.entries.isEmpty()) return@forEach
                add(
                    "${slotLabel(timeSlot)}  (net ${EnergyFormatter.formatNetBalance(ledger.netBalance)})",
                    PdfStyle.HEADING,
                )
                ledger.entries.forEach { entry ->
                    val dimensions = buildString {
                        append("1° ").append(entry.primaryDimension.name)
                        if (entry.secondaryDimensions.isNotEmpty()) {
                            append(" / 2° ")
                            append(entry.secondaryDimensions.joinToString(", ") { it.name })
                        }
                    }
                    val note = entry.situationalNote?.let { " - $it" }.orEmpty()
                    val value = EnergyFormatter.formatSignedValue(entry.direction, entry.energyValue)
                    add("   $value   ${entry.activity}$note   [$dimensions]")
                }
            }
            add(
                "Daily totals: gain +${account.totalGain} | spend -${account.totalSpend} | " +
                    "net ${EnergyFormatter.formatNetBalance(account.netBalance)}",
                PdfStyle.HEADING,
            )
            lines += PdfLine("")
        }

        add("Period summary", PdfStyle.HEADING)
        add("Days logged: ${insights.activeDaysCount}")
        add("Total gain: +${insights.totalGain}")
        add("Total spend: -${insights.totalSpend}")
        add("Net balance: ${EnergyFormatter.formatNetBalance(insights.netBalance)}")
        add("Average daily net: ${EnergyFormatter.formatNetBalance(insights.averageDailyNet)}")
        lines += PdfLine("")

        add("Spend by primary dimension", PdfStyle.HEADING)
        insights.byDimension
            .filter { it.primarySpend > 0 }
            .forEach { add("   ${it.dimension.name}: ${it.primarySpend} (${it.primarySpendSharePercent}%)") }

        add("Gain by primary dimension", PdfStyle.HEADING)
        insights.byDimension
            .filter { it.primaryGain > 0 }
            .forEach { add("   ${it.dimension.name}: ${it.primaryGain} (${it.primaryGainSharePercent}%)") }

        add("Secondary dimension occurrences", PdfStyle.HEADING)
        insights.byDimension
            .filter { it.secondaryOccurrenceCount > 0 }
            .forEach { add("   ${it.dimension.name}: ${it.secondaryOccurrenceCount}") }

        add("By time slot", PdfStyle.HEADING)
        insights.byTimeSlot.forEach {
            add(
                "   ${slotLabel(it.timeSlot)}: gain +${it.totalGain} | spend -${it.totalSpend} | " +
                    "net ${EnergyFormatter.formatNetBalance(it.netBalance)}"
            )
        }

        return lines
    }

    private fun wrap(text: String, maxChars: Int): List<String> {
        if (text.isEmpty()) return listOf("")
        val words = text.split(' ')
        val result = mutableListOf<String>()
        var current = StringBuilder()

        words.forEach { word ->
            val candidateLength = if (current.isEmpty()) word.length else current.length + 1 + word.length
            if (candidateLength <= maxChars) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(word)
            } else {
                if (current.isNotEmpty()) result += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result.ifEmpty { listOf("") }
    }

    private fun slotLabel(timeSlot: TimeSlot): String = when (timeSlot) {
        TimeSlot.EARLY_MORNING -> "Early Morning"
        TimeSlot.LATE_MORNING -> "Late Morning"
        TimeSlot.AFTERNOON -> "Afternoon"
        TimeSlot.EVENING -> "Evening"
    }

    private enum class PdfStyle { TITLE, HEADING, BODY }

    private companion object {
        const val MAX_BODY_CHARS = 105
    }
    private data class PdfLine(
        val text: String,
        val style: PdfStyle = PdfStyle.BODY,
    )

    /**
     * Minimal PDF 1.4 writer: A4 pages, Helvetica, absolute-positioned text runs.
     */
    private object PdfTextDocument {
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842
        private const val MARGIN_LEFT = 40f
        private const val MARGIN_TOP = 45f
        private const val MARGIN_BOTTOM = 45f
        private const val FOOTER_HEIGHT = 20f

        private const val OBJECT_CATALOG = 1
        private const val OBJECT_PAGES = 2
        private const val OBJECT_FONT_REGULAR = 3
        private const val OBJECT_FONT_BOLD = 4
        private const val FIRST_PAGE_OBJECT = 5

        fun write(out: OutputStream, lines: List<PdfLine>) {
            val pages = paginate(lines)
            val objects = buildObjects(pages)

            val document = ByteArrayOutputStream()
            document.write("%PDF-1.4\n".toByteArray(Charsets.ISO_8859_1))

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
                val entry = String.format(Locale.US, "%010d 00000 n \n", offset)
                document.write(entry.toByteArray(Charsets.ISO_8859_1))
            }
            document.write(
                (
                    "trailer\n<< /Size ${objects.size + 1} /Root $OBJECT_CATALOG 0 R >>\n" +
                        "startxref\n$xrefOffset\n%%EOF\n"
                    ).toByteArray(Charsets.ISO_8859_1)
            )

            document.writeTo(out)
            out.flush()
        }

        private fun paginate(lines: List<PdfLine>): List<List<PdfLine>> {
            val bottom = MARGIN_BOTTOM + FOOTER_HEIGHT
            val usableHeight = PAGE_HEIGHT - MARGIN_TOP - bottom
            val pages = mutableListOf<List<PdfLine>>()
            var current = mutableListOf<PdfLine>()
            var used = 0f

            lines.forEach { line ->
                val advance = advanceOf(line)
                if (used + advance > usableHeight && current.isNotEmpty()) {
                    pages += current
                    current = mutableListOf()
                    used = 0f
                }
                current += line
                used += advance
            }
            if (current.isNotEmpty()) pages += current
            return pages.ifEmpty { listOf(emptyList()) }
        }

        private fun buildObjects(pages: List<List<PdfLine>>): List<ByteArray> {
            val objects = mutableListOf<ByteArray>()

            objects += "<< /Type /Catalog /Pages $OBJECT_PAGES 0 R >>".bytes()
            val kids = pages.indices.joinToString(" ") { "${FIRST_PAGE_OBJECT + it * 2} 0 R" }
            objects += "<< /Type /Pages /Kids [$kids] /Count ${pages.size} >>".bytes()
            objects += (
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>"
                ).bytes()
            objects += (
                "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>"
                ).bytes()

            pages.forEachIndexed { index, pageLines ->
                val pageObjectNumber = FIRST_PAGE_OBJECT + index * 2
                val contentObjectNumber = pageObjectNumber + 1
                val content = renderContent(pageLines, index, pages.size)
                val contentBytes = content.toByteArray(Charsets.ISO_8859_1)

                objects += (
                    "<< /Type /Page /Parent $OBJECT_PAGES 0 R " +
                        "/MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] " +
                        "/Resources << /Font << /F1 $OBJECT_FONT_REGULAR 0 R /F2 $OBJECT_FONT_BOLD 0 R >> >> " +
                        "/Contents $contentObjectNumber 0 R >>"
                    ).bytes()

                val streamHeader = "<< /Length ${contentBytes.size} >>\nstream\n"
                objects += streamHeader.toByteArray(Charsets.ISO_8859_1) +
                    contentBytes +
                    "\nendstream".toByteArray(Charsets.ISO_8859_1)
            }

            return objects
        }

        private fun renderContent(lines: List<PdfLine>, pageIndex: Int, pageCount: Int): String {
            val builder = StringBuilder()
            var y = PAGE_HEIGHT - MARGIN_TOP

            lines.forEach { line ->
                val font = if (line.style == PdfStyle.BODY) "F1" else "F2"
                val size = when (line.style) {
                    PdfStyle.TITLE -> 16f
                    PdfStyle.HEADING -> 9.5f
                    PdfStyle.BODY -> 9f
                }
                if (line.text.isNotEmpty()) {
                    builder.append("BT\n")
                    builder.append("/$font $size Tf\n")
                    builder.append(
                        String.format(
                            Locale.US,
                            "1 0 0 1 %.2f %.2f Tm\n",
                            MARGIN_LEFT,
                            y,
                        )
                    )
                    builder.append("(${escape(line.text)}) Tj\n")
                    builder.append("ET\n")
                }
                y -= advanceOf(line)
            }

            builder.append("BT\n/F1 8 Tf\n")
            builder.append(
                String.format(
                    Locale.US,
                    "1 0 0 1 %.2f %.2f Tm\n",
                    PAGE_WIDTH - 120f,
                    MARGIN_BOTTOM - 12f,
                )
            )
            builder.append("(Page ${pageIndex + 1} of $pageCount) Tj\nET\n")
            return builder.toString()
        }

        private fun advanceOf(line: PdfLine): Float = when (line.style) {
            PdfStyle.TITLE -> 26f
            PdfStyle.HEADING -> 15f
            PdfStyle.BODY -> 13f
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
                        character == '°' -> append("deg")
                        character == '—' || character == '–' -> append('-')
                        character == '’' -> append('\'')
                        character == '“' || character == '”' -> append('"')
                        else -> append('?')
                    }
                }
            }
        }

        private fun String.bytes(): ByteArray = toByteArray(Charsets.ISO_8859_1)
    }
}
