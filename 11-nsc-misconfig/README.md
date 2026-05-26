# 11 — Небезопасная Network Security Config

## APK после сборки

```bash
./gradlew :micro:11-nsc-misconfig:assembleDebug
```

Файл: `micro/11-nsc-misconfig/build/outputs/apk/debug/11-nsc-misconfig-debug.apk`

## Что заложено в приложение

В манифесте на `<application>` указано
`android:networkSecurityConfig="@xml/network_security_config"`,
а сам этот XML-файл (`src/main/res/xml/network_security_config.xml`)
содержит сразу три ослабления политики защиты сетевого канала:

1. **Доверие к пользовательским CA**: в `<base-config>` среди
   `<trust-anchors>` явно указан `<certificates src="user"/>`. Это
   значит, что приложение доверяет любому корневому сертификату,
   который пользователь добавил в системное хранилище. Любой
   «корпоративный» прокси с собственным CA, попавший на устройство
   (через MDM, через социальную инженерию), сможет прозрачно читать
   и подменять весь TLS-трафик приложения.
2. **Глобальный cleartext в `<base-config>`**: атрибут
   `cleartextTrafficPermitted="true"` разрешает приложению ходить
   по обычному HTTP без TLS на любые домены.
3. **Явный cleartext для домена**: дополнительно `<domain-config>`
   с `cleartextTrafficPermitted="true"` для `api.legacy.example.com`
   и всех его поддоменов. Даже если в base-config исправят cleartext
   — этот блок продолжит разрешать HTTP для конкретного домена.

Расположение всех слабостей: `src/main/res/xml/network_security_config.xml`.

## Что должен найти сканер

Минимально достаточный отчёт — **три** finding'а, по одному на каждое
ослабление:

1. В `<base-config>` указано доверие к user-CA — приложение принимает
   любые сертификаты, добавленные пользователем. Категория —
   небезопасная конфигурация сетевого взаимодействия (R013).
2. В `<base-config>` стоит `cleartextTrafficPermitted="true"` — это
   глобальное разрешение HTTP. Та же категория.
3. `<domain-config>` для `api.legacy.example.com` тоже разрешает
   cleartext. Та же категория.

Хороший сканер показывает все три отдельно. Допустимо если он
сворачивает п.2 и п.3 в один пункт «cleartext разрешён» с приложенным
списком contexts. Но **trust user CA — обязательно отдельный finding**:
это другой класс ошибки и другой fix.

**Чего сканер не должен делать**:

- Флажить `MainActivity` как exported (это лаунчер).
- Флажить системный `<certificates src="system"/>` — это legitимный
  trust-anchor, без него никакой TLS работать не будет.
