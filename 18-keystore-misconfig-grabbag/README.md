# 18 — Неправильная конфигурация AndroidKeyStore

## APK после сборки

```bash
./gradlew :micro:18-keystore-misconfig-grabbag:assembleDebug
```

Файл: `micro/18-keystore-misconfig-grabbag/build/outputs/apk/debug/18-keystore-misconfig-grabbag-debug.apk`

## Что заложено в приложение

В одном файле `KeyManager.kt` объявлено три метода для генерации
ключей в `AndroidKeyStore`, и в каждом по одной типичной ошибке.
Плюс один контрольный «безопасный» метод, который НЕ должен попасть
в отчёт.

1. **`generateKeyWithoutAuth(alias)`** — `KeyGenParameterSpec.Builder`
   построен **без** вызова `setUserAuthenticationRequired(true)`.
   Это значит, что после генерации ключ можно использовать сразу,
   даже если телефон украден и не залочен. Любой код в приложении
   (включая вредоносный, попавший туда через webview/SDK/intent
   injection) может вызывать `cipher.init(ENCRYPT_MODE, key)` без
   биометрии или PIN'а. Категория R136.
2. **`generateKeyWithoutHwBackingCheck(alias)`** — ключ генерируется
   с указанием `KEYSTORE = "AndroidKeyStore"`, но **не проверяется**,
   что он реально оказался в TEE/StrongBox. На устройствах без
   секурного железа Android делает software fallback, а приложение
   об этом не знает. Безопасный паттерн (показан в
   `safeGenerateAndVerifyHwBacking` для контраста) — получить
   `KeyInfo` через `SecretKeyFactory` и проверить
   `info.isInsideSecureHardware`. Категория R135.
3. **`generateKeyWithoutBiometricInvalidation(alias)`** — `Builder`
   построен **без** `setInvalidatedByBiometricEnrollment(true)`.
   Если злоумышленник физически добавит свой отпечаток в систему
   (короткий доступ к разблокированному устройству), он сможет
   разблокировать наш ключ — старая биометрия не инвалидирует ключ.
   Категория R137.

Контрольный метод **`safeGenerateAndVerifyHwBacking()`** делает всё
правильно: вызывает оба `setUserAuthenticationRequired(true)` и
`setInvalidatedByBiometricEnrollment(true)`, затем получает `KeyInfo`
через `SecretKeyFactory.getKeySpec` и проверяет
`info.isInsideSecureHardware`. Если сканер срабатывает на этот метод
— это ложноположительное срабатывание, правило плохо отделяет
правильное использование от неправильного.

Расположение: `src/main/kotlin/com/masttest/vuln18/KeyManager.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на каждый
небезопасный метод:

1. `generateKeyWithoutAuth` — отсутствует `setUserAuthenticationRequired(true)`
   на `KeyGenParameterSpec.Builder`. Категория R136.
2. `generateKeyWithoutHwBackingCheck` — после `generateKey()` нет
   `KeyInfo.isInsideSecureHardware`- проверки. Категория R135.
3. `generateKeyWithoutBiometricInvalidation` — отсутствует
   `setInvalidatedByBiometricEnrollment(true)`. Категория R137.

Сильный сканер делает не grep по именам, а реальную проверку:
строит граф вызовов `KeyGenParameterSpec.Builder.*` и смотрит,
вызваны ли нужные setter'ы перед `.build()`.

**Чего сканер не должен делать**:

- Не должен флажить `safeGenerateAndVerifyHwBacking` — там всё
  правильно.
- Не должен флажить `MainActivity` (лаунчер).
