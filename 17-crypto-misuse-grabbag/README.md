# 17 — Сборная солянка криптографических ошибок

## APK после сборки

```bash
./gradlew :micro:17-crypto-misuse-grabbag:assembleDebug
```

Файл: `micro/17-crypto-misuse-grabbag/build/outputs/apk/debug/17-crypto-misuse-grabbag-debug.apk`

## Что заложено в приложение

В одном файле `CryptoUtil.kt` сразу семь распространённых ошибок
криптографии. Каждый метод — отдельная уязвимость, по которой у
сканера должно быть отдельное правило.

1. **Hardcoded key.** Константа `HARDCODED_KEY = "ThisIsTheKey1234"`
   используется во всех AES/DES операциях. Подобный ключ виден в
   декомпиляции apk через jadx за минуту.
2. **AES в режиме ECB.** Метод `aesEcb()` использует
   `Cipher.getInstance("AES/ECB/PKCS5Padding")`. ECB-режим
   раскрывает паттерны входных данных: одинаковые plaintext-блоки
   шифруются одинаково. Не семантически безопасен.
3. **AES/CBC со статическим IV.** Метод `aesWithStaticIv()` использует
   `IvParameterSpec(ByteArray(16) { 0 })` — IV из шестнадцати нулей.
   Шифр одного и того же plaintext'а с одним ключом всегда даёт
   одинаковый ciphertext, что выдаёт повторы и ломает CBC.
4. **DES.** Метод `weakDes()` использует `Cipher.getInstance("DES/CBC/PKCS5Padding")`.
   56-битный эффективный ключ ломается brute-force за дни. Алгоритм
   признан устаревшим много лет.
5. **MD5 для хэширования.** Метод `md5Hash()` использует
   `MessageDigest.getInstance("MD5")`. MD5 уязвим к коллизиям и
   непригоден в безопасности.
6. **SHA-1 для хэширования.** Метод `sha1Hash()` использует
   `MessageDigest.getInstance("SHA-1")`. SHA-1 deprecated, коллизии
   реальные (SHAttered).
7. **PBKDF2 со слабыми параметрами.** Метод `weakPbkdf()` использует
   `iterationCount=1000` (на 2024 OWASP рекомендует ≥600 000) и
   соль `"saltsalt"` (short, low-entropy, dictionary-word).

Все методы вызываются из `MainActivity.onCreate`, чтобы DCE/R8
не выкинул их при минификации (для будущих экспериментов с
включённым `isMinifyEnabled = true`).

Расположение всех слабостей: `src/main/kotlin/com/masttest/vuln17/CryptoUtil.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **семь** finding'ов, по одному на каждую
из перечисленных ошибок:

1. `HARDCODED_KEY` — вшитый ключ AES/DES в коде (R031, R128, R130).
2. `aesEcb()` — режим ECB для блочного шифра (R125, R126).
3. `aesWithStaticIv()` — статический IV для CBC (R127).
4. `weakDes()` — устаревший слабый шифр (R124).
5. `md5Hash()` — слабая хэш-функция (R132).
6. `sha1Hash()` — слабая хэш-функция (R132).
7. `weakPbkdf()` — недостаточный iteration count в KDF + слабая соль
   (R131, R133, R134). Допустимо если сканер делает два отдельных
   finding'а: один на iter=1000, второй на короткую/словарную соль.

Хороший сканер показывает все семь как **отдельные** findings —
у каждого свой fix, и нельзя их сворачивать в общий
«CryptoUtil contains insecure crypto».

**Чего сканер не должен делать**:

- Не должен флажить `Cipher.getInstance` сам по себе — это легитимный
  API. Уязвимость в его аргументе.
- Не должен флажить `SecretKeySpec` сам по себе — флажить нужно
  именно использование hardcoded источника.
- Не должен флажить `PBKDF2WithHmacSHA1` как алгоритм (он сам нормальный),
  флажить нужно слабые параметры.
