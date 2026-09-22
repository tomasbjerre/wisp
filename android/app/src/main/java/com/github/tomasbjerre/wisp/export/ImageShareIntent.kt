package com.github.tomasbjerre.wisp.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

/** Writes the activity image to a cache file and wraps it in a share-sheet intent. */
object ImageShareIntent {
    private const val FILE_NAME = "wisp-activity.png"

    fun build(
        context: Context,
        image: Bitmap,
    ): Intent {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, FILE_NAME)
        FileOutputStream(file).use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(sendIntent, "Export activity as image")
    }
}
