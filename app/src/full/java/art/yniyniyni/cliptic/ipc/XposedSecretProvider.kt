package art.yniyniyni.cliptic.ipc

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.Process
import art.yniyniyni.cliptic.IpcActions
import art.yniyniyni.cliptic.settings.ClipticSettings
class XposedSecretProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    /**
     * Liveness channel for the embedded module. The SystemUI hook calls
     * [IpcActions.PROVIDER_METHOD_RECORD_ACTIVE] on startup; we record the timestamp so the app
     * can show an honest module-active state. UID-gated to the app/SystemUI like [query].
     */
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle? {
        val context = context ?: return null
        if (!isAuthorizedCaller()) return null
        if (method == IpcActions.PROVIDER_METHOD_RECORD_ACTIVE) {
            ClipticSettings.recordXposedActive(context)
        }
        return null
    }

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
            addRow(arrayOf(XposedSecret.get(context)))
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
