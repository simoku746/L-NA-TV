package tv.own.owntv.ui.components

/**
 * Lina TV: extra TMDB search names for provider titles the normalizer leaves unsearchable, e.g.
 * "Saldırı - Onslaught (2026) TRSUB" → "Onslaught", "Saldırı". Used only for tiles with no poster,
 * only when a year is known (the search is year-filtered, so the top hit is reliable).
 */
object LinaTitleRescue {
    private val JUNK = Regex(
        """(?i)\b(trsub|trdub|tr\s*dublaj|dublaj|altyaz[ıi]l[ıi]|altyaz[ıi]|türkçe|turkce|sub|dub|tr|hd|fhd|uhd|4k|1080p|720p)\b""",
    )
    private val SPACES = Regex("""\s{2,}""")
    private val tried = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    /** True the first time for a title this session; the rescue runs once per title per session. */
    fun firstTry(key: String): Boolean = tried.size < 5000 && tried.add(key)

    private val TITLE_BEFORE_YEAR = Regex("""^(.*?)\s*[\[(]\s*((?:19|20)\d{2})\s*[\])]""")

    /**
     * Search names with the year each should be searched under. The year in the first brackets wins
     * ("The Fix (2026) TRSub 2027'de Sinemalarda" is a 2026 film, not 2027).
     */
    fun candidates(rawName: String): List<Pair<String, Int>> {
        val norm = tv.own.owntv.core.metadata.TitleNormalizer.normalize(rawName)
        val base = norm.query
        val out = LinkedHashMap<String, Int>()
        fun addFrom(text: String, year: Int?) {
            if (year == null) return
            val clean = text.replace(JUNK, " ").replace(SPACES, " ").trim(' ', '-', '–', ':', '.', '|')
            val parts = clean.split(Regex("""\s+[-–]\s+""")).map { it.trim() }.filter { it.length >= 2 }
            if (parts.size >= 2) {
                out.putIfAbsent(parts.last(), year)   // usually the original (English) title
                out.putIfAbsent(parts.first(), year)  // the Turkish title
            }
            if (clean.length >= 2) out.putIfAbsent(clean, year)
            // "Fall 2: Ölümcül Tırmanış" → also the part before the colon.
            clean.substringBefore(':').trim().takeIf { ':' in clean && it.length >= 2 }?.let { out.putIfAbsent(it, year) }
        }
        TITLE_BEFORE_YEAR.find(rawName)?.let { m ->
            val head = tv.own.owntv.core.metadata.TitleNormalizer.normalize(m.groupValues[1]).query
            addFrom(head, m.groupValues[2].toIntOrNull())
        }
        addFrom(base, norm.year)
        val firstYear = TITLE_BEFORE_YEAR.find(rawName)?.groupValues?.get(2)?.toIntOrNull()
        return out.entries
            .filterNot { it.key.equals(base, ignoreCase = true) && it.value == norm.year }
            .map { it.key to it.value }
            .take(4)
            .ifEmpty { if (firstYear != null && firstYear != norm.year && base.isNotBlank()) listOf(base to firstYear) else emptyList() }
    }
}
