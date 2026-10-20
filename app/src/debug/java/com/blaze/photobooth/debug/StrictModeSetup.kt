package com.blaze.photobooth.debug

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.StrictMode

/**
 * Debug builds only (README §8.2, M9): StrictMode logs any disk or network access on the main thread, slow calls,
 * and leaked closeables or registrations, from the very first line of startup (a provider runs before
 * `Application.onCreate`, and this one first of all: `initOrder` in the debug manifest). It only logs (tag
 * `StrictMode`), so it never crashes a test on the phone. Release builds don't contain it.
 */
class StrictModeSetup : ContentProvider() {

    override fun onCreate(): Boolean {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectDiskReads()
                .detectDiskWrites()
                .detectNetwork()
                .detectCustomSlowCalls()
                .detectResourceMismatches()
                .detectUnbufferedIo()
                .penaltyLog()
                .build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectLeakedRegistrationObjects()
                .detectActivityLeaks()
                .detectFileUriExposure()
                .detectContentUriWithoutPermission()
                .penaltyLog()
                .build(),
        )
        return true
    }

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = 0
}
