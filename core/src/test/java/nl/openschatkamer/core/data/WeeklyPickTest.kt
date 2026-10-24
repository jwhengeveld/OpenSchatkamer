package nl.openschatkamer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WeeklyPickTest {

    private fun day(y: Int, m: Int, d: Int): Calendar = Calendar.getInstance().apply { set(y, m - 1, d, 12, 0) }

    private val items = (1..20).toList()

    @Test
    fun `de hele week dezelfde keuze`() {
        val monday = WeeklyPick.week(day(2026, 10, 19))
        val sunday = WeeklyPick.week(day(2026, 10, 25))
        assertEquals(monday, sunday)
        assertEquals(WeeklyPick.shuffled(items, "kids", monday), WeeklyPick.shuffled(items, "kids", sunday))
    }

    @Test
    fun `volgende week en andere rij geven een andere keuze`() {
        val week = WeeklyPick.week(day(2026, 10, 19))
        val next = WeeklyPick.week(day(2026, 10, 26))
        assertNotEquals(WeeklyPick.shuffled(items, "kids", week), WeeklyPick.shuffled(items, "kids", next))
        assertNotEquals(WeeklyPick.shuffled(items, "kids", week), WeeklyPick.shuffled(items, "poppenspel", week))
    }

    @Test
    fun `index blijft binnen het bereik`() {
        repeat(60) { w -> assertTrue(WeeklyPick.index(5, "x", 202600 + w) in 0 until 5) }
        assertEquals(0, WeeklyPick.index(1, "x"))
    }
}
