package com.blaze.photobooth.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.StrictMode
import android.util.Log
import com.blaze.photobooth.core.ReduceMotionOverride
import java.io.File

/**
 * Debug builds only (README §6.7, §15.3): tries reduce motion on the phone without changing its settings. If
 * `files/debug_reduce_motion` says `on` (or `off`) when the app starts, that wins over the system's animation setting
 * until the file is deleted. The agent writes it with `adb shell run-as`; release builds don't contain this.
 */
class ReduceMotionTrigger : ContentProvider() {

    override fun onCreate(): Boolean {
        val file = File(context?.filesDir ?: return false, TriggerName)
        // A tiny file that must be read before the first frame; allowed so StrictMode's log stays about the app.
        val policy = StrictMode.allowThreadDiskReads()
        val override = try {
            parse(if (file.isFile) file.readText() else null)
        } finally {
            StrictMode.setThreadPolicy(policy)
        }
        ReduceMotionOverride.set(override)
        if (override != null) Log.i(Tag, "Reduce motion forced ${if (override) "on" else "off"} by $TriggerName")
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0

    companion object {
        const val Tag = "ReduceMotion"
        const val TriggerName = "debug_reduce_motion"

        /** `on` / `off` (any case, spaces ignored); anything else means "follow the system". */
        fun parse(text: String?): Boolean? = when (text?.trim()?.lowercase()) {
            "on", "true", "1" -> true
            "off", "false", "0" -> false
            else -> null
        }
    }
}
