package art.yniyniyni.cliptic.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Process
import art.yniyniyni.cliptic.settings.ClipticSettings

class XposedSecretProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        val context = context ?: return null
        if (!isAuthorizedCaller()) return null
        return MatrixCursor(arrayOf(COLUMN_SECRET)).apply {
            addRow(arrayOf(ClipticSettings.xposedSecret(context)))
        }
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.item/vnd.art.yniyniyni.cliptic.secret"

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int = 0

    private fun isAuthorizedCaller(): Boolean {
        val context = context ?: return false
        val callerUid = Binder.getCallingUid()
        if (callerUid == Process.myUid()) return true
        val packages = context.packageManager.getPackagesForUid(callerUid).orEmpty()
        return packages.contains(SYSTEMUI_PACKAGE)
    }

    private companion object {
        const val COLUMN_SECRET = "secret"
        const val SYSTEMUI_PACKAGE = "com.android.systemui"
    }
}
