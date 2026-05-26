# 01 — SharedPreferences с паролем в открытом виде

## APK после сборки

```bash
./gradlew :micro:01-storage-plaintext-prefs:assembleDebug
```

Файл: `micro/01-storage-plaintext-prefs/build/outputs/apk/debug/01-storage-plaintext-prefs-debug.apk`

## Что заложено в приложение

При запуске `MainActivity` вызывает приватный метод `savePlaintextCreds()`,
который через стандартный API SharedPreferences сохраняет два чувствительных
значения в открытом виде:

- ключ `password` со значением-литералом `hunter2`
- ключ `auth_token` со значением, имитирующим JWT (`eyJhbGciOiJIUzI1NiJ9.fakepayload.fakesig`)

Запись делается в `getSharedPreferences("user_creds", MODE_PRIVATE)`,
без какого-либо слоя шифрования (нет `EncryptedSharedPreferences`,
нет ручного AES, ничего). После запуска приложения файл
`/data/data/com.masttest.vuln01/shared_prefs/user_creds.xml` содержит
эти строки в plaintext и читается с любого рутованного устройства,
через `adb backup`, либо при наличии второй уязвимости с file read.

Расположение уязвимого кода: `src/main/kotlin/com/masttest/vuln01/MainActivity.kt`,
функция `savePlaintextCreds()`.

## Что должен найти сканер

Сканер должен сообщить о небезопасном хранении секретов в SharedPreferences.
Минимально достаточный отчёт — **два** finding'а:

1. Запись пароля. Файл `MainActivity.kt`, вызов
   `prefs.edit().putString("password", "hunter2")`. Категория —
   небезопасное хранение учётных данных (MASVS-STORAGE-1, CWE-312).
2. Запись токена аутентификации. Тот же файл, тот же чейн `.edit()`,
   вызов `putString("auth_token", "eyJhbGciOiJIUzI1NiJ9...")`.
   Та же категория.

Допустимо если сканер сворачивает оба `putString` в один общий finding
на весь чейн `.edit()` — этот случай тоже считается верным детектом.

Что должно настораживать в результате сканирования:

- Если сканер не нашёл **ничего** — правило на plaintext credentials
  в SharedPreferences не работает.
- Если сканер нашёл что-то ещё, кроме этих двух мест (например
  «exported activity» на launcher или «cleartext» на чём-то) — это
  ложное срабатывание, оно не относится к заложенной уязвимости.
