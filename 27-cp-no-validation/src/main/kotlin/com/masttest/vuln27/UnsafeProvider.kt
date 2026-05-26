package com.masttest.vuln27

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File

/**
 * VULN-27: ContentProvider, экспортированный без permission и без
 * валидации входных параметров. В одном CP — несколько разных
 * sink'ов, по которым у сканера должны срабатывать отдельные правила.
 */
class UnsafeProvider : ContentProvider() {

    private lateinit var dbHelper: DbHelper

    override fun onCreate(): Boolean {
        dbHelper = DbHelper(context!!)
        return true
    }

    override fun getType(uri: Uri): String = "vnd.android.cursor.dir/vnd.masttest.vuln27.row"

    /**
     * (1) SQL injection через selection: вызывающий передаёт raw SQL,
     *     CP конкатенирует его в WHERE без parameterization.
     *     query("SELECT ... WHERE $selection")
     */
    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val rawSql = "SELECT id, login, password FROM users " +
            (if (!selection.isNullOrBlank()) "WHERE $selection " else "") +    // <-- VULN: SQLi
            (if (!sortOrder.isNullOrBlank()) "ORDER BY $sortOrder" else "")
        return dbHelper.readableDatabase.rawQuery(rawSql, selectionArgs)
    }

    /**
     * (2) Insert без проверки прав вызывающего и без проверки колонок.
     *     Любой может писать что угодно.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri {
        // <-- VULN sink: незащищённая запись
        val rowId = dbHelper.writableDatabase.insert("users", null, values)
        return Uri.withAppendedPath(uri, rowId.toString())
    }

    /**
     * (3) Update тоже передаёт selection как есть (SQLi).
     */
    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        // <-- VULN sink: SQLi через selection + insecure update
        return dbHelper.writableDatabase.update("users", values, selection, selectionArgs)
    }

    /**
     * (4) Delete c selection без parameterization.
     */
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        // <-- VULN sink: SQLi через selection + любой может удалить
        return dbHelper.writableDatabase.delete("users", selection, selectionArgs)
    }

    /**
     * (5) openFile отдаёт файл по path-сегменту URI без canonicalize.
     *     Атака: content://com.masttest.vuln27.unsafe/../shared_prefs/secrets.xml
     */
    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val name = uri.lastPathSegment ?: return null
        val target = File(context!!.filesDir, name)               // <-- VULN: path traversal
        return ParcelFileDescriptor.open(
            target,
            ParcelFileDescriptor.MODE_READ_ONLY
        )
    }

    private class DbHelper(ctx: android.content.Context) : SQLiteOpenHelper(ctx, "vuln27.db", null, 1) {
        override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
            db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, login TEXT, password TEXT)")
            db.execSQL("INSERT INTO users (login, password) VALUES ('alice', 'hunter2')")
        }
        override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, old: Int, new: Int) {}
    }
}
