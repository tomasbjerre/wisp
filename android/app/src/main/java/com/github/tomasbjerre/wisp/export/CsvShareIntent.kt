package com.github.tomasbjerre.wisp.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes the CSV to a cache file and wraps it in a share-sheet intent — see
 * specs/export.md#trigger: the user picks where the file goes, Wisp doesn't
 * write to a fixed location.
 */
object CsvShareIntent {
    private const val FILE_NAME = "wisp-history.csv"

    fun build(
        context: Context,
        csv: String,
    ): Intent {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, FILE_NAME)
        file.writeText(csv)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(sendIntent, "Export history as CSV")
    }
}
