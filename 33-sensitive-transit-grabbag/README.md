# 33 — Чувствительные данные в транзите (URL / WebSocket)

## APK после сборки

```bash
./gradlew :micro:33-sensitive-transit-grabbag:assembleDebug
```

Файл: `micro/33-sensitive-transit-grabbag/build/outputs/apk/debug/33-sensitive-transit-grabbag-debug.apk`

## Что заложено в приложение

В `MainActivity.onCreate` три способа отправить чувствительные данные
по сети так, чтобы они попали в неожиданные логи и refer-цепочки.

1. **HTTPS GET с токеном в query-string.**
   ```
   https://api.example.com/profile?token=eyJ...&user=alice
   ```
   HTTPS защищает payload, но URL целиком попадает в access.log
   сервера, в логи прокси, в debug-tools браузера, и в `Referer`
   при последующих запросах. Категория R091.
2. **WebSocket-сообщение с JWT в payload.**
   ```json
   {"action":"auth","jwt":"eyJ...","user":"alice"}
   ```
   WebSocket-фреймы тоже шифруются в TLS, но серверы логируют их
   целиком (особенно при дебаге). Категория R093.
3. **HTTPS POST с password в query-параметре URL.**
   ```
   https://api.example.com/legacy_login?password=hunter2
   ```
   Даже когда тело пустое, password в query-string ведёт себя как
   п.1. Категория R087/R088 (sensitive в HTTP/HTTPS request).

Расположение: `src/main/kotlin/com/masttest/vuln33/MainActivity.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на
каждое отправление:

1. URL для `Request.Builder().url(...)` собран строкой и содержит
   подозрительные имена параметров (`token=`, `auth=`, `jwt=`,
   `password=`). Категория R091, R087.
2. `WebSocket.send(...)` с payload-строкой, содержащей sensitive
   ключи (`"jwt"`, `"token"`, `"password"`). Категория R093.
3. Отдельный finding на конкатенацию `password=...` в URL даже
   при POST — это специфический паттерн (часто пропускается
   автоматическими правилами). Категория R087.

Хороший сканер делает пасс по всем `Request.Builder().url(...)` /
`WebSocket.send(...)` / `URL(...)` и эвристически ищет внутри
строк подозрительные ключи. Допустимо если сворачивает три
finding'а в один общий «sensitive data in network parameters».

**Чего сканер не должен делать**:

- Не должен флажить сам факт `OkHttpClient` / `WebSocket`.
- Не должен флажить `MainActivity` (лаунчер).
