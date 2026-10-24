package nl.openschatkamer.core.data

/**
 * Maakt de archiefteksten van de Schatkamer leesbaar: titels in hoofdletters,
 * ontbrekende afleveringstitels, interne voorvoegsels en datums.
 */
object SchatkamerText {

    private val MONTHS = listOf(
        "januari", "februari", "maart", "april", "mei", "juni",
        "juli", "augustus", "september", "oktober", "november", "december"
    )
    private val ISO_DATE = Regex("""^(\d{4})-(\d{2})-(\d{2})""")
    private val DUTCH_DATE = Regex("""^(\d{1,2})\s+([a-z]+)\s+(\d{4})$""", RegexOption.IGNORE_CASE)
    private val YEAR = Regex("""\b(1[89]\d{2}|20\d{2})\b""")
    private val TAGS = Regex("<[^>]+>")
    private val INTERNAL_PREFIX = Regex(
        """^\s*(EXTERNE INFORMATIE|INTERNE INFORMATIE|BRON|OPMERKING)\s*:\s*""",
        RegexOption.IGNORE_CASE
    )
    private val MISSING_SPACE = Regex("""([a-zà-ÿ]{3,})\.([A-Z][a-zà-ÿ])""")

    private val GENERIC_TITLES = setOf(
        "geen afleveringstitel", "zonder titel", "geen titel", "onbekend", "niet bekend",
        "null", "undefined", "\$undefined"
    )

    fun clean(s: String?): String {
        val t = s?.trim().orEmpty()
        return if (t.lowercase() in setOf("null", "undefined", "\$undefined")) "" else t
    }

    fun stripTags(s: String?): String = clean(s).replace(TAGS, "")

    fun isGenericTitle(title: String?): Boolean {
        val t = clean(title).lowercase()
        return t.isBlank() || t in GENERIC_TITLES || t.startsWith("niet bekend")
    }

    private val ACRONYMS = setOf(
        "NOS", "VPRO", "TV", "KRO", "NCRV", "AVRO", "TROS", "VARA", "BNN", "EO", "NTR", "NPS",
        "WNL", "MAX", "NPO", "RTL", "SBS", "ADE", "VN", "EU", "USA", "DJ", "II", "III", "IV"
    )
    private val SMALL_WORDS = setOf(
        "de", "het", "een", "en", "van", "in", "op", "met", "voor", "aan", "bij", "uit", "of",
        "te", "der", "den", "naar", "om", "over", "tot", "'t"
    )

    /**
     * "JISKEFET" wordt "Jiskefet", "THEO EN THEA" wordt "Theo en Thea" en
     * "TV SHOW" wordt "TV Show". Titels met kleine letters blijven zoals ze zijn.
     */
    fun prettyTitle(raw: String?): String {
        val t = clean(raw).replace(Regex("\\s+"), " ")
        if (t.isBlank() || t.any { it.isLowerCase() }) return t
        if (t.count { it.isLetter() } < 5) return t
        var capitalizeNext = true
        return t.split(" ").joinToString(" ") { word ->
            val core = word.trim(':', ',', '.', '!', '?', '"', '(', ')')
            val lower = word.lowercase()
            val result = when {
                core in ACRONYMS -> word
                !capitalizeNext && core.lowercase() in SMALL_WORDS -> lower
                else -> lower.replaceFirstChar { it.titlecase() }
                    .replace(Regex("([-(\"])([a-zà-ÿ])")) { m -> m.groupValues[1] + m.groupValues[2].uppercase() }
            }
            capitalizeNext = word.endsWith(":") || word.endsWith(".") || word.endsWith("-")
            result
        }
    }

    /** Beschrijving zonder interne archiefvoorvoegsels en met spaties na zinnen. */
    fun cleanDescription(raw: String?): String =
        stripTags(raw)
            .replace(INTERNAL_PREFIX, "")
            .replace(MISSING_SPACE, "$1. $2")
            // Harde regeleinden midden in een zin (oude archiefinvoer) worden spaties.
            .replace(Regex("""([^\n.:;!?])\n(?=[a-zà-ÿ(])"""), "$1 ")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()

    /** "2012-05-04" of "4 mei 2012" naar "4 mei 2012"; onbekende vormen blijven staan. */
    fun formatDate(date: String?): String {
        val d = clean(date)
        ISO_DATE.find(d)?.let { m ->
            val (y, mo, day) = m.destructured
            val month = MONTHS.getOrNull(mo.toInt() - 1) ?: return d
            return "${day.toInt()} $month $y"
        }
        return d
    }

    /** Naar ISO-vorm als het een Nederlandse datum is ("13 november 1995"), anders ongewijzigd. */
    fun toIsoDate(date: String?): String {
        val d = clean(date)
        if (ISO_DATE.containsMatchIn(d)) return d.take(10)
        DUTCH_DATE.find(d)?.let { m ->
            val (day, monthName, y) = m.destructured
            val month = MONTHS.indexOf(monthName.lowercase()) + 1
            if (month > 0) return "%s-%02d-%02d".format(y, month, day.toInt())
        }
        return d
    }

    fun year(date: String?): String = YEAR.find(clean(date))?.value.orEmpty()

    /**
     * Een bruikbare titel voor een uitzending. Veel archiefstukken hebben geen
     * eigen titel; dan wordt het "Uitzending van 16 september 1990" (de serie
     * staat er in de apps al boven) of, zonder datum, het begin van de beschrijving.
     */
    fun episodeTitle(rawTitle: String?, seriesTitle: String?, date: String?, description: String?): String {
        val raw = stripTags(rawTitle)
        if (!isGenericTitle(raw)) return prettyTitle(raw)
        val formatted = formatDate(date)
        if (formatted.isNotBlank()) return "Uitzending van $formatted"
        val desc = cleanDescription(description)
        if (desc.isNotBlank()) {
            val sentence = desc.substringBefore(". ").take(70).trim()
            if (sentence.isNotBlank()) return sentence
        }
        return prettyTitle(seriesTitle).ifBlank { "Archiefuitzending" }
    }
}
