# Компиляция APK Not Gammon → NotNode

**Цель:** Собрать debug APK проекта Not Gammon и сохранить под именем `NotNode.apk` в `C:\ApkExport`.

---

## Предварительные требования

Проверить одной командой:

```bash
# 1. JAVA_HOME — должен указывать на JDK 17+
echo "JAVA_HOME: $JAVA_HOME"
java -version 2>&1

# 2. ANDROID_HOME — SDK с platform 34+
echo "ANDROID_HOME: $ANDROID_HOME"
ls "$ANDROID_HOME/platforms/" 2>/dev/null

# 3. Gradle — глобально или wrapper
gradle --version 2>/dev/null | head -1
```

Если что-то отсутствует — явные пути:

| Переменная | Типичный путь на Windows |
|-----------|-------------------------|
| `JAVA_HOME` | `C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot` |
| `ANDROID_HOME` | `C:\Users\yusya\AppData\Local\Android\Sdk` |

> **Важно:** `JAVA_HOME` в git-bash/MSYS2 должен быть в Windows-стиле (`C:\Program Files\...`), НЕ в Unix-стиле (`/c/Program Files/...`). Gradle его не понимает.

---

## Сборка

```bash
# Экспортировать переменные (Windows-стиль для JAVA_HOME!)
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
export ANDROID_HOME="C:\Users\yusya\AppData\Local\Android\Sdk"

# Перейти в корень проекта
cd /c/Temp/n2

# Запустить сборку
gradle assembleDebug
```

**Успешный финал:**
```
BUILD SUCCESSFUL in 1m 58s
40 actionable tasks: 10 executed, 30 up-to-date
```

**APK появится:**
```
C:\Temp\n2\app\build\outputs\apk\debug\app-debug.apk
```

---

## Копирование с переименованием

```bash
mkdir -p /c/ApkExport
cp /c/Temp/n2/app/build/outputs/apk/debug/app-debug.apk /c/ApkExport/NotNode.apk
ls -lh /c/ApkExport/NotNode.apk
```

---

## Типичные ошибки и решения

### Ошибка 1: `JAVA_HOME is set to an invalid directory`
```
ERROR: JAVA_HOME is set to an invalid directory: /c/Program Files/...
```
**Причина:** Gradle не понимает MSYS2/Unix-пути в JAVA_HOME.  
**Решение:** export JAVA_HOME в Windows-стиле:
```bash
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
```

### Ошибка 2: `Unresolved reference 'Foo'`
```
e: file:///.../GameViewModel.kt:43:25 Unresolved reference 'DuressPinManager'.
```
**Причина:** Неправильный import (package).  
**Решение:** Проверить actual package класса:
```bash
grep -rn "class DuressPinManager\|object DuressPinManager" app/src/main
```
Поправить import в файле-потребителе.

### Ошибка 3: `Cannot access 'X': it is private`
```
e: file:///.../GameCryptoWizard.kt:530:47 Cannot access 'smpOnionAddress': it is private
```
**Причина:** Свойство объявлено с `private set` или как `private val/var`, а внешний код пытается писать/читать напрямую.  
**Решение:** Найти публичный setter-метод:
```bash
grep -n "fun updateSmpOnionAddress" app/src/main
```
Заменить `viewModel.smpOnionAddress = val` на `viewModel.updateSmpOnionAddress(val)`.

### Ошибка 4: Сборка падает с `OutOfMemoryError`
**Решение:** Добавить в `gradle.properties`:
```properties
org.gradle.jvmargs=-Xmx4096m
```

### Ошибка 5: `SDK location not found`
**Решение:** Создать `local.properties` в корне проекта:
```properties
sdk.dir=C\:\\Users\\yusya\\AppData\\Local\\Android\\Sdk
```

---

## Полный скрипт (одна команда)

Скопировать и выполнить целиком:

```bash
export JAVA_HOME="C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
export ANDROID_HOME="C:\Users\yusya\AppData\Local\Android\Sdk"
cd /c/Temp/n2 && \
gradle assembleDebug && \
mkdir -p /c/ApkExport && \
cp app/build/outputs/apk/debug/app-debug.apk /c/ApkExport/NotNode.apk && \
echo "✅ NotNode.apk готов — $(ls -lh /c/ApkExport/NotNode.apk | awk '{print $5}')"
```

---

## Что ещё нужно знать

- **Размер APK:** ~98 MB (из них ~65 MB — Tor + Xray бинарники под 3 архитектуры)
- **Application ID:** `com.notgammon.app`
- **Debug-подпись:** Android SDK debug keystore (не для распространения)
- **Release-сборка:** требует `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_PASSWORD` в env
- **Gradle version:** 9.5.1 (системный, wrapper отсутствует)
