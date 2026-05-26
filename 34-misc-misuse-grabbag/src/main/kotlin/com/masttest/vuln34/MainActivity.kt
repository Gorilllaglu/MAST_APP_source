package com.masttest.vuln34

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) MODE_WORLD_WRITEABLE — намеренно небезопасный режим для
        //     SharedPreferences и файлов. Атрибут официально deprecated,
        //     но компилируется и попадает в bytecode. Категория R139.
        @Suppress("DEPRECATION")
        getSharedPreferences("public", Context.MODE_WORLD_WRITEABLE)        // <-- VULN
            .edit()
            .putString("key", "value")
            .apply()

        // (2) java.util.Random для генерации «секрета». Random — линейный
        //     конгруэнтный генератор, предсказуем после нескольких
        //     samples. Категория R128 / R139.
        val sessionToken = generateSessionTokenInsecurely()
        @Suppress("UNUSED_VARIABLE") val a = sessionToken
    }

    /**
     * Генератор «токена сессии» на основе java.util.Random.
     * После пары наблюдений можно предсказать следующие токены.
     */
    private fun generateSessionTokenInsecurely(): String {
        val r = Random()                                                   // <-- VULN: not SecureRandom
        val bytes = ByteArray(16)
        r.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
