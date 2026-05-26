package com.masttest.vuln14

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/**
 * Распаковка zip-архива без проверки имён entries.
 *
 * Уязвимость:
 *   Имена записей в zip-архиве могут содержать '../' и приводить к
 *   записи файла за пределами целевой директории (Zip-Slip, CVE-2018-1002200).
 *   Здесь мы делаем буквально File(targetDir, entry.name) и пишем туда —
 *   без вызова `canonicalPath` и проверки, что результирующий путь
 *   начинается с canonical path целевой директории.
 *
 * Безопасный вариант (для контраста, не используется тут):
 *
 *   val outFile = File(targetDir, entry.name)
 *   val canonicalDest = targetDir.canonicalPath + File.separator
 *   if (!outFile.canonicalPath.startsWith(canonicalDest)) {
 *       throw SecurityException("zip-slip blocked: ${entry.name}")
 *   }
 */
object ZipUtil {
    fun unzip(zipFile: File, targetDir: File) {
        if (!targetDir.exists()) targetDir.mkdirs()
        ZipInputStream(zipFile.inputStream()).use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                val outFile = File(targetDir, entry.name)            // <-- VULN sink
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    FileOutputStream(outFile).use { fos ->
                        zin.copyTo(fos)
                    }
                }
                entry = zin.nextEntry
            }
        }
    }
}
