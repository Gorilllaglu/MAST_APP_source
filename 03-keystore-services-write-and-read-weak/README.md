# 03 — Три keystore-сервиса (write + readable + weak)

## APK после сборки

```bash
./gradlew :micro:03-keystore-services-write-and-read-weak:assembleDebug
```

## Что заложено в приложение

Три `Service`, каждый — отдельная уязвимость вокруг хранилища ключей.
Все стартуются из `MainActivity.onCreate`.

| # | Service | Хранилище | Уязвимость | OWASP | R-номер |
|---|---|---|---|---|---|
| 1 | `HardwareKeyStoreWriteService` | Android Hardware-Backed Keystore | Доступное на запись (generateKey без `setUserAuthenticationRequired`) | **M9** Insecure Data Storage | R017 |
| 2 | `KeyChainReadablePrivateWeakService` | Android KeyChain (системное файловое) | Доступное на чтение с приватными ключами, защищёнными слабым паролем (`WEAK_PKCS12_PASSWORD = "android"`) | **M1** Improper Credential Usage | R018 / R035 |
| 3 | `HardwareKeyStoreReadablePrivateWeakService` | Android Hardware-Backed Keystore | Доступное на чтение со слабым паролем + закрытые ключи (RSA-keypair, `KeyStore.PasswordProtection("android")`) | **M1** Improper Credential Usage | R019 / R036 |

### Сигналы для сканера

- `KeyStore.getInstance("AndroidKeyStore")` × 2
- `KeyGenParameterSpec.Builder` без `setUserAuthenticationRequired(true)` × 2
- `KeyPairGenerator.getInstance(KEY_ALGORITHM_RSA, "AndroidKeyStore")` (Service 3 — приватный ключ)
- `KeyChain.getPrivateKey(this, alias)` + `KeyChain.getCertificateChain` (Service 2)
- `KeyStore.PasswordProtection("android".toCharArray())` (Services 1 и 3 — для AndroidKeyStore не работает, но literal в DEX)
- 3 hardcoded литерала alias + 2 раза literal `"android"` как weak password

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а (по одному на Service)
+ дополнительные сигналы:

1. AndroidKeyStore-write без user-auth (Service 1) — R017 + M9.
2. KeyChain-read + weak PKCS#12 password (Service 2) — R018 / R035 + M1.
3. AndroidKeyStore-read RSA-private + weak password (Service 3) — R019 / R036 + M1.

Сильный сканер дополнительно репортит:

- литерал `"android"` как known-default/weak password (R130)
- 3 hardcoded keystore alias (R031)
- генерация RSA в AndroidKeyStore без attestation / без `setKeyValidityForOriginationEnd` (R135 — нет HW-backing-check)
