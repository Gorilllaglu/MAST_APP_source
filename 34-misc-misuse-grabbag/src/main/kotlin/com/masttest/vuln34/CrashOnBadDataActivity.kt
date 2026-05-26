package com.masttest.vuln34

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-34 (часть 3): IPC DoS-crash.
 *
 * Activity exported=true, читает строку из Intent и приводит её
 * к Int БЕЗ try/catch. Любое стороннее приложение, отправив
 * `putExtra("amount", "not-a-number")` через явный ComponentName,
 * мгновенно валит наше приложение. Если этот же crash идёт в
 * приватной активности, доступной через deeplink — массовый DoS
 * пользовательских устройств.
 *
 * Категория R150.
 */
class CrashOnBadDataActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val amountStr = intent.getStringExtra("amount") ?: return
        // <-- VULN sink: NumberFormatException не пойман — приложение
        // падает на любом невалидном вводе.
        val amount: Int = amountStr.toInt()
        @Suppress("UNUSED_VARIABLE") val v = amount
    }
}
