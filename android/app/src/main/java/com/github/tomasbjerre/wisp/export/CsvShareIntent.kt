package com.github.tomasbjerre.wisp.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * Writes the session summaries and track points to two cache files and wraps them in one
 * share-sheet intent — see specs/export.md#trigger: the user picks where the files go,
 * Wisp doesn't write to a fixed location.
 */
object CsvShareIntent {
    private const val SESSIONS_FILE_NAME = "wisp-history.csv"
    private const val TRACK_POINTS_FILE_NAME = "wisp-track-points.csv"

    fun build(
        context: Context,
        sessionsCsv: String,
        trackPointsCsv: String,
        chooserTitle: String = "Export history as CSV",
        sessionsFileName: String = SESSIONS_FILE_NAME,
        trackPointsFileName: String = TRACK_POINTS_FILE_NAME,
    ): Intent {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val sessionsUri = writeAndShareableUri(context, exportsDir, sessionsFileName, sessionsCsv)
        val trackPointsUri = writeAndShareableUri(context, exportsDir, trackPointsFileName, trackPointsCsv)

        val sendIntent =
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "text/csv"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(sessionsUri, trackPointsUri))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        return Intent.createChooser(sendIntent, chooserTitle)
    }

    private fun writeAndShareableUri(
        context: Context,
        dir: File,
        fileName: String,
        content: String,
    ): Uri {
        val file = File(dir, fileName)
        file.writeText(content)
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}
