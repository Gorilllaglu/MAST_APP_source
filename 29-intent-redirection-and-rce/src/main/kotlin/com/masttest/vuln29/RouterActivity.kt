package com.masttest.vuln29

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-29: «Универсальный диспетчер» — Activity, которая принимает
 * подсказку, что запустить, и тупо запускает. В одном файле — три
 * разных класса уязвимостей.
 */
class RouterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // (1) intent:// scheme injection через Intent.parseUri.
        //     Атакующее приложение передаёт `intent_uri` —
        //     произвольную intent:// строку — мы её парсим и запускаем.
        val rawIntentUri = intent.getStringExtra("intent_uri")
        if (rawIntentUri != null) {
            val parsed = Intent.parseUri(rawIntentUri, Intent.URI_INTENT_SCHEME) // <-- VULN
            startActivity(parsed)
        }

        // (2) Intent redirection: входящий Intent несёт в Extras другой
        //     Intent, который мы slепо запускаем через startActivity.
        //     Любой компонент в системе (включая приватные нашего же
        //     приложения) может быть так запущен с правами нашей activity.
        @Suppress("DEPRECATION")
        val nested: Intent? = intent.getParcelableExtra("forward_intent")  // <-- VULN
        if (nested != null) {
            startActivity(nested)
        }

        // (3) ClassLoader RCE: получаем имя класса от вызывающего
        //     и инстанциируем его через reflection. Атакующий в своём
        //     приложении может объявить класс с осмысленной логикой
        //     (например, в его dex'е), но если у нас есть доступ к
        //     SDK-классам c сильными side-эффектами, можно сделать
        //     gadget-чейн.
        val className = intent.getStringExtra("class_name")
        if (className != null) {
            try {
                val cls = Class.forName(className)                          // <-- VULN
                val instance = cls.getDeclaredConstructor().newInstance()
                Log.w("Router", "Loaded class $className -> $instance")
            } catch (t: Throwable) {
                Log.e("Router", "load failed: $t")
            }
        }
    }
}
