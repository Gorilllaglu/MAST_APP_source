# 32 — Приложение без защитных проверок (baseline)

## APK после сборки

```bash
./gradlew :micro:32-no-protections-baseline:assembleDebug
```

Файл: `micro/32-no-protections-baseline/build/outputs/apk/debug/32-no-protections-baseline-debug.apk`

## Что заложено в приложение

Это намеренно **«vanilla»** Android-приложение. Оно состоит из одной
тривиальной `MainActivity`, которая ничего не делает, и больше ничего.

В нём **отсутствуют** все типичные защитные проверки, которые
полагается иметь production-приложению с чувствительной функциональностью:

- **root detection** — нет вызовов `RootBeer`, `SafetyNetClient`,
  ручных проверок `su`, `Magisk`, `Xposed`, файлов `/system/app/Superuser.apk`.
- **emulator detection** — нет проверок `Build.FINGERPRINT`/`Build.MODEL`/
  `Build.HARDWARE`/`Build.PRODUCT` против эмуляторных значений
  (`generic`, `goldfish`, `ranchu`, `sdk_gphone`, и пр.).
- **debugger detection** — нет вызовов `Debug.isDebuggerConnected()`
  или проверки `applicationInfo.flags & FLAG_DEBUGGABLE`.
- **Frida detection** — нет поиска `frida-server`, файлов
  `/data/local/tmp/frida-*`, hooked-классов через попытку их найти.
- **app integrity check** — нет вызовов `PackageManager.getPackageInfo`
  с `GET_SIGNATURES` / `GET_SIGNING_CERTIFICATES` и последующей сверки
  signature-hash'а с захардкоженным значением.
- **screen-lock check** — нет вызовов
  `KeyguardManager.isDeviceSecure()` / `isKeyguardSecure()` для
  проверки, что устройство защищено PIN'ом / биометрией.

В сумме это «приложение, которое спокойно работает на рутованном
эмуляторе с подключённым Frida». Категории — **R118, R119, R120,
R121, R122, R123** (именно «отсутствует»). Слабые реализации
(R116 — weak root, R117 — weak emulator) для этого app **не
применимы** и покрываются отдельно в app 38 `38-weak-rasp-checks`.

## Что должен найти сканер

Минимально достаточный отчёт — **шесть** finding'ов, по одному на
каждое отсутствующее семейство защит. Все — типа «protection missing»:

1. Отсутствует root detection (**R118**).
2. Отсутствует emulator detection (**R119**).
3. Отсутствует debugger detection (**R122**).
4. Отсутствует Frida detection (**R120**).
5. Отсутствует integrity check / signature verification (**R123**).
6. Отсутствует screen-lock check (**R121**).

R116 (weak root) и R117 (weak emulator) этому app **не подходят** —
здесь нет ни одной проверки, поэтому правильная категория «отсутствует»
(R118/R119), а не «слабая реализация». Если сканер на app 32 даёт
R116 или R117 — это ложный класс finding'а (правильно для app 38).

Это нестандартный класс правил: сканер ищет **отсутствие**
определённых API-вызовов / зависимостей. Эвристики:

- нет import'ов `com.scottyab.rootbeer.RootBeer`, `com.google.android.gms.safetynet.SafetyNet`;
- нет ссылок на `Debug.isDebuggerConnected`, `Build.FINGERPRINT`,
  `KeyguardManager.isDeviceSecure`, `PackageManager.GET_SIGNATURES`;
- нет string-references вроде `"frida"`, `"magisk"`, `"/system/app/Superuser"`.

Хороший сканер выдаёт informational/medium-severity findings и
предлагает добавить соответствующие проверки. Слабый — пропускает
(потому что искать отсутствие сложнее, чем наличие).

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на отсутствие защит, которые не нужны
  в этом классе приложений (этот тестовый app намеренно покрывает
  максимальный набор — реальные приложения могут не иметь, например,
  Frida-detection и это нормально для не-fintech).
