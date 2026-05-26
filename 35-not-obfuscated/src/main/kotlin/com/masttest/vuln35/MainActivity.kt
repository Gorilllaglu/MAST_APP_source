package com.masttest.vuln35

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // Наглядно «секретный» класс, использующийся в onCreate чтобы R8
        // не выкидывал его. В реальной release-сборке должен быть
        // переименован в a/b/c — но здесь minification выключена,
        // поэтому имена сохраняются.
        AntiFraudInternal.checkRiskScoreAndDecideIfBlocked()
    }
}

/**
 * Класс, имя и логика которого в production должны быть скрыты
 * минификатором. Без обфускации:
 *  - имя класса видно в jadx как `AntiFraudInternal`,
 *  - имя метода как `checkRiskScoreAndDecideIfBlocked`,
 *  - все ветки `if` сохраняются (cleartext logic),
 *  - все «магические числа» и строки сохраняются.
 */
object AntiFraudInternal {
    private const val INTERNAL_FRAUD_THRESHOLD: Double = 0.85
    private const val ADMIN_OVERRIDE_HEADER: String = "X-Internal-Admin-Override"

    fun checkRiskScoreAndDecideIfBlocked(): Boolean {
        // Логика «определения мошенничества». В обычной release-сборке
        // R8 заinline'ил бы это и переименовал; здесь видно как есть.
        val score = computeRiskScore()
        return score > INTERNAL_FRAUD_THRESHOLD
    }

    private fun computeRiskScore(): Double = 0.42
}
