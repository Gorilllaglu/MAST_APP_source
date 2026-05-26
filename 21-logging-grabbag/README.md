# 21 — Утечки чувствительных данных через логи

## APK после сборки

```bash
./gradlew :micro:21-logging-grabbag:assembleDebug
```

Файл: `micro/21-logging-grabbag/build/outputs/apk/debug/21-logging-grabbag-debug.apk`

## Что заложено в приложение

В `MainActivity.onCreate` сразу несколько способов утечки секретов
через лог-каналы:

1. **`android.util.Log.*`** с чувствительными данными:
   - `Log.d("Auth", "user=$username pass=$password")` — пароль в логе.
   - `Log.w("Auth", "issued token=$authToken")` — Bearer/JWT токен.
   - `Log.e("Auth", "credit_card=4242 ... cvv=123")` — PII платёжной
     карты.
2. **`System.out` / `System.err`**:
   - `System.err.println("DEBUG token=$authToken")` — токен в stderr,
     который тоже попадает в logcat.
   - `println("user $username logged in with password $password")` —
     пароль в stdout.
3. **OkHttp `HttpLoggingInterceptor.Level.BODY`**. В коде создаётся
   `OkHttpClient.Builder().addInterceptor(httpLogger)` где
   `httpLogger.level = Level.BODY`. Это пишет в logcat **полное**
   тело каждого HTTP-запроса и ответа (включая JSON с password,
   `Authorization: Bearer ...`, и т.д.). Категория R066 (вывод
   sensitive в системный лог).

В реальных Android-приложениях все три типа утечек встречаются
постоянно — в vault'е есть много примеров (WB Drive
DebugNetworkInterceptor, Скан-приёмка HTTP logging, и пр.).

Расположение: `src/main/kotlin/com/masttest/vuln21/MainActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — несколько finding'ов из категории
«утечка sensitive data в лог» (R066):

1. Вызов `Log.d/Log.w/Log.e` с переменной, содержащей
   password / token / cvv (можно по эвристике: имена переменных,
   совпадающие с регулярками `password|passw|pwd|token|jwt|cvv|card`).
2. Вызов `System.err.println` / `println` с теми же переменными.
3. Установка `HttpLoggingInterceptor.level = Level.BODY` (или
   `setLevel(Level.BODY)`) — это критично, так как Body-level в
   release-сборке всегда LEAK.

Допустимо если сканер сворачивает все Log.*-вызовы в один общий
finding «logging of sensitive data» с перечислением мест. Но
`HttpLoggingInterceptor.Level.BODY` — это **отдельный** signature,
и его пропускать нельзя.

**Чего сканер не должен делать**:

- Не должен флажить `Log.*` вызовы без чувствительных переменных
  (например `Log.d("App", "started")`).
- Не должен флажить `HttpLoggingInterceptor` сам по себе — флажить
  нужно именно `Level.BODY` или `Level.HEADERS` (последний меньше
  утечки, но тоже вызывает вопросы).
