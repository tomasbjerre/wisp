package com.github.tomasbjerre.wisp.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri

/**
 * Writes an exported activity image directly to a location the user picked via
 * Android's Storage Access Framework ([android.content.Intent.ACTION_CREATE_DOCUMENT]) —
 * a genuine on-device save, distinct from handing the file to another app via the share
 * sheet (see [ImageShareIntent]). See specs/export.md#trigger and issue #141.
 */
object ImageDeviceWriter {
    fun write(
        context: Context,
        uri: Uri,
        image: Bitmap,
    ) {
        context.contentResolver.openOutputStream(uri)?.use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
