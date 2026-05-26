package com.masttest.vuln30

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-30 (часть 2): App Link с autoVerify="true", но БЕЗ
 * опубликованного /.well-known/assetlinks.json для домена
 * myapp.example.com (этого файла в проекте нет).
 *
 * Когда устройство пытается верифицировать App Link и не находит
 * assetlinks.json, autoVerify тихо отключается, и Android начинает
 * показывать chooser. Это превращает «безопасный» https-deeplink
 * в обычный browsable-link, который перехватывается любым приложением,
 * заявившим тот же intent-filter.
 *
 * Кодовая часть тривиальна — основная уязвимость в манифесте.
 */
class ProfileViaAppLinkActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
