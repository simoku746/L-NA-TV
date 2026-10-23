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

    fun candidates(rawName: String): List<String> {
        val base = tv.own.owntv.core.metadata.TitleNormalizer.normalize(rawName).query
        val clean = base.replace(JUNK, " ").replace(SPACES, " ").trim(' ', '-', '–', ':', '.')
        val out = LinkedHashSet<String>()
        val parts = clean.split(Regex("""\s+[-–]\s+""")).map { it.trim() }.filter { it.length >= 2 }
        if (parts.size >= 2) {
            out += parts.last()   // usually the original (English) title
            out += parts.first()  // the Turkish title
        }
        if (clean != base && clean.length >= 2) out += clean
        // "Fall 2: Ölümcül Tırmanış" → also try the part before the colon.
        clean.substringBefore(':').trim().takeIf { ':' in clean && it.length >= 2 }?.let { out += it }
        return out.filter { !it.equals(base, ignoreCase = true) }.take(3)
    }
}
