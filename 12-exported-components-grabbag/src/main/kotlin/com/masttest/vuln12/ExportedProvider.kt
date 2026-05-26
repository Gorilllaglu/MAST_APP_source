package com.masttest.vuln12

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log

/**
 * Content Provider, экспортированный без read/write permission.
 * Любое стороннее приложение может вызвать query/insert/update/delete.
 *
 * Здесь намеренно нет проверок calling UID/permission, нет path-permission,
 * вход parameters прокидывается в селекторы как есть. Это типичный
 * «небезопасный CP» — ground-truth уязвимость для семейства правил
 * вокруг exported provider'ов.
 */
class ExportedProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        Log.w("ExportedProvider", "query selection=$selection args=${selectionArgs?.toList()}")
        // Возвращаем фиктивный курсор. В реальном приложении тут был бы
        // запрос к SQLite с теми же неотфильтрованными `selection`/`selectionArgs`.
        val c = MatrixCursor(arrayOf("id", "secret"))
        c.addRow(arrayOf(1, "hunter2"))
        return c
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.masttest.vuln12.row"

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        Log.w("ExportedProvider", "insert values=$values")
        return Uri.withAppendedPath(uri, "1")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        Log.w("ExportedProvider", "delete selection=$selection")
        return 1
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        Log.w("ExportedProvider", "update values=$values selection=$selection")
        return 1
    }
}
