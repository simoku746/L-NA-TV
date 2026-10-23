package tv.own.owntv.ui.components

import androidx.compose.runtime.mutableStateMapOf

/**
 * Lina TV: provider poster URLs that failed to load (dead image host, 404). Tiles treat them as
 * "no poster" so the TMDB fallback can take their place. Observable from Compose and snapshotFlow.
 */
object LinaPosterFailures {
    private val failed = mutableStateMapOf<String, Boolean>()
    fun mark(url: String) { if (failed.size < 5000) failed[url] = true }
    fun isFailed(url: String?): Boolean = url != null && failed[url] == true
    /** The provider poster if it is usable, else null. */
    fun usable(url: String?): String? = url?.takeIf { it.isNotBlank() && !isFailed(it) }
}
