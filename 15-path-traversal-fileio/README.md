# 15 — Path Traversal в файловом I/O

## APK после сборки

```bash
./gradlew :micro:15-path-traversal-fileio:assembleDebug
```

Файл: `micro/15-path-traversal-fileio/build/outputs/apk/debug/15-path-traversal-fileio-debug.apk`

## Что заложено в приложение

В манифесте есть `FileReadActivity` с `exported="true"`, без
permission. При получении Intent она читает строку из
`intent.getStringExtra("filename")` и собирает путь так:

```kotlin
val base = File(filesDir, "user_files")
val target = File(base, name)        // name берётся из Intent как есть
if (target.exists()) target.readText()
```

Идея «безопасности» здесь — что чтение ограничено каталогом
`filesDir/user_files`. Реализация неправильная: `name` может
содержать `../`, и тогда `target` уйдёт куда угодно в пределах
приватной директории приложения, например в `shared_prefs/`.

Поскольку Activity экспортирована и не требует permission, любое
приложение на устройстве может вызвать её через явный
`ComponentName`-Intent с `putExtra("filename", "../shared_prefs/secrets.xml")`
и заставить наш код прочитать чужой приватный файл. Прочитанный
текст потом выводится в `Log.w` и в `TextView` — то есть утечёт.

Безопасный паттерн (НЕ реализован):

```kotlin
val base = File(filesDir, "user_files").canonicalFile
val target = File(base, name).canonicalFile
require(target.path.startsWith(base.path + File.separator))
```

Расположение: `src/main/kotlin/com/masttest/vuln15/FileReadActivity.kt`,
функция `onCreate`.

## Что должен найти сканер

Минимально достаточный отчёт — **один** finding на конструкцию
`File(base, name)` где `name` пришёл из Intent extras без проверки:

- В `FileReadActivity.onCreate` строка `intent.getStringExtra("filename")`
  напрямую попадает в `File(base, name)` без нормализации
  (`canonicalPath`/`canonicalFile`) и без whitelist'а. Категория —
  Path Traversal (R149, CWE-22).

Хороший сканер обращает внимание на сочетание сигналов:

- источник — `intent.get*Extra`, `bundle.getString`, `Uri.parse(...).path`;
- sink — `File(...)`, `Paths.get(...)`, `FileInputStream(String)`,
  `FileOutputStream(String)`, `RandomAccessFile`, `FileReader`;
- между ними нет ни `canonicalPath.startsWith(base)`, ни whitelist'а.

**Чего сканер не должен делать**:

- Флажить `MainActivity` (это лаунчер).
- Флажить сам `File(filesDir, "user_files")` без traversal-входа —
  это легитимная конструкция базового пути.
