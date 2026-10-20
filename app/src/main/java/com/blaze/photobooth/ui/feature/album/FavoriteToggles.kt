package com.blaze.photobooth.ui.feature.album

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The ♡ shows her tap at once, however fast she taps (README §4.6, §13), while the writes catch up in order.
 * A heart's own value ([overrides]) wins over the stored one until every write for it has landed **and** the stored
 * value agrees, so a stale database emission never makes it flicker. A failed write falls back to the stored value.
 * Used by Album and Detail.
 */
class FavoriteToggles(
    private val scope: CoroutineScope,
    private val write: suspend (id: String, favorite: Boolean) -> Unit,
) {
    private data class Pending(val value: Boolean, val inFlight: Int)

    private val pending = MutableStateFlow<Map<String, Pending>>(emptyMap())
    private val shown = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    @Volatile
    private var stored: Map<String, Boolean> = emptyMap()

    /** Hearts that differ (or may still differ) from the database: id → what she sees. */
    val overrides: StateFlow<Map<String, Boolean>> = shown.asStateFlow()

    /** What the heart of [id] shows, given the stored value. */
    fun resolve(id: String, storedValue: Boolean): Boolean = pending.value[id]?.value ?: storedValue

    /** Flips the heart of [id] from what it shows now ([storedValue] if there's no override). Returns the new value. */
    fun toggle(id: String, storedValue: Boolean): Boolean {
        val next = !resolve(id, storedValue)
        update { it + (id to Pending(next, (it[id]?.inFlight ?: 0) + 1)) }
        scope.launch {
            try {
                write(id, next)
                settle(id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("FavoriteToggles", "Couldn't save the favourite of $id", e)
                update { it - id }
            }
        }
        return next
    }

    /** The database's latest favourites (id → is_favorite). */
    fun onStored(values: Map<String, Boolean>) {
        stored = values
        update { m -> m.filterNot { (id, p) -> p.inFlight <= 0 && values[id] == p.value } }
    }

    private fun settle(id: String) = update { m ->
        val p = m[id] ?: return@update m
        val left = p.copy(inFlight = p.inFlight - 1)
        if (left.inFlight <= 0 && stored[id] == left.value) m - id else m + (id to left)
    }

    private inline fun update(change: (Map<String, Pending>) -> Map<String, Pending>) {
        pending.update(change)
        shown.value = pending.value.mapValues { it.value.value }
    }
}
