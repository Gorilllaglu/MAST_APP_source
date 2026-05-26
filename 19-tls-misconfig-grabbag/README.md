# 19 — Сборная солянка ошибок TLS-конфигурации

## APK после сборки

```bash
./gradlew :micro:19-tls-misconfig-grabbag:assembleDebug
```

Файл: `micro/19-tls-misconfig-grabbag/build/outputs/apk/debug/19-tls-misconfig-grabbag-debug.apk`

## Что заложено в приложение

В одном файле `TlsClients.kt` объявлены три фабричных метода для
`OkHttpClient`, каждый с отдельной TLS-ошибкой. Плюс контрольный
«правильный» метод `safePinnedClient`, который НЕ должен попасть
в отчёт.

1. **`trustAllClient()`** — клиент с тремя совместными ошибками:
   - анонимный `X509TrustManager` с пустыми `checkClientTrusted` /
     `checkServerTrusted` (молча принимает любой сертификат);
   - `HostnameVerifier`, который всегда возвращает `true` (любой
     hostname проходит);
   - `SSLContext`, инициализированный этим trust-all менеджером,
     передан в `OkHttpClient.Builder.sslSocketFactory(...)`.
   В сумме это полное отключение проверки TLS — MITM с любым
   self-signed сертификатом проходит. Категория R013/R098.
2. **`noPinningClient()`** — обычный `OkHttpClient.Builder().build()`,
   без `CertificatePinner`. Сам по себе trust-anchors системы
   используются (включая user CA), pinning отсутствует. Любой CA,
   выпустивший сертификат на этот hostname (включая корпоративный
   прокси), будет принят. Категория R098.
3. **`hostnameOnlyPinningClient()`** — клиент, в котором попытались
   «закрепить» соединение через `HostnameVerifier` со сравнением
   `hostname == "api.example.com"`. Это **не** pinning: оно никак
   не связано с сертификатом, проверяется только TLS SNI/hostname.
   Любой сертификат для `api.example.com`, выписанный любым
   доверенным CA, будет принят. Категория R099.

Контрольный метод **`safePinnedClient()`** использует
`CertificatePinner.Builder().add(host, "sha256/...")` — это
правильный pinning по SPKI-хешу. Если сканер срабатывает на этот
метод — это false positive.

Расположение: `src/main/kotlin/com/masttest/vuln19/TlsClients.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на каждую
проблемную фабрику:

1. `trustAllClient` — пустые `checkClientTrusted/checkServerTrusted`
   и/или `HostnameVerifier { _, _ -> true }`. Категория R013/R098.
   Возможно сканер выдаст это как два отдельных finding'а
   (trust-all-X509TrustManager + always-true-HostnameVerifier) —
   тоже валидно.
2. `noPinningClient` — `OkHttpClient` собран без `.certificatePinner(...)`.
   Категория R098 (отсутствует или некорректный SSL-pinning).
3. `hostnameOnlyPinningClient` — `HostnameVerifier` использован вместо
   `CertificatePinner`. Категория R099.

Хороший сканер строит граф вызовов `OkHttpClient.Builder` и видит
**отсутствие** `.certificatePinner(...)` рядом с `.build()` — это
то, что отличает п.2 от p.3 (который вообще зашёл в HostnameVerifier).

**Чего сканер не должен делать**:

- Не должен флажить `safePinnedClient` — там pinning сделан
  правильно через `CertificatePinner.add(host, "sha256/...")`.
- Не должен флажить `MainActivity` (лаунчер).
