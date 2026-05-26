package com.masttest.vuln39

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore

/**
 * VULN-39: запись/чтение файлового keystore'а в writable-локацию
 * со слабыми паролями. Покрывает семейство R017 / R033 / R034 / R035 / R036.
 *
 * Это **только статика**: на устройстве многие операции упадут
 * (BKS-провайдер может отсутствовать, permission не выдан, и т.д.),
 * но в DEX call-site остаётся, и именно его ловит сканер.
 *
 * Сигнатуры, которые должен увидеть статический сканер:
 *   - `KeyStore.getInstance("BKS"|"PKCS12")` (выбор файлового формата);
 *   - `KeyStore.store(java.io.OutputStream, char[])` (намерение записать);
 *   - литеральные пароли (`android`, `1234`, `password`, `changeit`, `letmein`);
 *   - target paths в writable-локациях:
 *       `Context.getFilesDir()` / `getCacheDir()`,
 *       `Environment.getExternalStorageDirectory()`,
 *       `openFileOutput(..., MODE_WORLD_WRITEABLE)`.
 */
object KeystoreFileOps {

    private const val TAG = "Vuln39"

    // Hardcoded слабые пароли — словарные / короткие / default.
    // Все объявлены `const val` → попадают в DEX как литералы.
    private const val WEAK_PWD_ANDROID: String  = "android"
    private const val WEAK_PWD_NUMERIC: String  = "1234"
    private const val WEAK_PWD_DEFAULT: String  = "password"
    private const val WEAK_PWD_CHANGEIT: String = "changeit"
    private const val WEAK_PWD_LETMEIN: String  = "letmein"

    fun runAll(ctx: Context) {
        writeBksToFilesDir(ctx)
        writePkcs12ToExternal(ctx)
        writeBksWorldWriteable(ctx)
        readBksWithSamePassword(ctx)
        roundtripDifferentWeakPwds(ctx)
    }

    /**
     * R017 / R033 — запись BKS в **приватный** filesDir со слабым паролем.
     * Файл доступен на запись самому приложению. Атакующий с локальным
     * access (rooted device, backup) сможет подменить файл, а пароль
     * `android` тривиально подбирается.
     */
    fun writeBksToFilesDir(ctx: Context) = runCatching {
        val ks = KeyStore.getInstance("BKS")
        ks.load(null, null)
        val target = File(ctx.filesDir, "local.bks")
        FileOutputStream(target).use { out ->
            // <-- VULN sink: writable target + weak password literal
            ks.store(out, WEAK_PWD_ANDROID.toCharArray())
        }
        Log.d(TAG, "wrote BKS to ${target.absolutePath}")
    }

    /**
     * R033 «в худшем виде»: запись PKCS#12 в **публичную** external
     * storage директорию + 4-значный numeric password. Любой app с
     * `READ_EXTERNAL_STORAGE` (то есть практически любой) скачивает
     * файл, и `1234` подбирается за миллисекунды.
     */
    @Suppress("DEPRECATION")
    fun writePkcs12ToExternal(@Suppress("UNUSED_PARAMETER") ctx: Context) = runCatching {
        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, null)
        val target = File(Environment.getExternalStorageDirectory(), "backup.p12")
        FileOutputStream(target).use { out ->
            // <-- VULN sink: writable + public + 4-digit numeric password
            ks.store(out, WEAK_PWD_NUMERIC.toCharArray())
        }
        Log.d(TAG, "wrote PKCS12 to ${target.absolutePath}")
    }

    /**
     * R033 двойная ошибка: legacy `MODE_WORLD_WRITEABLE` (deprecated
     * с Android 7, но компилируется) + слабый пароль `password`. Это
     * означает, что любой app на устройстве может перезаписать наш
     * keystore.
     */
    fun writeBksWorldWriteable(ctx: Context) = runCatching {
        val ks = KeyStore.getInstance("BKS")
        ks.load(null, null)
        @Suppress("DEPRECATION")
        ctx.openFileOutput("shared.bks", Context.MODE_WORLD_WRITEABLE).use { out ->
            // <-- VULN sink: MODE_WORLD_WRITEABLE + weak password
            ks.store(out, WEAK_PWD_DEFAULT.toCharArray())
        }
        Log.d(TAG, "wrote BKS world-writeable")
    }

    /**
     * R035 / R036 — зеркальный сценарий: чтение того же файла с тем же
     * слабым паролем. Сканер видит «keystore + weak-password literal»
     * в обоих направлениях data-flow.
     */
    fun readBksWithSamePassword(ctx: Context) = runCatching {
        val ks = KeyStore.getInstance("BKS")
        val target = File(ctx.filesDir, "local.bks")
        FileInputStream(target).use { input ->
            // <-- VULN sink: keystore.load с тем же слабым паролем
            ks.load(input, WEAK_PWD_ANDROID.toCharArray())
        }
        Log.d(TAG, "loaded BKS aliases=${ks.size()}")
    }

    /**
     * Grab-bag: ещё два словарных пароля (`changeit`, `letmein`)
     * для проверки, что сканер сверяет с **широким** словарём,
     * а не только с парой захардкоженных known-weak.
     */
    fun roundtripDifferentWeakPwds(ctx: Context) = runCatching {
        val ks1 = KeyStore.getInstance("BKS")
        ks1.load(null, null)
        FileOutputStream(File(ctx.cacheDir, "ks_changeit.bks")).use { out ->
            ks1.store(out, WEAK_PWD_CHANGEIT.toCharArray())
        }
        val ks2 = KeyStore.getInstance("BKS")
        ks2.load(null, null)
        FileOutputStream(File(ctx.cacheDir, "ks_letmein.bks")).use { out ->
            ks2.store(out, WEAK_PWD_LETMEIN.toCharArray())
        }
        Log.d(TAG, "wrote two BKS with dictionary passwords")
    }
}
