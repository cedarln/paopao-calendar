package com.paopao.calendar.domain.export

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 导出范围与文件名。
 *
 * 范围决定了月报分几节、ICS 里有几天的事件，所以「最近 2 个月到底是哪几天」
 * 必须钉死在这里，而不是等用户发现少导了一个月才回过头来查。
 */
class ExportModelsTest {

    @Test
    fun `presets cut on calendar month boundaries`() {
        val today = LocalDate.of(2026, 10, 29)
        assertEquals(
            ExportRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31)),
            ExportRange.preset(ExportPreset.LAST_2_MONTHS, today),
        )
        assertEquals(
            ExportRange(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 29)),
            ExportRange.preset(ExportPreset.THIS_MONTH, today),
        )
        assertEquals(
            ExportRange(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 29)),
            ExportRange.preset(ExportPreset.THIS_YEAR, today),
        )
        assertEquals(
            ExportRange(LocalDate.of(2023, 10, 1), LocalDate.of(2026, 10, 31)),
            ExportRange.preset(ExportPreset.LAST_3_YEARS, today),
        )
    }

    @Test
    fun `custom preset falls back to this month until the picker supplies a range`() {
        val today = LocalDate.of(2026, 10, 29)
        assertEquals(
            ExportRange.preset(ExportPreset.THIS_MONTH, today),
            ExportRange.preset(ExportPreset.CUSTOM, today),
        )
    }

    @Test
    fun `months walks inclusive month ends`() {
        val range = ExportRange(LocalDate.of(2026, 11, 15), LocalDate.of(2027, 2, 3))
        assertEquals(
            listOf("2026-11", "2026-12", "2027-01", "2027-02"),
            range.months().map { it.toString() },
        )
    }

    @Test
    fun `a range ending on the 31st still counts months correctly`() {
        val range = ExportRange(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1))
        assertEquals(3, range.months().size)
        // 1月31 + 2月28天 + 3月1日 = 30 天（含两端）
        assertEquals(30, range.days)
    }

    @Test
    fun `reversed range is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            ExportRange(LocalDate.of(2026, 5, 2), LocalDate.of(2026, 5, 1))
        }
    }

    @Test
    fun `file token is ascii digits only`() {
        val range = ExportRange(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31))
        assertEquals("20260301_20260331", range.fileToken())
        assertTrue(range.displayText().contains("31 天"))
    }

    @Test
    fun `artifact name carries format, range and second`() {
        val millis = LocalDate.of(2026, 4, 1).atTime(8, 30, 15)
            .atZone(ExportFixtures.zone).toInstant().toEpochMilli()
        assertEquals(
            "paopao-markdown-20260301_20260331-083015.md",
            ExportArtifactNames.fileName(ExportFormat.MARKDOWN, ExportFixtures.march, millis, ExportFixtures.zone),
        )
        assertEquals(
            "paopao-json-20260301_20260331-083015.json",
            ExportArtifactNames.fileName(ExportFormat.JSON, ExportFixtures.march, millis, ExportFixtures.zone),
        )
    }
}
