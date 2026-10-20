package com.blaze.photobooth.data.export

import android.content.Intent

/** What the screens need from export (README §11.5): save to Photos and the share sheet. A fake in tests. */
interface ExportService {
    suspend fun save(jobs: List<ExportJob>, onProgress: (done: Int, total: Int) -> Unit): ExportOutcome

    /** The system share sheet for [jobs]; throws if a file is missing. */
    suspend fun shareIntent(jobs: List<ExportJob>): Intent
}

class BoothExportService(private val exporter: PhotoExporter, private val share: ShareHelper) : ExportService {
    override suspend fun save(jobs: List<ExportJob>, onProgress: (done: Int, total: Int) -> Unit) = exporter.save(jobs, onProgress)

    override suspend fun shareIntent(jobs: List<ExportJob>): Intent = share.chooser(jobs)
}
