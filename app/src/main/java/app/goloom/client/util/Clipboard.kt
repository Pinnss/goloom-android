package app.goloom.client.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object Clipboard {
    fun read(context: Context): String? {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null
        val item = cm.primaryClip?.getItemAt(0) ?: return null
        return item.text?.toString()?.takeIf { it.isNotBlank() }
    }

    fun write(context: Context, text: String, label: String = "Goloom") {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }
}
