# 06 — Cleartext HTTP-трафик

## APK после сборки

```bash
./gradlew :micro:06-network-cleartext-http:assembleDebug
```

Файл: `micro/06-network-cleartext-http/build/outputs/apk/debug/06-network-cleartext-http-debug.apk`

## Что заложено в приложение

В приложении одновременно две связанных слабости:

1. В `AndroidManifest.xml` на теге `<application>` выставлен атрибут
   `android:usesCleartextTraffic="true"`. Это глобальное разрешение
   приложению ходить по обычному HTTP (без TLS) на любые домены.
2. При запуске `MainActivity` стартует фоновый поток, который реально
   делает HTTP-запрос к hardcoded-URL `http://api.example.com/v1/profile`
   через `HttpURLConnection`. Запрос несёт заголовок
   `Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.fake.fake`, то есть имитирует
   аутентифицированного пользователя.

Вместе это означает, что и сам трафик не защищён (любой между телефоном
и сервером может его читать и подменять), и в этом трафике уходит токен
аутентификации.

Расположение: `src/main/AndroidManifest.xml` (атрибут
`usesCleartextTraffic`) и `src/main/kotlin/com/masttest/vuln06/MainActivity.kt`,
функция `fetchInsecure()`.

## Что должен найти сканер

Минимально достаточный отчёт — два связанных finding'а:

1. В манифесте у `<application>` стоит `usesCleartextTraffic="true"`.
   Это глобально разрешает приложению незашифрованный трафик.
   Категория — небезопасное сетевое взаимодействие
   (MASVS-NETWORK-1, CWE-319).
2. В коде `MainActivity.kt` выполняется реальный HTTP-запрос
   к `http://api.example.com/v1/profile` с заголовком `Authorization`.
   Та же категория.

Хороший сканер должен зарепортить **оба**. Если он флажит только манифест —
он пропустит реальные апки, где разработчик оставил `usesCleartextTraffic`
включённым «на всякий случай», но при этом всё равно гонит секреты по HTTP.
Если флажит только код — он пропустит ситуацию, где манифест разрешает
HTTP всему приложению, что разрешает плагинам/SDK тоже ходить по HTTP.

Если сканер вернёт что-то ещё, кроме этих двух мест — это
ложное срабатывание (например, exported-launcher на `MainActivity` —
это нормальный лаунчер, не уязвимость).
