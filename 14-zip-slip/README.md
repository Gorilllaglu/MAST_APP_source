# 14 — Zip-Slip при распаковке архива

## APK после сборки

```bash
./gradlew :micro:14-zip-slip:assembleDebug
```

Файл: `micro/14-zip-slip/build/outputs/apk/debug/14-zip-slip-debug.apk`

## Что заложено в приложение

Класс `ZipUtil.unzip(zipFile, targetDir)` распаковывает zip-архив в
указанную директорию через `ZipInputStream`, при этом для каждого
entry он буквально делает:

```kotlin
val outFile = File(targetDir, entry.name)
FileOutputStream(outFile).use { ... }
```

Имя записи (`entry.name`) берётся из самого zip-файла. Если это
`../../../shared_prefs/secrets.xml`, итоговый `File` будет
указывать **за пределы** `targetDir` — внутрь приватной директории
приложения. То есть архив, специально сформированный злоумышленником,
может перезаписать SharedPreferences, базы данных и любые другие
приватные файлы.

Это уязвимость класса **Zip-Slip** (CVE-2018-1002200, а также целое
семейство CVE в Java/Kotlin приложениях). Безопасный вариант
требует, чтобы `outFile.canonicalPath` начинался с
`targetDir.canonicalPath + File.separator` — этой проверки в коде нет.

`MainActivity` для полноты картины передаёт в `ZipUtil.unzip` файл из
`cacheDir` (типичный сценарий: приложение скачало архив и распаковывает
его). Сам файл `downloaded.zip` создаваться при запуске не будет
(нечего распаковывать) — это нормально, статический сканер должен
ловить уязвимость по сигнатуре кода `unzip`, а не по runtime поведению.

Расположение: `src/main/kotlin/com/masttest/vuln14/ZipUtil.kt`,
функция `unzip()`.

## Что должен найти сканер

Минимально достаточный отчёт — **один** finding на функцию `ZipUtil.unzip()`:

- В функции выполняется `File(targetDir, entry.name)` где `entry.name`
  взят из zip-архива и не проверен на отсутствие `..` / абсолютный путь.
  Категория — Zip-Slip / Path Traversal при распаковке (R078).

Хороший сканер обращает внимание на сочетание сигналов:

- `ZipInputStream` или `ZipFile.entries()` (источник),
- `File(parent, entry.name)` или `Paths.get(parent, entry.name)` (sink),
- отсутствие `canonicalPath`/`startsWith`/whitelist между ними.

**Чего сканер не должен делать**:

- Не должен флажить сам факт использования `ZipInputStream` — это
  легитимный API. Уязвимость только в комбинации с unsanitized entry name.
- Не должен флажить `MainActivity` (лаунчер).
