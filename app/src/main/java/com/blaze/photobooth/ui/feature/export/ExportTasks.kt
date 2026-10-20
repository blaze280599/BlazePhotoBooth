package com.blaze.photobooth.ui.feature.export

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.Immutable
import com.blaze.photobooth.data.export.ExportError
import com.blaze.photobooth.data.export.ExportJob
import com.blaze.photobooth.data.export.ExportOutcome
import com.blaze.photobooth.data.export.ExportService
import com.blaze.photobooth.data.export.PhotoExporter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which save or share button a state belongs to. */
sealed interface ExportKey {
    data object Strip : ExportKey
    data class Photo(val shotIndex: Int) : ExportKey
    data object AllPhotos : ExportKey
    data object Share : ExportKey

    /** The album's multi-select *Save to Photos*. */
    data object Selection : ExportKey
}

/** A save button's look (§7 A12): its icon, a spinner while saving, then a ✓. */
enum class ExportPhase { Idle, Busy, Done }

/** [done] of [total] files are saved so far (while [phase] is Busy). */
@Immutable
data class ExportActionState(val phase: ExportPhase = ExportPhase.Idle, val done: Int = 0, val total: Int = 0) {
    companion object {
        val Idle = ExportActionState()
        val Done = ExportActionState(ExportPhase.Done)
    }
}

/** Every save/share button on a screen. While [busy], they're all disabled (one export at a time, no double taps). */
@Immutable
data class ExportActions(val states: Map<ExportKey, ExportActionState> = emptyMap(), val busy: Boolean = false) {
    operator fun get(key: ExportKey): ExportActionState = states[key] ?: ExportActionState.Idle
}

/** What was saved, for the snackbar's words. */
enum class SavedWhat { Strip, Strips, Photos }

/** One-shot results for the route: a snackbar (§7 A12, §13) or the share sheet. */
sealed interface ExportMessage {
    data class Saved(val what: SavedWhat, val count: Int, val missing: Int = 0) : ExportMessage

    /** [saved] files made it before the error. [retry] carries on with the rest (null when retrying can't help). */
    data class Failed(val error: ExportError, val saved: Int, val retry: (() -> Unit)?) : ExportMessage

    /** The auto-save after printing didn't work (§4.8). */
    data class AutoSaveFailed(val retry: () -> Unit) : ExportMessage

    data class Share(val intent: Intent) : ExportMessage
    data class ShareFailed(val error: ExportError) : ExportMessage
}

/**
 * The save and share buttons of one screen (Result, Detail, the Album's multi-select), run in its ViewModel's
 * [scope]. One export at a time: a tap while one is running is ignored. A batch saves one file after another with
 * progress; a failure offers Retry, which carries on from the file that failed. A button that saved stays ✓ (so the
 * same strip isn't saved twice by accident) until [reset].
 */
class ExportTasks(private val scope: CoroutineScope, private val service: ExportService) {

    private val state = MutableStateFlow(ExportActions())
    val actions: StateFlow<ExportActions> = state.asStateFlow()

    private val channel = Channel<ExportMessage>(Channel.BUFFERED)
    val messages: Flow<ExportMessage> = channel.receiveAsFlow()

    /**
     * Saves [jobs] to Photos under [key]. [savedBefore] counts files a failed run already saved, so the snackbar
     * after a Retry tells the whole story. [onSaved] runs after a success (e.g. the album clears its selection).
     */
    fun save(key: ExportKey, what: SavedWhat, jobs: List<ExportJob>, savedBefore: Int = 0, onSaved: () -> Unit = {}) {
        if (state.value.busy || jobs.isEmpty()) return
        set(key, ExportActionState(ExportPhase.Busy, 0, jobs.size), busy = true)
        scope.launch {
            val outcome = try {
                service.save(jobs) { done, total -> set(key, ExportActionState(ExportPhase.Busy, done, total), busy = true) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Export failed", e)
                ExportOutcome(emptyList(), error = PhotoExporter.errorOf(e), stoppedAt = 0)
            }
            val total = savedBefore + outcome.saved.size
            val error = outcome.error
            when {
                error != null -> {
                    set(key, ExportActionState.Idle, busy = false)
                    val rest = jobs.drop(outcome.stoppedAt ?: 0)
                    val retry = if (error == ExportError.MissingFile) null else ({ save(key, what, rest, total, onSaved) })
                    channel.send(ExportMessage.Failed(error, total, retry))
                }
                outcome.saved.isEmpty() -> {
                    // Every file was missing (§13): a friendly error, nothing to retry.
                    set(key, ExportActionState.Idle, busy = false)
                    channel.send(ExportMessage.Failed(ExportError.MissingFile, total, retry = null))
                }
                else -> {
                    set(key, ExportActionState.Done, busy = false)
                    channel.send(ExportMessage.Saved(what, total, outcome.missing))
                    onSaved()
                }
            }
        }
    }

    /** Makes the files for [jobs] and opens the system share sheet. */
    fun share(jobs: List<ExportJob>) {
        if (state.value.busy || jobs.isEmpty()) return
        set(ExportKey.Share, ExportActionState(ExportPhase.Busy, 0, jobs.size), busy = true)
        scope.launch {
            val message = try {
                ExportMessage.Share(service.shareIntent(jobs))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(Tag, "Share failed", e)
                ExportMessage.ShareFailed(PhotoExporter.errorOf(e))
            }
            set(ExportKey.Share, ExportActionState.Idle, busy = false)
            channel.send(message)
        }
    }

    /** Shows [key] as already saved (the auto-save after printing did it). */
    fun markDone(key: ExportKey) = set(key, ExportActionState.Done, busy = state.value.busy)

    fun reset(key: ExportKey) = state.update { it.copy(states = it.states - key) }

    /** Every ✓ goes back to its icon (the files changed, e.g. after an Edit). */
    fun resetDone() = state.update { a -> a.copy(states = a.states.filterValues { it.phase != ExportPhase.Done }) }

    fun post(message: ExportMessage) {
        channel.trySend(message)
    }

    private fun set(key: ExportKey, action: ExportActionState, busy: Boolean) =
        state.update { it.copy(states = it.states + (key to action), busy = busy) }

    private companion object {
        const val Tag = "ExportTasks"
    }
}
