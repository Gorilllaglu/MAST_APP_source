# TestingAppAndroid-2 — Разбор заложенных уязвимостей

Учебное Android-приложение (`com.example.testingapp`), в которое **намеренно**
заложен набор типовых мобильных уязвимостей. Почти везде в коде есть
комментарии-указатели (`M8`, `M9`, `MSTG-…`, «vulnerability», «insecure…»).
Ниже — разбор по каждому классу: что именно заложено (с точным местом),
что должен найти сканер и где не надо ловить ложные срабатывания.

Сверено с эталоном безопасной разработки
`Methodology/knowledges/secure-coding` (`rule-mobile-app-security`,
`always-no-hardcoded-secrets`, `rule-logging-monitoring`,
`rule-input-validation-injection`, `rule-database-data-storage`).

Классы находок (по убыванию значимости):

1. [Небезопасная конфигурация сети (NSC + cleartext + отключённая проверка серта)](#1)
2. [Хардкод секретов в коде](#2)
3. [Небезопасное хранение: `MODE_WORLD_READABLE` + секреты в SharedPreferences](#3)
4. [Логирование чувствительных данных](#4)
5. [Экспортированные Activity без защиты (в т.ч. deeplink)](#5)
6. [Экспортированные Service без защиты](#6)
7. [Экспортированные ContentProvider — утечка данных и файлов](#7)
8. [Экспортированные BroadcastReceiver (авто-старт, командный канал, перехват SMS)](#8)
9. [`debuggable=true` в манифесте](#9)
10. [Небезопасный backup (`allowBackup` + полный backup всех данных)](#10)
11. [Избыточные разрешения и legacy-хранилище](#11)
12. [Отсутствие валидации входных данных (sink'и инъекций)](#12)

---

<a id="1"></a>
## 1. Небезопасная конфигурация сети

### Что заложено

Сразу три уровня ослабления транспорта:

- **`AndroidManifest.xml:25`** — `android:usesCleartextTraffic="true"` на
  `<application>`: глобально разрешён HTTP без TLS.
- **`res/xml/network_security_config.xml`** (подключён в манифесте, строка 24):
  - `base-config cleartextTrafficPermitted="true"` (стр. 4) — cleartext по умолчанию;
  - доверие к **пользовательским CA** — `<certificates src="user"/>` в
    `base-config` (стр. 7), в `domain-config` (стр. 25) **и** в `debug-overrides`
    (стр. 32). Любой добавленный на устройство корпоративный/атакующий CA
    прозрачно читает и подменяет трафик;
  - `domain-config cleartextTrafficPermitted="true"` (стр. 12) для `example.com`,
    `test.example.com`, `api.test.com` с `includeSubdomains`;
  - `pin-set` **пустой и просроченный** (`expiration="2024-12-31"`, стр. 18) —
    пиннинг фактически выключен.
- **`MainActivity.kt:83-99`** — `performInsecureNetworkRequest()`:
  - эндпоинт `http://api.example.com/data` (`MainActivity.kt:24`) кастуется к
    `HttpsURLConnection` — обычный HTTP под видом «https»-класса;
  - `connection.hostnameVerifier = { _, _ -> true }` (`MainActivity.kt:92`) —
    хостнейм-верификатор принимает **любой** сертификат для любого хоста.

### Что должен найти сканер

- Доверие к user-CA — **отдельный finding** (это отдельный класс и отдельный fix):
  указано в трёх местах NSC. Категория — небезопасная конфигурация сети
  (MASVS-NETWORK).
- Cleartext разрешён — в манифесте (`usesCleartextTraffic`), в `base-config` и в
  `domain-config`. Допустимо свернуть в один пункт «cleartext разрешён» со списком
  контекстов.
- Пустой/просроченный `pin-set` — пиннинг отсутствует.
- Отключённая проверка сертификата в коде — `hostnameVerifier { true }` +
  HTTP-эндпоинт (MSTG-NETWORK-2/3). Это отдельная находка от NSC.

Эталон: `rule-mobile-app-security` → «Сеть» (только HTTPS, никакого trust-all
custom-trust-manager-а, явные trust-anchor-ы).

### Чего сканер не должен делать

- Флажить `<certificates src="system"/>` — это легитимный trust-anchor, без него
  TLS не работает.

---

<a id="2"></a>
## 2. Хардкод секретов

### Что заложено

- **`MainActivity.kt:23`** — `HARDCODED_PASSWORD = "admin123"`.
- **`MainActivity.kt:24`** — зашитый эндпоинт API.
- **`TestingApplication.kt:17-18`** — `HARDCODED_API_KEY = "sk_test_1234567890abcdef"`,
  `HARDCODED_SECRET = "super_secret_key_do_not_use_in_production"`.
- В качестве «данных» провайдеров зашиты и выдаются наружу секреты:
  `VulnerableContentProviderOne.kt:38-40` (`sk_live_abcdef`, `secret_key_123`,
  `admin123`), `VulnerableContentProviderThree.kt:37-39` (логины, e-mail,
  хеши паролей).

### Что должен найти сканер

- Секреты (пароль, API-ключ, «secret») в исходном коде — MASVS-CRYPTO / MASVS-STORAGE.
  Эталон: `always-no-hardcoded-secrets` (креды и токены не должны попадать в исходники).
- Зашитые «боевые» ключи в данных провайдеров (`sk_live_…`) — отдельно, т.к. они
  ещё и **отдаются наружу** экспортированным компонентом (см. п. 7).

---

<a id="3"></a>
## 3. Небезопасное хранение данных

### Что заложено

- **`MainActivity.kt:74`** и **`VulnerableActivityThree.kt:52`** —
  `getSharedPreferences(..., MODE_WORLD_READABLE)`: файл настроек делается читаемым
  любым приложением. Туда пишутся пользовательский ввод и **пароль**
  (`MainActivity.kt:77`), а также `username/password/auth_token/api_key`
  (`VulnerableActivityThree.kt:53-58`).
- **`VulnerableBroadcastReceiverTwo.kt:42`** — тоже `MODE_WORLD_READABLE`.
- **`VulnerableBroadcastReceiverThree.kt:76-83`** — перехваченные SMS
  (отправитель + тело) складываются в SharedPreferences.

### Что должен найти сканер

- Использование `MODE_WORLD_READABLE` — небезопасный режим доступа (MASVS-STORAGE,
  MSTG-STORAGE-2). Эталон: `rule-mobile-app-security` → «Хранение» (SharedPreferences
  не для кредов; чувствительное — в Keystore/EncryptedSharedPreferences).
- Хранение паролей/токенов в открытом виде в SharedPreferences (MSTG-STORAGE-1).

### Замечание о честности

`MODE_WORLD_READABLE` **устарел и на API 24+ бросает `SecurityException`** —
на современных устройствах запись не выполнится. Находкой является сам
паттерн/намерение (небезопасный режим + креды в открытом хранилище), а не
«живой» world-readable файл на новом Android. Это стоит отметить в отчёте, но
severity не завышать до «эксплуатируется на стоке из коробки».

---

<a id="4"></a>
## 4. Логирование чувствительных данных

### Что заложено

Массово через `Log.d/Log.e`:

- API-ключ — `TestingApplication.kt:34`;
- пароль/токен/api_key — `VulnerableActivityThree.kt:28-31`;
- deeplink-токен и action — `VulnerableActivityTwo.kt:35-36, 47, 52`;
- координаты GPS — `VulnerableServiceTwo.kt:96, 104`;
- отправитель и тело SMS (потенциально OTP/2FA) — `VulnerableBroadcastReceiverThree.kt:39-40`;
- пользовательский ввод и хранимые данные — `MainActivity.kt:80`, сервисы/провайдеры и т.д.

### Что должен найти сканер

- Запись чувствительных данных (креды, токены, PII, геолокация, содержимое SMS) в
  системный лог (MSTG-STORAGE-3). Эталон: `rule-logging-monitoring` и
  `rule-mobile-app-security` → «Release: никакого verbose-логирования
  пользовательских данных». Усугубляется тем, что приложение `debuggable` (п. 9).

---

<a id="5"></a>
## 5. Экспортированные Activity без защиты

### Что заложено

Все три «vulnerable» Activity объявлены `android:exported="true"` и обрабатывают
внешний ввод без валидации:

- **`VulnerableActivityOne`** (`AndroidManifest.xml:41-50`) — принимает
  `sensitive_data` из intent и обрабатывает без проверки
  (`VulnerableActivityOne.kt:23-30`).
- **`VulnerableActivityTwo`** (`AndroidManifest.xml:53-63`) — **deeplink**
  `testingapp://vulnerable`; из URI берутся `token` и `action` и используются без
  какой-либо валидации/авторизации (`VulnerableActivityTwo.kt:31-53`).
- **`VulnerableActivityThree`** (`AndroidManifest.xml:66-70`) — принимает
  `username/password/auth_token/api_key` из extras, логирует и складывает в
  world-readable prefs (`VulnerableActivityThree.kt:23-60`).

### Что должен найти сканер

- Экспортированные Activity, доступные любому приложению (MASVS-PLATFORM,
  MSTG-PLATFORM-4). Эталон: `rule-mobile-app-security` → «`exported=false` по
  умолчанию; интенты с чувствительными данными — explicit component, не implicit».
- Обработка deeplink-параметров (`token`, `action`) без валидации/авторизации —
  отдельный класс (небезопасные deeplinks, MSTG-PLATFORM-2).

---

<a id="6"></a>
## 6. Экспортированные Service без защиты

### Что заложено

- **`VulnerableServiceOne`** (`AndroidManifest.xml:73-81`, exported, без permission) —
  `IntentService`-**диспетчер команд**: `delete_data` / `update_config` / `execute`
  из extra `command`, без авторизации (`VulnerableServiceOne.kt:17-50`).
- **`VulnerableServiceTwo`** (`AndroidManifest.xml:84-89`, exported, foreground
  `location`) — трекинг геолокации; интервал берётся из intent без проверки; любой
  может запустить/влиять (`VulnerableServiceTwo.kt`).
- **`VulnerableServiceThree`** (`AndroidManifest.xml:92-100`, exported) — bound-сервис
  с операциями `get_data`/`set_data`/`execute_sql`; `execute_sql` — прямой sink
  SQL-инъекции (`VulnerableServiceThree.kt:41, 59-64`).

### Что должен найти сканер

- Экспортированные сервисы без `permission`-защиты — управляемы соседним
  приложением (MASVS-PLATFORM, MSTG-PLATFORM-4).
- Командные диспетчеры (`execute`, `delete_data`, `execute_sql`) на экспортированной
  поверхности — путь к небезопасным операциям от чужого процесса.

---

<a id="7"></a>
## 7. Экспортированные ContentProvider — утечка данных и файлов

### Что заложено

- **`VulnerableContentProviderOne`** (`AndroidManifest.xml:103-109`) — exported;
  `query()` отдаёт зашитые секреты и пароли
  (`VulnerableContentProviderOne.kt:37-42`). Защита заявлена через
  `readPermission/writePermission = READ/WRITE_EXTERNAL_STORAGE` — это **слабое**
  ограничение: разрешение массово выдаётся другим приложениям и не защищает
  чувствительные данные.
- **`VulnerableContentProviderTwo`** (`AndroidManifest.xml:112-117`) — exported,
  `grantUriPermissions="true"`, **без permission**; `query()` перечисляет файлы из
  `filesDir`/`cacheDir` (пути, имена, размеры), есть `openFile()`/`delete()` по
  пользовательскому пути (`VulnerableContentProviderTwo.kt:40-98`) — потенциальный
  path traversal/утечка файлов.
- **`VulnerableContentProviderThree`** (`AndroidManifest.xml:120-124`) — exported,
  **вообще без разрешений**; `query()` дампит таблицу пользователей: логины, e-mail,
  хеши паролей (`VulnerableContentProviderThree.kt:36-42`).

### Что должен найти сканер

- Экспортированные провайдеры, отдающие данные без адекватной авторизации
  (MASVS-PLATFORM/STORAGE, MSTG-PLATFORM-4). Провайдер Three — самый явный (нет
  ни одного ограничения + дамп кредов).
- Слабая защита провайдера One через `EXTERNAL_STORAGE`-разрешение — отметить как
  недостаточную, а не как «защищён».
- Провайдер Two — перечисление/открытие файлов приложения через IPC.

### Чего сканер не должен делать

- Считать провайдер One «защищённым» только потому, что у него заданы
  `readPermission/writePermission`: `EXTERNAL_STORAGE` здесь не является барьером.

---

<a id="8"></a>
## 8. Экспортированные BroadcastReceiver

### Что заложено

- **`VulnerableBroadcastReceiverOne`** (`AndroidManifest.xml:127-136`) — exported,
  `BOOT_COMPLETED`/`QUICKBOOT_POWERON`; авто-стартует `VulnerableServiceOne` после
  загрузки без согласия пользователя (`VulnerableBroadcastReceiverOne.kt:22-41`).
- **`VulnerableBroadcastReceiverTwo`** (`AndroidManifest.xml:139-148`) — exported,
  custom-action `CUSTOM_BROADCAST`, **без permission**; командный канал
  `update_settings`/`clear_data`/`send_data`, пишет в world-readable prefs
  (`VulnerableBroadcastReceiverTwo.kt:30-59`). Любое приложение может послать этот
  broadcast (ср. `MainActivity.kt:51-56`, где он шлётся без ограничения получателя).
- **`VulnerableBroadcastReceiverThree`** (`AndroidManifest.xml:151-160`) — SMS-ресивер:
  читает `SMS_RECEIVED`, ищет паттерны OTP/2FA/«password», логирует тело SMS и
  складывает в prefs (`VulnerableBroadcastReceiverThree.kt`).

### Что должен найти сканер

- Экспортированные ресиверы без ограничения источника (MASVS-PLATFORM,
  MSTG-PLATFORM-4). Эталон: `rule-mobile-app-security` → «Ограничение источников
  broadcast»: статическому ресиверу задавать `android:permission`; внутренние
  сообщения — не через глобальный broadcast.
- Ресивер Two как командный канал от произвольного отправителя.
- Ресивер Three — перехват/логирование SMS-контента (OTP/2FA), утечка PII.

### Чего сканер не должен делать

- Флажить ресивер Three как «exported без защиты»: у него **есть**
  `android:permission="RECEIVE_SMS"` (`AndroidManifest.xml:156`) — источник
  ограничен системой. Его настоящая находка — **перехват и логирование содержимого
  SMS** (privacy/2FA-interception), а не отсутствие permission.

---

<a id="9"></a>
## 9. `debuggable=true`

### Что заложено

`AndroidManifest.xml:16` — `android:debuggable="true"`. Позволяет подключать
отладчик, читать память процесса, дампить переменные; усиливает все находки с
логированием (п. 4).

### Что должен найти сканер

- Флаг `debuggable` в манифесте (MASVS-RESILIENCE). Эталон: `rule-mobile-app-security`
  → «Release-сборки: debuggable off».

---

<a id="10"></a>
## 10. Небезопасный backup

### Что заложено

- `AndroidManifest.xml:14-18` — `allowBackup="true"`, `backupAgent=".TestingBackupAgent"`,
  `fullBackupContent="@xml/backup_rules"`, `hasFragileUserData="true"`.
- `res/xml/backup_rules.xml` — в backup включено **всё**: `sharedpref`, `database`,
  `file`, `root`, `external` (`path="."`).
- `TestingBackupAgent.kt` — резервирует SharedPreferences (в т.ч. `credentials`),
  БД и файлы; в `onRestore` восстанавливает данные **без проверки целостности**
  (`TestingBackupAgent.kt:38-58`).

### Что должен найти сканер

- Включённый backup чувствительных данных (MSTG-STORAGE-8). Эталон:
  `rule-mobile-app-security` → «Отключайте backup для файлов с секретами
  (`allowBackup="false"` + backup-rules)».
- Восстановление без верификации целостности — отдельно.

---

<a id="11"></a>
## 11. Избыточные разрешения и legacy-хранилище

### Что заложено

- `AndroidManifest.xml:8-9` — `READ/WRITE_EXTERNAL_STORAGE` (широкий доступ к
  внешнему хранилищу; на нём же построена «защита» провайдера One).
- `AndroidManifest.xml:21` — `requestLegacyExternalStorage="true"` (обход
  scoped storage).
- `AndroidManifest.xml:11` — `RECEIVE_BOOT_COMPLETED` (нужен ресиверу авто-старта, п. 8).

### Что должен найти сканер

- Избыточные/устаревшие storage-разрешения и `requestLegacyExternalStorage`
  (MASVS-PLATFORM, принцип наименьших привилегий). Отметить связь с провайдером One
  (разрешение как псевдо-защита).

---

<a id="12"></a>
## 12. Отсутствие валидации входных данных (sink'и инъекций)

### Что заложено

Внешний ввод (intent-extras, deeplink, данные провайдера) везде обрабатывается без
санитизации. Явно обозначенные точки инъекций:

- SQL-инъекция в `VulnerableServiceThree.kt:59-64` (`executeSqlInsecurely`,
  `// database.execSQL(sql)`), в `VulnerableActivityOne.kt:37-39`
  (`SELECT … WHERE name = '$data'`), в `VulnerableContentProviderOne` (query без
  проверки `selection`).
- deeplink `token`/`action` без валидации (`VulnerableActivityTwo`, см. п. 5).

### Что должен найти сканер

- Отсутствие валидации/параметризации на пути `внешний источник → sink`
  (MASVS-CODE, MSTG-CODE-8). Эталон: `rule-input-validation-injection`,
  `rule-database-data-storage` (параметризованные запросы).

### Замечание о честности

Опасные SQL-строки **закомментированы** (`// database.execSQL(sql)`,
`// val query = …`). Это заготовленный sink, а не исполняемый код. Флажить как
паттерн/потенциальную инъекцию — корректно; выдавать за «живую» эксплуатируемую
SQLi — нет.

---

## Чего сканер не должен делать (сводка ложных срабатываний)

- **`MainActivity` exported** (`AndroidManifest.xml:29-38`) — это лаунчер
  (`MAIN`/`LAUNCHER`), экспорт обязателен. Не находка.
- **`<certificates src="system"/>`** в NSC — легитимный trust-anchor.
- **Ресивер Three** — не «exported без защиты»: у него есть `RECEIVE_SMS`
  (см. п. 8).
- **Провайдер One** — не считать «защищённым» из-за `EXTERNAL_STORAGE`-разрешений
  (см. п. 7).
- **`MODE_WORLD_READABLE`** — на API 24+ бросает исключение; находка — паттерн, не
  «живой» world-readable файл на стоке (см. п. 3).
- **Закомментированные SQL-строки** — потенциальный sink, не исполняемый код
  (см. п. 12).

---

## Итоговая таблица

| # | Класс находки | Где | Стандарт (MASVS/MSTG) |
|---|---------------|-----|------------------------|
| 1 | Небезопасная сеть: user-CA, cleartext, trust-all verifier | `network_security_config.xml`, `AndroidManifest.xml:25`, `MainActivity.kt:83-99` | MASVS-NETWORK |
| 2 | Хардкод секретов | `MainActivity.kt:23-24`, `TestingApplication.kt:17-18`, провайдеры | MASVS-CRYPTO/STORAGE |
| 3 | `MODE_WORLD_READABLE` + креды в prefs | `MainActivity.kt:74`, `VulnerableActivityThree.kt:52`, receiver Two | MSTG-STORAGE-1/2 |
| 4 | Логирование чувствительных данных | по всему коду (`Log.*`) | MSTG-STORAGE-3 |
| 5 | Экспортированные Activity + deeplink без валидации | `VulnerableActivityOne/Two/Three` | MSTG-PLATFORM-2/4 |
| 6 | Экспортированные Service (командные/location/SQL) | `VulnerableServiceOne/Two/Three` | MSTG-PLATFORM-4 |
| 7 | Экспортированные ContentProvider (дамп кредов, файлы) | `VulnerableContentProviderOne/Two/Three` | MSTG-PLATFORM-4 |
| 8 | Экспортированные BroadcastReceiver (авто-старт, команды, SMS) | `VulnerableBroadcastReceiverOne/Two/Three` | MSTG-PLATFORM-4 / Privacy |
| 9 | `debuggable=true` | `AndroidManifest.xml:16` | MASVS-RESILIENCE |
| 10 | Небезопасный backup всех данных | `AndroidManifest.xml:14-18`, `backup_rules.xml`, `TestingBackupAgent.kt` | MSTG-STORAGE-8 |
| 11 | Избыточные разрешения + legacy storage | `AndroidManifest.xml:8-21` | MASVS-PLATFORM |
| 12 | Отсутствие валидации (sink'и инъекций) | `ServiceThree`, `ActivityOne/Two`, `ProviderOne` | MSTG-CODE-8 |
