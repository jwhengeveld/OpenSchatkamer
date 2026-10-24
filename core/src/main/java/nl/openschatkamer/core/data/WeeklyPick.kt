package nl.openschatkamer.core.data

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

/**
 * Een "weekkeuze" voor de eigen secties van de app (die niet al op de site
 * wisselen): de hele week dezelfde selectie, op elk toestel, en elke maandag
 * een nieuwe. Elke rij gebruikt een eigen [salt], zodat ze niet samen verspringen.
 */
object WeeklyPick {

    /** Jaar en weeknummer (week begint op maandag, zoals in Nederland), bijv. 202643. */
    fun week(time: Calendar = Calendar.getInstance()): Int {
        val c = Calendar.getInstance(Locale.forLanguageTag("nl-NL")).apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            timeInMillis = time.timeInMillis
        }
        return c.getWeekYear() * 100 + c.get(Calendar.WEEK_OF_YEAR)
    }

    private fun random(salt: String, week: Int): Random = Random((week.toLong() shl 32) xor salt.hashCode().toLong())

    /** Dezelfde volgorde de hele week; volgende week een andere. */
    fun <T> shuffled(items: List<T>, salt: String, week: Int = week()): List<T> = items.shuffled(random(salt, week))

    /** Een getal van 0 tot [count] (exclusief) dat per week verandert. */
    fun index(count: Int, salt: String, week: Int = week()): Int = if (count <= 1) 0 else random(salt, week).nextInt(count)
}
