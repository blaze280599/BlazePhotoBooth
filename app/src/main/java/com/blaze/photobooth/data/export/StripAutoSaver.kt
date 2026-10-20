package com.blaze.photobooth.data.export

import android.util.Log
import com.blaze.photobooth.data.db.SessionEntity
import com.blaze.photobooth.data.prefs.BoothSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException

/** How the auto-save of one printed strip is going (§4.8). */
enum class AutoSaveStatus { Saving, Saved, Failed }

/**
 * *Auto-save strip to Photos after printing* (README §4.8, default off). Print calls [onPrinted]; when the switch is
 * on, the strip is saved in [scope] (the app's, so leaving Result doesn't stop it) and [status] says how it went,
 * so Result can show a small note and its Save strip button already done.
 */
class StripAutoSaver(
    private val settings: Flow<BoothSettings>,
    private val session: suspend (id: String) -> SessionEntity?,
    private val save: suspend (List<ExportJob>) -> ExportOutcome,
    private val scope: CoroutineScope,
) {
    private val statuses = MutableStateFlow<Map<String, AutoSaveStatus>>(emptyMap())
    val status: StateFlow<Map<String, AutoSaveStatus>> = statuses.asStateFlow()

    /** After Print: saves the strip of session [id] to Photos if the switch is on. */
    suspend fun onPrinted(id: String) {
        // A preferences read error must never fail the print itself.
        val on = try {
            settings.first().autoSaveStrip
        } catch (e: IOException) {
            Log.e("StripAutoSaver", "Couldn't read the auto-save switch", e)
            false
        }
        if (!on) return
        statuses.update { it + (id to AutoSaveStatus.Saving) }
        scope.launch {
            val ok = try {
                val row = session(id)
                row != null && save(listOf(ExportJob(row, ExportItem.Strip))).let { it.ok && it.saved.isNotEmpty() }
            } catch (e: Exception) {
                Log.e("StripAutoSaver", "Auto-save of $id failed", e)
                false
            }
            statuses.update { it + (id to if (ok) AutoSaveStatus.Saved else AutoSaveStatus.Failed) }
        }
    }
}
