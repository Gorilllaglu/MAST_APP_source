# 38 — Слабые RASP-проверки

## APK после сборки

```bash
./gradlew :micro:38-weak-rasp-checks:assembleDebug
```

Файл: `micro/38-weak-rasp-checks/build/outputs/apk/debug/38-weak-rasp-checks-debug.apk`

## Что заложено в приложение

В отличие от app 32 (`32-no-protections-baseline`, где защит **нет
вообще**) — здесь все типичные RASP-проверки **есть**, но каждая
реализована заведомо плохо и обходится за минуту.

Сканер должен на этом наборе уметь **различать два класса**:

- «**защита отсутствует**» (R118 / R119 / R120 / R121 / R122 / R123) →
  должно срабатывать на app 32, **не** должно — на app 38;
- «**защита есть, но слабая**» (R116 / R117 + слабые варианты
  R120/R121/R122/R123) → должно срабатывать на app 38, **не** должно
  — на app 32.

Если правила сканера не различают эти два класса — он либо будет
давать ложноположительные срабатывания R118/R119 на app 38 (видеть
проверки и всё равно репортить «отсутствует»), либо пропустит app 38
целиком (увидеть, что что-то есть, и успокоиться).

### Слабые проверки в `WeakChecks.kt`

| Метод | Реализация (слабая) | Категория | Как обходится |
|---|---|---|---|
| `weakRootSinglePath()` | `File("/system/app/Superuser.apk").exists()` | **R116** | Magisk вообще не создаёт этот файл; SuperSU давно его не пишет |
| `weakRootByTestKeys()` | `Build.TAGS?.contains("test-keys")` | **R116** | `test-keys` — про factory-image AOSP, не про root. Common-mistake реализация |
| `weakEmulatorFingerprintOnly()` | `Build.FINGERPRINT.contains("generic")` | **R117** | Genymotion с custom fingerprint обходит; реальные rebranded firmware ложно срабатывают |
| `weakEmulatorHardwareOnly()` | `Build.HARDWARE == "goldfish" \|\| "ranchu"` | **R117** | Bluestacks/MEmu/LDPlayer/Genymotion имеют свой `Build.HARDWARE` |
| `weakDebuggerFlagOnly()` | `applicationInfo.flags and FLAG_DEBUGGABLE != 0` | **R122** (слабый вариант) | Frida через `ptrace` подключается без манифестного флага |
| `weakIntegrityHasSignatureOnly()` | `getPackageInfo(GET_SIGNATURES).signatures.isNotEmpty()` | **R123** (слабый вариант) | Любой re-signed APK с любым подписывающим пройдёт — нет сверки SHA-256 с known-good |
| `weakFridaSinglePath()` | `File("/data/local/tmp/frida-server").exists()` | **R120** (слабый вариант) | Frida-server можно переименовать или запустить frida-gadget из памяти |
| `weakScreenLockLegacy()` | `KeyguardManager.isKeyguardSecure` (deprecated с API 23) | **R121** (слабый вариант) | API устарел, не различает биометрию/PIN корректно. Правильно — `isDeviceSecure()` (API 23+) |

Все вызываются из `WeakChecks.runAll(ctx)` в `MainActivity.onCreate`,
результаты складываются в `data class Report` (чтобы R8/DCE не
выкинул их даже при `isMinifyEnabled=true`).

Расположение: `src/main/kotlin/com/masttest/vuln38/WeakChecks.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **восемь** finding'ов:

### R116 — слабая root-detection

1. `weakRootSinglePath` — проверка единственного path без вариативности.
2. `weakRootByTestKeys` — не root-check, common mistake.

### R117 — слабая emulator-detection

3. `weakEmulatorFingerprintOnly` — один Build field.
4. `weakEmulatorHardwareOnly` — только AOSP-эмулятор.

### R120, R121, R122, R123 — слабые варианты

5. `weakDebuggerFlagOnly` — только manifest-флаг, не runtime detection.
6. `weakIntegrityHasSignatureOnly` — нет SHA-256 сверки.
7. `weakFridaSinglePath` — один путь.
8. `weakScreenLockLegacy` — deprecated API.

Хороший сканер для каждого пункта объясняет **почему конкретно** реализация слабая (например: «root check работает только с устаревшим SuperSU; не покрывает Magisk → нужно добавить `which su`, /sbin/su, magisk-mount-check»). Слабый сканер просто срепортит «root check present» и успокоится.

## Чего сканер НЕ должен делать

- Не должен флажить app 38 как R118 / R119 («защиты нет») — защиты **есть**, и в DEX это видно через методы `weakRoot*`, `weakEmulator*`, `weakDebugger*`, `weakIntegrity*`, `weakFrida*`, `weakScreenLock*`.
- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на сам `File.exists()` — это легитимный API.
  Слабость — в **сочетании** «название метода `*root*` / `*frida*`» + «единственный путь».
