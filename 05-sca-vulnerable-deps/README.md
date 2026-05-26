# 05 — SCA fixture (Software Composition Analysis)

## APK после сборки

```bash
./gradlew :micro:05-sca-vulnerable-deps:assembleDebug
```

## Что заложено в приложение

Приложение тривиальное по коду (одна `MainActivity`), но в
`build.gradle.kts` подключено **шесть** библиотек **намеренно
устаревших версий**, каждая с известными CVE. SCA-сканер должен
их обнаружить.

Все 6 библиотек реально используются (touch-вызов в `VulnerableLibsTouch`),
чтобы их классы гарантированно попали в финальный DEX — даже при
включении R8/minify.

| # | Зависимость | Версия | CVE / advisory | Класс уязвимости |
|---|---|---|---|---|
| 1 | `com.squareup.okhttp3:okhttp` | **3.12.0** | **CVE-2021-0341** | TLS hostname verification bypass |
| 2 | `com.google.code.gson:gson` | **2.8.5** | **CVE-2022-25647** | Deserialization / DoS |
| 3 | `com.fasterxml.jackson.core:jackson-databind` | **2.9.10** | **CVE-2020-36518**, **CVE-2022-42003**, **CVE-2022-42004**, ... | RCE через polymorphic deserialization gadgets |
| 4 | `commons-collections:commons-collections` | **3.2.1** | **CVE-2015-7501** | Java deserialization RCE (классический ysoserial gadget) |
| 5 | `org.bouncycastle:bcprov-jdk15on` | **1.55** | **CVE-2018-1000613**, **CVE-2018-5382** | Криптографические — RSA-key recovery, GCM/AEAD issues |
| 6 | `commons-codec:commons-codec` | **1.10** | multiple advisories | Устаревшая, заменена 1.15+ |

### Соответствующие touch-классы в коде

```kotlin
okhttp3.OkHttpClient.Builder().build()                                    // ← OkHttp 3.12.0
com.google.gson.Gson().toJson(...)                                        // ← Gson 2.8.5
com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(...)     // ← Jackson 2.9.10
org.apache.commons.collections.functors.InvokerTransformer.getInstance(...) // ← Commons-Collections 3.2.1 (gadget!)
org.bouncycastle.jce.provider.BouncyCastleProvider()                      // ← BC 1.55
org.apache.commons.codec.digest.DigestUtils.md5Hex(...)                   // ← Commons-Codec 1.10
```

## Что должен найти сканер

Минимально достаточный отчёт — **шесть** finding'ов уровня
«Vulnerable dependency», по одному на библиотеку, с привязкой к
конкретному CVE:

1. `okhttp 3.12.0` → CVE-2021-0341
2. `gson 2.8.5` → CVE-2022-25647
3. `jackson-databind 2.9.10` → CVE-2020-36518 (и каскад других)
4. `commons-collections 3.2.1` → CVE-2015-7501
5. `bouncycastle bcprov-jdk15on 1.55` → CVE-2018-1000613 + …
6. `commons-codec 1.10` → multiple

### Как именно сканер должен это видеть

Зависит от подхода:

- **По декларации Gradle** (если сканер читает `build.gradle.kts` /
  `gradle.lockfile` напрямую) — простой grep по строкам `implementation("...")`.
- **По содержимому APK** — извлечь classes.dex + просканировать
  package-имена (`okhttp3.*`, `com.google.gson.*`,
  `com.fasterxml.jackson.*`, `org.apache.commons.collections.*`,
  `org.bouncycastle.*`, `org.apache.commons.codec.*`) и сопоставить
  с базой CVE по версионным маркерам внутри классов.
- **По META-INF** — некоторые библиотеки оставляют
  `META-INF/<artifact>.kotlin_module` или `META-INF/maven/<group>/pom.properties`
  с version-info.

Сильный сканер делает все три способа и репортит:

- artifact name + version
- список CVE
- CVSS score
- рекомендованная safe version
- путь к gradle-декларации, где зависимость объявлена

Слабый сканер — только наличие package-имени, без сопоставления версии.

## Чего сканер НЕ должен делать

- Не должен флажить **код приложения** (vuln05.MainActivity и т.п.) —
  это не уязвимая часть.
- Не должен флажить **AndroidX / Kotlin stdlib** — они актуальных
  версий.
- Не должен флажить как «использование `MD5`» — это пример
  (DigestUtils.md5Hex) для туча класса; настоящая уязвимость —
  устаревшая версия библиотеки, а не сам факт MD5 (хотя его флажить
  тоже допустимо — это пересечение с правилом R132).
