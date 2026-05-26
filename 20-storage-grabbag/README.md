# 20 — Сборная солянка небезопасного хранилища

## APK после сборки

```bash
./gradlew :micro:20-storage-grabbag:assembleDebug
```

Файл: `micro/20-storage-grabbag/build/outputs/apk/debug/20-storage-grabbag-debug.apk`

## Что заложено в приложение

В `MainActivity.onCreate` подряд используется **четыре** разных
storage-backend'а, и в каждый кладётся секрет в открытом виде:

1. **SQLite без шифрования.** `SecretsDbHelper` создаёт базу
   `secrets.db` с таблицей `users(login, password)` и вставляет
   `INSERT INTO users (login, password) VALUES ("alice", "hunter2")`.
   База лежит в `/data/data/com.masttest.vuln20/databases/secrets.db`,
   `password` — в открытом виде. Никакого SQLCipher / EncryptedDatabase
   нет.
2. **Файл с MODE_WORLD_READABLE.** `openFileOutput("creds.txt",
   Context.MODE_WORLD_READABLE)` — режим устарел в Android 7,
   но сама строка кода / сигнатура остаётся в bytecode и легко
   ловится grep'ом / static-сканером. Туда пишутся `password=hunter2`
   и `auth_token=...`.
3. **WebView CookieManager.** `CookieManager.getInstance().setCookie(
   "https://api.example.com", "auth=eyJ...")` — кука с токеном
   уходит в стандартный `Cookies.db` WebView без шифрования.
4. **External storage.** Файл `MAST_APP_BACKUP/secret_token.txt`
   пишется в `Environment.getExternalStorageDirectory()` (deprecated,
   но всё ещё работает на legacy). Содержимое прочитает любое
   приложение с `READ_EXTERNAL_STORAGE`.

Расположение всех слабостей: `src/main/kotlin/com/masttest/vuln20/MainActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **четыре** finding'а, по одному на
каждый storage-канал:

1. `SecretsDbHelper.insertCredentials` — вставка пароля в SQLite без
   шифрования. Категория R067.
2. `openFileOutput("creds.txt", MODE_WORLD_READABLE)` — небезопасный
   режим файла, плюс сами write'ы пароля и токена. Категория R061
   (общедоступный файл) и R139 (deprecated MODE_WORLD_READABLE).
3. `CookieManager.setCookie(...)` с токеном — категория R069
   (хранение cookies в стандартной WebView-базе).
4. Запись токена в `Environment.getExternalStorageDirectory()/...` —
   категория R072 (sensitive в общедоступном файле вне директории
   приложения).

Хороший сканер показывает все четыре отдельно: каждый storage-канал
лечится своим способом (Room+SQLCipher, EncryptedSharedPreferences,
изоляция WebView session, отказ от external для секретов).

**Чего сканер не должен делать**:

- Не должен флажить сам факт использования `SQLiteOpenHelper` —
  это стандартный API. Уязвимость — в комбинации с записью
  чувствительной колонки без шифрования.
- Не должен флажить `MainActivity` (лаунчер).
