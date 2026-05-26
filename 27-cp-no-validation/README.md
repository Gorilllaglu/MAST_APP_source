# 27 — ContentProvider, экспортированный без валидации

## APK после сборки

```bash
./gradlew :micro:27-cp-no-validation:assembleDebug
```

Файл: `micro/27-cp-no-validation/build/outputs/apk/debug/27-cp-no-validation-debug.apk`

## Что заложено в приложение

`UnsafeProvider` объявлен `<provider exported="true">` без
`android:readPermission` / `android:writePermission` / `android:permission`.
В одном классе сразу пять разных классов уязвимостей.

1. **SQL injection в `query()`**. `selection` от вызывающего
   подставляется в `rawQuery("SELECT ... WHERE $selection ...")`
   через конкатенацию строк. Любое стороннее приложение может
   передать `selection = "1=1 UNION SELECT ..."` и получить произвольные
   данные.
2. **Insert без проверки**. `insert()` напрямую делает
   `db.insert("users", null, values)` с переданными `ContentValues`.
   Вызывающий может писать в любую колонку, включая `password`.
3. **SQL injection в `update()`**. `selection` снова конкатенируется
   в WHERE. Плюс самим вызовом `update` любой может модифицировать
   чужие данные.
4. **SQL injection в `delete()`**. То же самое для удаления.
5. **Path traversal в `openFile()`**. `uri.lastPathSegment` напрямую
   передаётся в `File(filesDir, name)` без canonicalize. Атака:
   `content://com.masttest.vuln27.unsafe/../shared_prefs/secrets.xml` —
   и приложение отдаст приватный файл.

Это сборная солянка ContentProvider-уязвимостей. В реальных
приложениях встречается одно из них (а не все одновременно), но
в test app собрано всё ради покрытия rule family.

Расположение: `src/main/kotlin/com/masttest/vuln27/UnsafeProvider.kt`.

## Что должен найти сканер

Минимально достаточный отчёт — **пять-шесть** finding'ов:

1. Сам факт `<provider exported="true">` без permission — категория
   R009.
2. `query()` строит SQL через конкатенацию `selection` — SQL injection
   (R094, R147, R148, R157).
3. `insert()` принимает произвольные `ContentValues` без валидации —
   R140, R141, R154.
4. `update()` строит SQL через `selection` + позволяет произвольный
   update — R142, R155.
5. `delete()` строит SQL через `selection` + позволяет произвольный
   delete — R156.
6. `openFile()` собирает File из `uri.lastPathSegment` без canonicalize —
   R105 (доступ к произвольному файлу через ContentProvider).

Хороший сканер, обнаружив `exported="true"` без permission, должен
дальше проверять каждый из методов CP отдельно. Слабый — только
сообщит про сам экспорт.

**Чего сканер не должен делать**:

- Не должен флажить `MainActivity` (лаунчер).
- Не должен срабатывать на сам `SQLiteOpenHelper.rawQuery` —
  уязвимость в **источнике** аргументов, а не в API.
