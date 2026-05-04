package app.goloom.client.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Утилиты для шеринга файлов и текста через системный share-sheet.
 * `.conf` экспорт и логи уходят как FileProvider URI с временным грантом.
 */
object Sharing {

    fun shareText(context: Context, text: String, mimeType: String = "text/plain") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    fun shareFile(context: Context, file: File, mimeType: String = "text/plain") {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Сохраняет текст в cache/exports/<filename> и возвращает File. */
    fun writeExport(context: Context, fileName: String, content: String): File {
        val dir = File(context.cacheDir, "exports").also { it.mkdirs() }
        val file = File(dir, fileName)
        file.writeText(content)
        return file
    }
}
