# 22 — Утечка PII через IPC (implicit Intent + setResult)

## APK после сборки

```bash
./gradlew :micro:22-ipc-pii-leak-grabbag:assembleDebug
```

Файл: `micro/22-ipc-pii-leak-grabbag/build/outputs/apk/debug/22-ipc-pii-leak-grabbag-debug.apk`

## Что заложено в приложение

В одном app четыре типичных IPC-канала, через которые приложение
случайно отправляет чужим приложениям персональные данные.

В `MainActivity.onCreate`:

1. **Implicit Intent для Activity (ACTION_SEND)** с `EXTRA_TEXT`,
   содержащим `user=$username password=$password token=$authToken`.
   `setPackage` не вызвано. Любое приложение, у которого есть
   intent-filter на `ACTION_SEND` + `text/plain`, увидит и сможет
   принять это намерение.
2. **Implicit Intent для Service** с custom action
   `com.example.ACTION_PROCESS_TEXT` и `putExtra("password", ...)`.
   Без `setPackage` любой Service, заявивший такой action, получит
   пароль.
3. **Implicit Broadcast** с custom action и `auth_token`/`user`
   в extras. Любой `<receiver>` с этим action в системе получит
   токен.

В `LoginResultActivity`:

4. **`setResult(RESULT_OK, intent)`** где `intent` содержит
   `auth_token` и `user_id`. Если эту Activity запустят через
   `startActivityForResult`, вызывающее приложение (а Activity
   `exported="true"` — значит, любое стороннее приложение) получит
   токен и идентификатор пользователя обратно как Intent extras.

Категории — R079, R081, R082, R084, R086, R110, R114.

Расположение:

- `src/main/kotlin/com/masttest/vuln22/MainActivity.kt` — пп. 1-3
- `src/main/kotlin/com/masttest/vuln22/LoginResultActivity.kt` — п. 4

## Что должен найти сканер

Минимально достаточный отчёт — **четыре** finding'а:

1. `Intent(ACTION_SEND).putExtra(EXTRA_TEXT, ...password...)` без
   `setPackage` — implicit intent с PII. Категория R079/R110.
2. `Intent("com.example.ACTION_PROCESS_TEXT").putExtra("password", ...)`
   без `setPackage` — implicit intent с PII для Service. Категория R084.
3. `Intent("com.example.ACTION_USER_LOGIN").putExtra("auth_token", ...)`
   без `setPackage` — implicit broadcast с токеном. Категория R082.
4. `setResult(RESULT_OK, Intent().putExtra("auth_token", ...))` —
   утечка sensitive через результат Activity (R114).

Хороший сканер обращает внимание на сочетание сигналов:

- **источник** — переменная с подозрительным именем
  (`password|token|jwt|user_id|email|phone|cvv|card|auth_*`),
- **sink** — `Intent.putExtra(...)` или `bundle.putString(...)` или
  `setResult(*, intent)`,
- **отсутствие защитной обвязки** — `setPackage(...)` для implicit
  Intent / `intent-filter` с permission на target component.

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен флажить `LoginResultActivity` сам по себе из-за
  `exported="true"` без других сигналов — exported activity без
  `intent.get*Extra` ловится отдельным правилом из app `09`.
