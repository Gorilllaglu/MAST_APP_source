package com.masttest.vuln38

import android.app.KeyguardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File

/**
 * VULN-38: восемь намеренно ОЧЕНЬ слабых RASP-проверок.
 *
 * Отличие от app 32 (`no-protections-baseline`): здесь проверки
 * **есть** — сканер должен это заметить и НЕ репортить R118/R119
 * (отсутствие). Но реализованы они так, что обходятся за минуту —
 * сканер должен зарепортить R116/R117/R120/R121/R122/R123 (слабая
 * реализация).
 *
 * Все проверки сделаны статически-видимыми: имена методов, имена
 * полей Build, литералы путей — всё доступно grep'у по DEX.
 */
object WeakChecks {

    data class Report(
        val rootByOnePath: Boolean,
        val rootByTestKeys: Boolean,
        val emuByFingerprint: Boolean,
        val emuByHardware: Boolean,
        val debuggerByManifestFlag: Boolean,
        val integrityByHasSignature: Boolean,
        val fridaByOnePath: Boolean,
        val screenLockLegacy: Boolean,
    )

    fun runAll(ctx: Context): Report = Report(
        rootByOnePath          = weakRootSinglePath(),
        rootByTestKeys         = weakRootByTestKeys(),
        emuByFingerprint       = weakEmulatorFingerprintOnly(),
        emuByHardware          = weakEmulatorHardwareOnly(),
        debuggerByManifestFlag = weakDebuggerFlagOnly(ctx),
        integrityByHasSignature = weakIntegrityHasSignatureOnly(ctx),
        fridaByOnePath         = weakFridaSinglePath(),
        screenLockLegacy      = weakScreenLockLegacy(ctx),
    )

    // ==================== R116 — Weak ROOT detection ====================

    /**
     * Проверяет ОДИН-единственный путь. Magisk не оставляет
     * `/system/app/Superuser.apk` — последние 6+ лет апдейт SuperSU
     * этот файл вообще не ставит. Обход: переименовать / не ставить
     * этот файл.
     *
     * Сильная реализация проверяла бы 15-30 путей + `which su` +
     * mount -o rw / + SafetyNet attestation.
     */
    private fun weakRootSinglePath(): Boolean {
        return File("/system/app/Superuser.apk").exists()
    }

    /**
     * Common mistake: разработчик путает «root detection» с проверкой
     * фабричных ключей (`test-keys` означает userdebug/eng AOSP-сборку,
     * не root). Это вообще не про root.
     */
    private fun weakRootByTestKeys(): Boolean {
        return Build.TAGS?.contains("test-keys") == true
    }

    // ==================== R117 — Weak EMULATOR detection ====================

    /**
     * Проверяет ТОЛЬКО `Build.FINGERPRINT.contains("generic")`.
     * Обходится:
     *   - Genymotion с custom fingerprint
     *   - изменение `ro.build.fingerprint` через kernel-mod
     *   - реальные устройства с rebranded firmware могут содержать
     *     "generic" в составе → ложноположительные срабатывания
     *
     * Сильная реализация комбинирует 6-8 fields из `Build`
     * (FINGERPRINT, MODEL, MANUFACTURER, HARDWARE, PRODUCT, DEVICE,
     * BOOTLOADER, BRAND) + наличие специфических файлов / property.
     */
    private fun weakEmulatorFingerprintOnly(): Boolean {
        return Build.FINGERPRINT.contains("generic")
    }

    /**
     * Только AOSP-эмулятор. Bluestacks/MEmu/LDPlayer обходят легко —
     * у них `Build.HARDWARE` свой. Genymotion имеет `Build.HARDWARE`
     * вроде "vbox86" — тоже не "goldfish".
     */
    private fun weakEmulatorHardwareOnly(): Boolean {
        return Build.HARDWARE == "goldfish" || Build.HARDWARE == "ranchu"
    }

    // ==================== R122 — Weak DEBUGGER detection ====================

    /**
     * Проверяет ТОЛЬКО manifest-флаг `android:debuggable`.
     * Это совершенно не покрывает реальные сценарии:
     *   - Frida через `ptrace` присоединяется к процессу без флага в манифесте.
     *   - JDWP можно открыть через `adb shell am set-debug-app`.
     *
     * Сильная реализация дополнительно проверяет
     * `Debug.isDebuggerConnected()`, наличие `tracerpid` в
     * `/proc/self/status`, и tries to detect Frida-server.
     */
    private fun weakDebuggerFlagOnly(ctx: Context): Boolean {
        val flags = ctx.applicationInfo.flags
        // Намеренно НЕ вызываем Debug.isDebuggerConnected() —
        // это была бы дополнительная (сильная) проверка.
        @Suppress("UNUSED_VARIABLE")
        val _unused = Debug::class.java  // ссылка на класс, чтобы он попал в DEX
        return flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    // ==================== R123 — Weak INTEGRITY check ====================

    /**
     * «Проверка подписи» которая просто смотрит, что подпись вообще
     * присутствует. Любой re-signed APK (с любым подписавшим)
     * пройдёт эту проверку.
     *
     * Сильная реализация хэширует подпись (SHA-256) и сверяет с
     * захардкоженным known-good значением.
     */
    @Suppress("DEPRECATION", "PackageManagerGetSignatures")
    private fun weakIntegrityHasSignatureOnly(ctx: Context): Boolean {
        val pm = ctx.packageManager
        return try {
            val info = pm.getPackageInfo(ctx.packageName, PackageManager.GET_SIGNATURES)
            // Проверяем только что массив не пуст — подпись есть.
            // Никакой сверки SHA-256 с ожидаемым отпечатком нет.
            !info.signatures.isNullOrEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    // ==================== R120 — Weak FRIDA detection ====================

    /**
     * Проверка наличия ОДНОГО файла `frida-server` в стандартном
     * пути. Обходится переименованием бинаря или запуском
     * frida-gadget из памяти.
     *
     * Сильная реализация сканирует TCP-порты 27042/27047, ищет
     * `/proc/self/maps` на наличие `frida` или `gum-js-loop`,
     * проверяет /data/local/tmp/re.frida.server.
     */
    private fun weakFridaSinglePath(): Boolean {
        return File("/data/local/tmp/frida-server").exists()
    }

    // ==================== R121 — Weak SCREEN-LOCK check ====================

    /**
     * Использует deprecated `isKeyguardSecure()` (API 16+,
     * deprecated на API 23+). Правильный API — `isDeviceSecure()`
     * (API 23+), который точнее различает PIN/pattern/password
     * vs swipe-unlock.
     *
     * Помимо этого, ни тот, ни другой API не реагирует на
     * биометрию-как-единственный-фактор корректно — нужно
     * комбинировать с BiometricManager.canAuthenticate.
     */
    @Suppress("DEPRECATION")
    private fun weakScreenLockLegacy(ctx: Context): Boolean {
        val km = ctx.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return km.isKeyguardSecure  // вместо km.isDeviceSecure
    }
}
