package com.masttest.vuln32

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/**
 * VULN-32: «vanilla» приложение БЕЗ каких-либо защитных проверок.
 *
 * Этот app намеренно НЕ содержит:
 *   - root detection (RootBeer / SafetyNet / ручные проверки su, Magisk, Xposed)
 *   - emulator detection (Build.FINGERPRINT/Build.MODEL/Build.HARDWARE проверки)
 *   - debugger detection (Debug.isDebuggerConnected, applicationInfo.flags & FLAG_DEBUGGABLE)
 *   - Frida detection (поиск /data/local/tmp/frida, fridaserver, hooked classes)
 *   - app integrity check (PackageManager.getPackageInfo + GET_SIGNATURES + сверка)
 *   - screen-lock check (KeyguardManager.isDeviceSecure, isKeyguardSecure)
 *
 * Сканер должен сообщить, что эти защиты ОТСУТСТВУЮТ. Сама по себе
 * Activity тривиальна — проверки делаются по факту отсутствия в проекте
 * соответствующих API-вызовов / зависимостей / классов.
 */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
    }
}
