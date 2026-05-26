# 37 — Ключи и сертификаты в директории/ресурсах приложения

## APK после сборки

```bash
./gradlew :micro:37-keys-and-certs-in-resources:assembleDebug
```

Файл: `micro/37-keys-and-certs-in-resources/build/outputs/apk/debug/37-keys-and-certs-in-resources-debug.apk`

## Что заложено в приложение

В одном app **восемь** разных файлов key-материала — по чистому
триггеру на каждое из четырёх требований таблицы:

| # | Файл | PEM-заголовок / магия | Покрывает |
|---|---|---|---|
| 1 | `res/raw/public_cert_x509.pem` | `-----BEGIN CERTIFICATE-----` (X.509) | **R026** — публичный сертификат в ресурсах |
| 2 | `res/raw/public_key_rsa.pem` | `-----BEGIN PUBLIC KEY-----` (SubjectPublicKeyInfo) | **R026** — публичный ключ в ресурсах |
| 3 | `res/raw/private_unencrypted_pkcs8.pem` | `-----BEGIN PRIVATE KEY-----` (PKCS#8, БЕЗ пароля) | **R027** — приватный ключ без пароля |
| 4 | `res/raw/private_unencrypted_rsa.pem` | `-----BEGIN RSA PRIVATE KEY-----` (PKCS#1, legacy, БЕЗ пароля) | **R027** — приватный ключ без пароля (legacy формат) |
| 5 | `res/raw/private_encrypted_pkcs8.pem` | `-----BEGIN ENCRYPTED PRIVATE KEY-----` (PKCS#8 шифрованный) | **R028** — приватный ключ под паролем |
| 6 | `res/raw/client_pkcs12.p12` | бинарь PKCS#12 (ASN.1 SEQUENCE) | **R028** — приватный ключ в PKCS#12-keystore под паролем |
| 7 | `assets/client_bouncy.bks` | бинарь BKS (`BKS-1` magic) | **R028** — приватный ключ в BKS-keystore под паролем |
| 8 | `res/raw/mystery_key.bin` | произвольный бинарь без заголовков | **R029** — generic key-файл (тип неочевиден) |

Каждый из этих файлов **реально читается** в `KeyMaterialUsage.kt`
через стандартные API:

- `CertificateFactory.getInstance("X.509").generateCertificate(...)` для #1
- `KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(...))` для #2
- `KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(...))` для #3
- сырой `readText()` для #4 (PKCS#1 неудобно парсить в Java напрямую)
- `readText()` + использование `ENCRYPTED_PEM_PASSWORD = "changeit"` для #5
- `KeyStore.getInstance("PKCS12").load(input, "changeit".toCharArray())` для #6
- `KeyStore.getInstance("BKS").load(input, "android".toCharArray())` для #7
- `openRawResource(...).readBytes()` для #8

Вызовы обёрнуты в `runCatching`, потому что содержимое — placeholder и
не парсится как валидный ключ. Сканеру важна сама связка «файл в
ресурсах ↔ API чтения ключа» и сам факт наличия файла.

### Бонус-signal: hardcoded пароли

`KeyMaterialUsage.kt` содержит три константы:

```kotlin
private const val ENCRYPTED_PEM_PASSWORD: String = "changeit"   // PKCS#8 PEM
private const val PKCS12_PASSWORD: String = "changeit"          // PKCS#12
private const val BKS_PASSWORD: String = "android"              // BKS
```

`changeit` — дефолтный пароль Java truststore'а, `android` — типичный
default для debug-keystore'ов. Оба — известные «слабые» пароли.
Это перекликается с семейством R022/R023 («слабый пароль keystore'а»),
но **основной фокус этого app — R026/R027/R028/R029**.

Расположение слабостей:

- `src/main/res/raw/*.pem` — пять разных PEM-блоков (5 файлов)
- `src/main/res/raw/client_pkcs12.p12` — PKCS#12-keystore
- `src/main/res/raw/mystery_key.bin` — generic blob
- `src/main/assets/client_bouncy.bks` — BKS-keystore
- `src/main/kotlin/com/masttest/vuln37/KeyMaterialUsage.kt` — точки
  использования (call-sites + пароли)

## Что должен найти сканер

Минимально достаточный отчёт — **восемь** finding'ов с правильной
классификацией по категориям:

### R026 — публичный материал

1. `public_cert_x509.pem` — X.509 сертификат в директории ресурсов.
2. `public_key_rsa.pem` — публичный ключ в директории ресурсов.

Severity обычно LOW: публичный материал не секрет, но захардкоженный
trust-anchor — это причина последующих pinning-bypass'ов при ротации
ключей.

### R027 — приватный ключ БЕЗ пароля

3. `private_unencrypted_pkcs8.pem` — PKCS#8 plaintext.
4. `private_unencrypted_rsa.pem` — PKCS#1 plaintext (legacy формат).

Severity CRITICAL: любой, кто получил APK, **сразу** имеет
полную копию приватного ключа.

### R028 — приватный ключ ПОД паролем

5. `private_encrypted_pkcs8.pem` — PKCS#8 шифрованный.
6. `client_pkcs12.p12` — PKCS#12-keystore.
7. `assets/client_bouncy.bks` — BKS-keystore.

Severity HIGH-MEDIUM в зависимости от стойкости пароля. Сильный
сканер дополнительно проверит: если пароль `changeit` / `android` /
другой common-default найден рядом в коде — поднять severity до
CRITICAL (фактически = R027 после brute-force).

### R029 — сертификат/ключ в ресурсах (общая категория)

8. `mystery_key.bin` — бинарный blob без явных PEM-заголовков, но
   с именем, содержащим `key`. Сканер должен флажить либо по имени
   файла (`*_key*`, `*cert*`, `*credential*`), либо по entropy-эвристике.

### Бонус-signal

9. Хорошо, если сканер дополнительно зарепортит **три hardcoded
   пароля** (`ENCRYPTED_PEM_PASSWORD = "changeit"`, `PKCS12_PASSWORD =
   "changeit"`, `BKS_PASSWORD = "android"`) как сильный сигнал
   слабого/дефолтного пароля keystore'а.

Хороший сканер сочетает три источника:

- расширение файла + содержимое (PEM-заголовки в первых байтах),
- имя файла (`*private*` / `*public*` / `*cert*` / `*key*`),
- code-flow связи (`CertificateFactory` / `KeyStore.load` /
  `KeyFactory.generatePrivate` / `KeyFactory.generatePublic`).

Слабый сканер ловит только PEM-заголовки в текстовых файлах и
пропустит `mystery_key.bin` (R029) и `*.p12`/`*.bks` (бинарные R028).

## Чего сканер НЕ должен делать

- Не должен флажить `client_pkcs12.p12` как R027 (отсутствие пароля) —
  PKCS#12-контейнер сам по себе подразумевает наличие пароля.
  Правильная категория — R028.
- Не должен пугаться сертификата `public_cert_x509.pem` так же сильно,
  как приватного ключа — это разные классы severity.
- Не должен флажить `MainActivity` (лаунчер).
