# 04 — Три keystore-сервиса (readable + weak + mixed)

## APK после сборки

```bash
./gradlew :micro:04-keystore-services-read-weak-mixed:assembleDebug
```

## Что заложено в приложение

Три `Service`, каждый — отдельная вариация уязвимости вокруг
хранилища ключей. Все стартуются из `MainActivity.onCreate`.

| # | Service | Хранилище | Уязвимость | OWASP | R-номер |
|---|---|---|---|---|---|
| 1 | `HardwareKeyStoreReadableWeakPublicService` | Android Hardware-Backed Keystore | Доступное на чтение со слабым паролем (`"1234"`), содержащим открытые ключи (RSA-keypair, читается PUBLIC часть) | **M1** Improper Credential Usage | R020 / R037 |
| 2 | `KeyChainReadableFileService` | Android KeyChain (системное файловое) | Доступное на чтение файловое хранилище (`KeyChain.getPrivateKey` по hardcoded alias) | **M9** Insecure Data Storage | R021 / R038 |
| 3 | `HardwareKeyStorePrivateWeakService` | Android Hardware-Backed Keystore | Использование приватного ключа (EC P-256), защищённого слабым паролем (`"letmein"`) | **M1** Improper Credential Usage | R022 / R039 |

### Service 1 — public keys + weak password

```kotlin
KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, "AndroidKeyStore")
    .also { it.initialize(spec) }
    .generateKeyPair()
val publicKey = ks.getCertificate(ALIAS).publicKey
ks.getEntry(ALIAS, KeyStore.PasswordProtection("1234".toCharArray()))
```

### Service 2 — KeyChain file-based readable

```kotlin
val privateKey = KeyChain.getPrivateKey(this, "vuln04_keychain_file_alias")
val chain = KeyChain.getCertificateChain(this, "vuln04_keychain_file_alias")
```

### Service 3 — private key + weak password (EC P-256)

```kotlin
KeyPairGenerator.getInstance(KEY_ALGORITHM_EC, "AndroidKeyStore")
    .also { it.initialize(specP256) }
    .generateKeyPair()
val entry = ks.getEntry(ALIAS,
    KeyStore.PasswordProtection("letmein".toCharArray())) as KeyStore.PrivateKeyEntry
val privateKey = entry.privateKey
```

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а (по одному на Service):

1. AndroidKeyStore-read RSA-public + weak password — R020 / R037 + M1.
2. KeyChain-read file-based по hardcoded alias — R021 / R038 + M9.
3. AndroidKeyStore-read EC-private + weak password — R022 / R039 + M1.

Сильный сканер дополнительно репортит:

- `"1234"` (Service 1) — known-weak numeric password (R130)
- `"letmein"` (Service 3) — dictionary word password (R130/R134)
- 3 hardcoded keystore aliases (R031)
- Все три `KeyGenParameterSpec.Builder` без `setUserAuthenticationRequired(true)` (R136)
- Все три случая без `KeyInfo.isInsideSecureHardware`-проверки после `generateKey/generateKeyPair` (R135)
