package app.goloom.client.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/**
 * Запускает системный package-installer на скачанный APK. Android
 * Package Installer покажет диалог "Install update? / Установить
 * обновление?" — пользователь подтверждает руками. Без подтверждения
 * установить нельзя без root, и это правильно.
 *
 * REQUEST_INSTALL_PACKAGES — runtime-permission на API 26+. На некоторых
 * устройствах при первом вызове Android попросит "Allow install from
 * this source"; после согласия диалог не повторяется.
 */
object Installer {
    fun installApk(context: Context, file: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }
        context.startActivity(intent)
    }
}
