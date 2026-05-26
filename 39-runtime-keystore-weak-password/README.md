# 39 — Запись/чтение файлового keystore'а в writable-локацию со слабым паролем

## APK после сборки

```bash
./gradlew :micro:39-runtime-keystore-weak-password:assembleDebug
```

Файл: `micro/39-runtime-keystore-weak-password/build/outputs/apk/debug/39-runtime-keystore-weak-password-debug.apk`

## Что заложено в приложение

В отличие от apps 16 и 37, где `client.bks` / `client_pkcs12.p12`
**уже лежат** внутри APK как read-only ресурсы (R024 / R028), здесь
приложение **само создаёт и читает** файловые keystore'ы в writable-локациях,
и каждый раз — со слабым словарным паролем.

Это и есть семейство **R017 / R033 / R034 / R035 / R036** —
«доступное на запись хранилище ключей со слабым паролем».

Пять методов в `KeystoreFileOps.kt`, каждый из MainActivity через
`KeystoreFileOps.runAll(ctx)`:

| Метод | Target path | Пароль | Категория |
|---|---|---|---|
| `writeBksToFilesDir` | `filesDir/local.bks` (приватный, но writable приложением) | `"android"` | **R017 / R033** |
| `writePkcs12ToExternal` | `Environment.getExternalStorageDirectory()/backup.p12` (публичная директория) | `"1234"` | **R033** в худшем виде — публично + 4-значный |
| `writeBksWorldWriteable` | `openFileOutput("shared.bks", MODE_WORLD_WRITEABLE)` (любой app может перезаписать) | `"password"` | **R033** через legacy MODE_WORLD_WRITEABLE |
| `readBksWithSamePassword` | чтение того же `filesDir/local.bks` | `"android"` | **R035 / R036** — зеркальный сценарий (читаемое со слабым паролем) |
| `roundtripDifferentWeakPwds` | два keystore'а в `cacheDir/` | `"changeit"`, `"letmein"` | grab-bag — проверка словаря сканера |

Все пять паролей — отдельные `const val` в DEX:

```kotlin
private const val WEAK_PWD_ANDROID: String  = "android"
private const val WEAK_PWD_NUMERIC: String  = "1234"
private const val WEAK_PWD_DEFAULT: String  = "password"
private const val WEAK_PWD_CHANGEIT: String = "changeit"
private const val WEAK_PWD_LETMEIN: String  = "letmein"
```

Сигнатуры в DEX, которые сканер должен видеть:

- `java.security.KeyStore.getInstance("BKS")` / `("PKCS12")`
- `java.security.KeyStore.store(java.io.OutputStream, char[])` — намерение записать
- `java.security.KeyStore.load(java.io.InputStream, char[])` — намерение прочитать
- `android.content.Context.openFileOutput("...", MODE_WORLD_WRITEABLE)`
- `android.os.Environment.getExternalStorageDirectory()`
- 5 hardcoded паролей-литералов

Все вызовы обёрнуты в `runCatching`. На реальном устройстве
- BKS-провайдер может отсутствовать (на современных Android Bouncy
  Castle BKS убран начиная с Android 9),
- `WRITE_EXTERNAL_STORAGE` без runtime-grant'а упадёт,
- `MODE_WORLD_WRITEABLE` бросит `SecurityException` с API 24+.

Но в DEX все call-site сохраняются — статическому сканеру этого хватает.

Расположение: `src/main/kotlin/com/masttest/vuln39/KeystoreFileOps.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **пять-семь** finding'ов:

1. `writeBksToFilesDir` — `keystore.store` в writable filesDir + пароль
   `"android"`. Категория **R017 / R033**.
2. `writePkcs12ToExternal` — `keystore.store` в external storage + пароль
   `"1234"`. Категория **R033** (усиленная — публичная директория).
3. `writeBksWorldWriteable` — `keystore.store` + `MODE_WORLD_WRITEABLE`
   + пароль `"password"`. Категория **R033** (двойная — legacy mode).
4. `readBksWithSamePassword` — `keystore.load` с тем же слабым
   паролем. Категория **R035 / R036**.
5. `roundtripDifferentWeakPwds` — два keystore'а с паролями `"changeit"`
   и `"letmein"`. Категории **R017 / R033** (по два пункта на каждый).

Сильный сканер дополнительно:

- сравнит пароли с **широким** словарём слабых паролей (не только
  «changeit» / «password» / «android», но и `letmein` / `1234` / `qwerty`);
- учтёт **длину** пароля (4 символа — заведомо weak вне зависимости
  от словаря);
- проследит **data-flow** между `keystore.store` и `keystore.load` —
  если оба используют один и тот же литерал, это особо подозрительно
  (защита только видимостью).

## Чего сканер НЕ должен делать

- Не должен флажить shipped-keystore из app 16 / app 37 как R033 —
  у них файлы read-only внутри APK, target не writable.
- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на сам факт `KeyStore.getInstance("BKS")` без
  слабых паролей — это легитимный API, ошибка только в комбинации.
