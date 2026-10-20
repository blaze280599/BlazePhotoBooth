package com.blaze.photobooth.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import java.io.File

/**
 * Memory-cache keys for a saved strip's pictures (README §4.6, §4.7). [version] is the file's last-modified time,
 * so an edited strip gets a new key and is never shown stale. Detail uses the album's thumbnail key as its
 * placeholder, so the shared-element hero (§7 A10) has a picture from the first frame.
 */
object StripImageKeys {
    fun thumb(sessionId: String, version: Long) = "thumb:$sessionId@$version"
    fun strip(sessionId: String, version: Long) = "strip:$sessionId@$version"
}

/** A saved strip's `thumb.jpg` or `strip.jpg`, loaded by Coil off the main thread and sized to the view. */
@Composable
fun StripImage(
    file: File,
    memoryKey: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    placeholderKey: String? = null,
    contentScale: ContentScale = ContentScale.Fit,
    alignment: Alignment = Alignment.Center,
    colorFilter: ColorFilter? = null,
) {
    val context = LocalPlatformContext.current
    val request = remember(context, file, memoryKey, placeholderKey) {
        ImageRequest.Builder(context)
            .data(file)
            .memoryCacheKey(memoryKey)
            .placeholderMemoryCacheKey(placeholderKey)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        alignment = alignment,
        contentScale = contentScale,
        colorFilter = colorFilter,
    )
}
