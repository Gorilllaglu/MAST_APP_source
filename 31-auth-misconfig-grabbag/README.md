# 31 — Сборная солянка ошибок аутентификации

## APK после сборки

```bash
./gradlew :micro:31-auth-misconfig-grabbag:assembleDebug
```

Файл: `micro/31-auth-misconfig-grabbag/build/outputs/apk/debug/31-auth-misconfig-grabbag-debug.apk`

## Что заложено в приложение

В одном файле `AuthFlows` сразу три класса auth-ошибок.

1. **BiometricPrompt без CryptoObject.**
   ```kotlin
   prompt.authenticate(info)        // нет CryptoObject
   ...
   onAuthenticationSucceeded(result) {
       prefs.edit().putBoolean("biometric_unlocked", true).apply()
   }
   ```
   Биометрия используется только как «yes/no»-флаг. Привязки
   разблокировки к ключу в AndroidKeyStore (через CryptoObject)
   нет. Атакующий с root'ом может через Frida хукнуть
   `onAuthenticationSucceeded` и обойти всю проверку. Категория R136.

2. **OAuth без PKCE и без state.** Метод `buildOAuthUrlNoPkceNoState`
   собирает authorization URL только с `response_type=code`,
   `client_id`, `redirect_uri`, `scope`. Параметров `code_challenge`/
   `code_challenge_method` нет (нет PKCE), параметра `state` нет
   (CSRF / login CSRF возможен). Любой, кто перехватил
   `authorization_code` через подделанный redirect (см. app 30,
   custom-URI scheme deeplink hijack), может обменять его на токены.

3. **Hardcoded backdoor.** Метод `tryHardcodedBackdoor` сравнивает
   ввод с захардкоженной строкой `"admin:debugbypass"` и при
   совпадении возвращает true (вход с правами админа). Подобные
   обходные пути часто остаются в коде после внутреннего
   тестирования.

Расположение: `src/main/kotlin/com/masttest/vuln31/MainActivity.kt`
(объект `AuthFlows`).

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а:

1. `BiometricPrompt.authenticate(info)` без `BiometricPrompt.CryptoObject` —
   биометрия используется как простой gate. Категория R136.
   Сильный сканер также видит, что в `onAuthenticationSucceeded`
   `result.cryptoObject` не используется — это второй сигнал.
2. URL OAuth `authorize`-эндпоинта формируется без `code_challenge`
   и без `state` (рассуждение по строкам / data flow). Категория
   MASVS-AUTH (нет прямого R-номера, но это классическое нарушение).
3. Сравнение `if (input == "admin:debugbypass")` — hardcoded
   backdoor. Категория R031 (sensitive в исходном коде, но более
   точно — backdoor pattern).

**Чего сканер не должен делать**:

- Не должен флажить сам `BiometricPrompt` сам по себе — это
  легитимный API.
- Не должен флажить `URLEncoder.encode` — это правильная защита
  от URL injection.
- Не должен флажить `MainActivity` (лаунчер).
