# 28 — Доступ к произвольному файлу через Uri

## APK после сборки

```bash
./gradlew :micro:28-arbitrary-file-via-uri:assembleDebug
```

Файл: `micro/28-arbitrary-file-via-uri/build/outputs/apk/debug/28-arbitrary-file-via-uri-debug.apk`

## Что заложено в приложение

`FileLoaderActivity` объявлена `exported="true"`. В её `onCreate`
два источника пути и две раскладки sink'ов:

1. **`Uri` через `getParcelableExtra("file_uri")`** напрямую попадает
   в `contentResolver.openInputStream(uri)` без проверки scheme,
   authority и path. Атака:
   ```
   content://com.masttest.vuln27.unsafe/../shared_prefs/secrets.xml
   ```
   `ContentResolver` сходит к нашему же UnsafeProvider'у (или к любому
   другому CP, на который уцепится authority) и прочтёт приватный
   файл. Также возможен `file://` с traversal'ом, или
   `android.resource://com.target.app/raw/secret`.
2. **`String path` через `getStringExtra("path")`** напрямую
   передаётся в `FileInputStream(path)` — это вторая дорожка к чтению
   произвольного файла, аналогично app `15`, но через native FS-путь.

Расположение: `src/main/kotlin/com/masttest/vuln28/FileLoaderActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **два** finding'а:

1. `intent.getParcelableExtra<Uri>("file_uri")` → `contentResolver.openInputStream(uri)` —
   доступ к произвольному файлу через ContentResolver / content://-uri
   без валидации. Категория R103, R105, R107.
2. `intent.getStringExtra("path")` → `FileInputStream(path)` — прямой
   File path traversal через FileInputStream без canonicalize.
   Категория R106, R149.

Сильный сканер строит data-flow от `intent.get*Extra` до File/Uri sink
и видит **отсутствие** `URI.scheme.equals("https")`-фильтра или
canonical-path проверки между ними.

**Чего сканер не должен делать**:

- Не должен флажить `contentResolver.openInputStream` сам по себе —
  это легитимный API, уязвимость в taint-источнике.
- Не должен флажить `MainActivity` (лаунчер).
