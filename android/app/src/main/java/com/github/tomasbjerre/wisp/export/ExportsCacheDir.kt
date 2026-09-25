package com.github.tomasbjerre.wisp.export

import android.content.Context
import java.io.File

/**
 * The cache directory export files are written to before being handed to the share
 * sheet. Each export gets a distinct, timestamped name (see specs/export.md#file-names),
 * so earlier exports would otherwise pile up here forever instead of being overwritten —
 * once handed off, they're the receiving app's copy to keep, not Wisp's.
 */
internal object ExportsCacheDir {
    fun fresh(context: Context): File {
        val dir = File(context.cacheDir, "exports")
        dir.listFiles()?.forEach { it.delete() }
        return dir.apply { mkdirs() }
    }
}
