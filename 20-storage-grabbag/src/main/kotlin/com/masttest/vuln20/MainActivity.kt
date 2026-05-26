package com.masttest.vuln20

import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.os.Bundle
import android.os.Environment
import android.webkit.CookieManager
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) SQLite без шифрования с password в открытом виде.
        SecretsDbHelper(this).insertCredentials("alice", "hunter2")

        // (2) Файл с MODE_WORLD_READABLE (флаг устарел, но статически
        //     обнаруживается).
        @Suppress("DEPRECATION")
        openFileOutput("creds.txt", Context.MODE_WORLD_READABLE).use {
            it.write("password=hunter2\nauth_token=eyJhbGc...".toByteArray())
        }

        // (3) WebView CookieManager — Cookie уезжает в стандартный
        //     Cookies.db без шифрования.
        CookieManager.getInstance().setCookie(
            "https://api.example.com",
            "auth=eyJhbGciOiJIUzI1NiJ9.fake.fake; Path=/; Secure"
        )

        // (4) External storage — пишем секретный токен туда, где его
        //     прочитает любое приложение с READ_EXTERNAL_STORAGE.
        @Suppress("DEPRECATION")
        val ext = Environment.getExternalStorageDirectory()
        File(ext, "MAST_APP_BACKUP/secret_token.txt")
            .also { it.parentFile?.mkdirs() }
            .writeText("eyJhbGciOiJIUzI1NiJ9.exfil-target.fake")
    }
}

/**
 * SQLite-хранилище без шифрования. Никакого SQLCipher / EncryptedDatabase —
 * password лежит в /data/data/<pkg>/databases/secrets.db в открытом виде.
 */
private class SecretsDbHelper(ctx: Context) : SQLiteOpenHelper(ctx, "secrets.db", null, 1) {
    override fun onCreate(db: android.database.sqlite.SQLiteDatabase) {
        db.execSQL("CREATE TABLE users (id INTEGER PRIMARY KEY, login TEXT, password TEXT)")
    }

    override fun onUpgrade(db: android.database.sqlite.SQLiteDatabase, old: Int, new: Int) {}

    fun insertCredentials(login: String, password: String) {
        // INSERT с password в plaintext — VULN sink
        writableDatabase.execSQL(
            "INSERT INTO users (login, password) VALUES (?, ?)",
            arrayOf(login, password)
        )
    }
}
