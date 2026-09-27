package com.github.tomasbjerre.wisp.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Writes exported CSV content directly into a folder the user picked via Android's
 * Storage Access Framework ([android.content.Intent.ACTION_OPEN_DOCUMENT_TREE]) — a
 * genuine on-device save, distinct from handing the files to another app via the share
 * sheet (see [CsvShareIntent]). See specs/export.md#trigger and issue #141: sharing to a
 * cloud app and downloading from there again was previously the only way to end up with
 * a local copy.
 */
object CsvDeviceWriter {
    fun write(
        context: Context,
        treeUri: Uri,
        fileNames: ExportFileNames.CsvPair,
        sessionsCsv: String,
        trackPointsCsv: String,
    ) {
        val dir = DocumentFile.fromTreeUri(context, treeUri) ?: return
        writeFile(context, dir, fileNames.sessions, sessionsCsv)
        writeFile(context, dir, fileNames.trackPoints, trackPointsCsv)
    }

    private fun writeFile(
        context: Context,
        dir: DocumentFile,
        name: String,
        content: String,
    ) {
        val file = dir.createFile("text/csv", name) ?: return
        context.contentResolver.openOutputStream(file.uri)?.use { it.write(content.toByteArray()) }
    }
}
