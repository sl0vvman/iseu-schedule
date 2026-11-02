# ISEU Android App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Собрать лёгкое монохромное Android-приложение `ISEU`, которое показывает расписание групп и преподавателей с `rsp.iseu.by` напрямую, с офлайн-кэшем и без сервера.

**Architecture:** Один модуль `:app` на Kotlin + Jetpack Compose. Слой данных: OkHttp-сессия, повторяющая ASP.NET WebForms postback-каскад, Jsoup-парсеры, Room-кэш и DataStore для выбранной цели и темы. UI: MVVM, `StateFlow`, Navigation Compose. Ручной DI через `IseuApp`.

**Tech Stack:** Kotlin 2.1, Jetpack Compose (BOM 2024.12.01), Material 3, Navigation Compose, OkHttp 4.12, Jsoup 1.18, Room 2.6.1 + KSP, DataStore 1.1.1, kotlinx.serialization 1.7.3, Coroutines 1.9. minSdk 24, compile/target 35.

**Spec:** `docs/superpowers/specs/2026-11-02-iseu-android-app-design.md`

## Global Constraints

- Пакет `by.iseu.schedule`, имя приложения `ISEU`, UI на русском.
- `minSdk 24`, `compileSdk 35`, `targetSdk 35`, JDK 17.
- Никакого бэкенда, аккаунтов, аналитики, уведомлений и фоновой работы.
- Cleartext HTTP разрешён только для `rsp.iseu.by`.
- Монохромная палитра, светлая и тёмная темы, без акцентных цветов.
- Все сетевые запросы: таймаут, максимум одна повторная попытка, без бесконечных циклов.
- Сайт — единственный источник; `value` факультетов, групп и преподавателей меняются каждый семестр и не хардкодятся.
- Сборка и проверка — локально, без Android Studio.

## Review Focus

1. Сохранённая группа/преподаватель из прошлого семестра: приложение должно перезагрузить каталог и попросить выбрать заново, а не падать и не показывать пустоту.
2. Неделя без пар (`row-empty`): это пустое состояние «Пар нет», а не ошибка.
3. Нет сети или сайт недоступен: показывается кэш с пометкой об устаревании, а не пустой экран.
4. `row-spanned` и строки без атрибута `class`: парсер не должен терять пары и даты.
5. Маркер подгруппы приходит и в `cell-subgroup`, и как `[Nп/гр]` в тексте: подгруппа не должна дублироваться, а карточка должна визуально отличаться без опоры на цвет.

---

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle.properties
gradle/libs.versions.toml
gradle/wrapper/...
gradlew, gradlew.bat
scripts/env.sh
local.properties                 # не коммитится
app/build.gradle.kts
app/proguard-rules.pro
app/src/main/AndroidManifest.xml
app/src/main/res/values/strings.xml
app/src/main/res/values/themes.xml
app/src/main/res/values-night/themes.xml
app/src/main/res/xml/network_security_config.xml
app/src/main/res/drawable/ic_launcher.xml
app/src/main/java/by/iseu/schedule/IseuApp.kt
app/src/main/java/by/iseu/schedule/MainActivity.kt
app/src/main/java/by/iseu/schedule/data/model/Models.kt
app/src/main/java/by/iseu/schedule/data/parser/TextParsing.kt
app/src/main/java/by/iseu/schedule/data/parser/ScheduleParser.kt
app/src/main/java/by/iseu/schedule/data/parser/OptionParser.kt
app/src/main/java/by/iseu/schedule/data/parser/WeekSelector.kt
app/src/main/java/by/iseu/schedule/data/network/MemoryCookieJar.kt
app/src/main/java/by/iseu/schedule/data/network/AspNetSession.kt
app/src/main/java/by/iseu/schedule/data/db/Entities.kt
app/src/main/java/by/iseu/schedule/data/db/Daos.kt
app/src/main/java/by/iseu/schedule/data/db/AppDatabase.kt
app/src/main/java/by/iseu/schedule/data/prefs/SelectionStore.kt
app/src/main/java/by/iseu/schedule/data/repo/ScheduleRepository.kt
app/src/main/java/by/iseu/schedule/data/repo/CachePolicy.kt
app/src/main/java/by/iseu/schedule/ui/theme/Theme.kt
app/src/main/java/by/iseu/schedule/ui/common/Components.kt
app/src/main/java/by/iseu/schedule/ui/select/SelectViewModel.kt
app/src/main/java/by/iseu/schedule/ui/select/SelectScreen.kt
app/src/main/java/by/iseu/schedule/ui/schedule/ScheduleViewModel.kt
app/src/main/java/by/iseu/schedule/ui/schedule/ScheduleScreen.kt
app/src/main/java/by/iseu/schedule/ui/settings/SettingsScreen.kt
app/src/test/resources/fixtures/group_week.html
app/src/test/resources/fixtures/teacher_week.html
app/src/test/resources/fixtures/teachers_page.html
app/src/test/java/by/iseu/schedule/data/parser/TextParsingTest.kt
app/src/test/java/by/iseu/schedule/data/parser/GroupScheduleParserTest.kt
app/src/test/java/by/iseu/schedule/data/parser/TeacherScheduleParserTest.kt
app/src/test/java/by/iseu/schedule/data/parser/OptionParserTest.kt
app/src/test/java/by/iseu/schedule/data/parser/WeekSelectorTest.kt
app/src/test/java/by/iseu/schedule/data/network/AspNetSessionTest.kt
app/src/test/java/by/iseu/schedule/data/network/LiveFetchTest.kt
app/src/test/java/by/iseu/schedule/data/repo/CachePolicyTest.kt
app/src/test/java/by/iseu/schedule/ui/select/SelectViewModelTest.kt
app/src/test/java/by/iseu/schedule/ui/schedule/ScheduleViewModelTest.kt
README.md
```

---

### Task 0: Toolchain

**Files:**
- Create: `scripts/env.sh`

**Interfaces:**
- Produces: working `java` (17), `sdkmanager`, `gradle`, `adb` on PATH via `source scripts/env.sh`.

- [ ] **Step 1: Check architecture**

Run: `uname -m`
Expected: `x86_64` (для других архитектур взять соответствующий архив Adoptium и cmdline-tools).

- [ ] **Step 2: Install JDK 17**

Run:
```bash
mkdir -p ~/.local/opt/jdk17
curl -L -o /tmp/jdk17.tar.gz "https://api.adoptium.net/v3/binary/latest/17/ga/linux/x64/jdk/hotspot/normal/eclipse"
tar -xzf /tmp/jdk17.tar.gz -C ~/.local/opt/jdk17 --strip-components=1
~/.local/opt/jdk17/bin/java -version
```
Expected: `openjdk version "17...`.

- [ ] **Step 3: Install Android command-line tools and SDK 35**

Run:
```bash
mkdir -p ~/Android/Sdk/cmdline-tools
curl -L -o /tmp/cmdline-tools.zip "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip"
unzip -q /tmp/cmdline-tools.zip -d ~/Android/Sdk/cmdline-tools
mv ~/Android/Sdk/cmdline-tools/cmdline-tools ~/Android/Sdk/cmdline-tools/latest
export JAVA_HOME=$HOME/.local/opt/jdk17
export ANDROID_HOME=$HOME/Android/Sdk
yes | ~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --licenses
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-35" "build-tools;35.0.0"
~/Android/Sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root="$ANDROID_HOME" --list_installed
```
Expected: в списке есть `platforms;android-35` и `build-tools;35.0.0`.

- [ ] **Step 4: Install Gradle 8.11.1**

Run:
```bash
mkdir -p ~/.local/opt
curl -L -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-8.11.1-bin.zip"
unzip -q /tmp/gradle.zip -d ~/.local/opt
~/.local/opt/gradle-8.11.1/bin/gradle --version
```
Expected: `Gradle 8.11.1`, `JVM 17`.

- [ ] **Step 5: Write `scripts/env.sh`**

```bash
#!/usr/bin/env bash
export JAVA_HOME="$HOME/.local/opt/jdk17"
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$ANDROID_HOME"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$HOME/.local/opt/gradle-8.11.1/bin:$PATH"
```

Run: `chmod +x scripts/env.sh && source scripts/env.sh && java -version && gradle --version`
Expected: Java 17 and Gradle 8.11.1.

---

### Task 1: Project Scaffold

**Files:**
- Create: `.gitignore`, `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/res/values/strings.xml`, `app/src/main/res/values/themes.xml`, `app/src/main/res/values-night/themes.xml`, `app/src/main/res/xml/network_security_config.xml`, `app/src/main/java/by/iseu/schedule/MainActivity.kt`
- Test: build only

**Interfaces:**
- Produces: a buildable `:app` module; `MainActivity` renders a placeholder `Text("ISEU")`.

- [ ] **Step 1: Initialize git and ignore build outputs**

Run:
```bash
cd /home/sl0vvman/Projects/ISEURaspis
git init
```
`.gitignore`:
```
.gradle/
build/
app/build/
local.properties
.idea/
*.iml
.DS_Store
captures/
*.jks
*.keystore
```

- [ ] **Step 2: Create Gradle files**

`settings.gradle.kts`:
```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "ISEU"
include(":app")
```

`gradle/libs.versions.toml`:
```toml
[versions]
agp = "8.7.3"
kotlin = "2.1.0"
ksp = "2.1.0-1.0.29"
composeBom = "2024.12.01"
coreKtx = "1.15.0"
activityCompose = "1.9.3"
lifecycle = "2.8.7"
navigation = "2.8.5"
room = "2.7.2"
datastore = "1.1.1"
okhttp = "4.12.0"
jsoup = "1.18.3"
serialization = "1.7.3"
coroutines = "1.9.0"
junit = "4.13.2"

[libraries]
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "activityCompose" }
androidx-lifecycle-runtime-compose = { module = "androidx.lifecycle:lifecycle-runtime-compose", version.ref = "lifecycle" }
androidx-lifecycle-viewmodel-compose = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
androidx-navigation-compose = { module = "androidx.navigation:navigation-compose", version.ref = "navigation" }
compose-bom = { module = "androidx.compose:compose-bom", version.ref = "composeBom" }
compose-ui = { module = "androidx.compose.ui:ui" }
compose-ui-tooling = { module = "androidx.compose.ui:ui-tooling" }
compose-ui-tooling-preview = { module = "androidx.compose.ui:ui-tooling-preview" }
compose-material3 = { module = "androidx.compose.material3:material3" }
compose-material-icons-core = { module = "androidx.compose.material:material-icons-core" }
room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
room-ktx = { module = "androidx.room:room-ktx", version.ref = "room" }
room-compiler = { module = "androidx.room:room-compiler", version.ref = "room" }
datastore-preferences = { module = "androidx.datastore:datastore-preferences", version.ref = "datastore" }
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
jsoup = { module = "org.jsoup:jsoup", version.ref = "jsoup" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
junit = { module = "junit:junit", version.ref = "junit" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

Root `build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
```

`gradle.properties`:
```
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

`app/build.gradle.kts`:
```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "by.iseu.schedule"
    compileSdk = 35

    defaultConfig {
        applicationId = "by.iseu.schedule"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.kotlinx.coroutines.test)
}
```

`app/proguard-rules.pro`:
```
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class by.iseu.schedule.data.model.** { *; }
```

- [ ] **Step 3: Create resources and manifest**

`app/src/main/res/values/strings.xml`:
```xml
<resources>
    <string name="app_name">ISEU</string>
</resources>
```

`app/src/main/res/values/themes.xml`:
```xml
<resources>
    <style name="Theme.ISEU" parent="android:Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@android:color/white</item>
        <item name="android:statusBarColor">@android:color/transparent</item>
        <item name="android:navigationBarColor">@android:color/transparent</item>
    </style>
</resources>
```

`app/src/main/res/values-night/themes.xml`: same style with `@android:color/black` window background.

`app/src/main/res/xml/network_security_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false" />
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">rsp.iseu.by</domain>
    </domain-config>
</network-security-config>
```

`app/src/main/AndroidManifest.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <application
        android:name=".IseuApp"
        android:allowBackup="true"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:networkSecurityConfig="@xml/network_security_config"
        android:supportsRtl="true"
        android:theme="@style/Theme.ISEU">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

- [ ] **Step 4: Add placeholder Activity and icon**

`app/src/main/java/by/iseu/schedule/MainActivity.kt`:
```kotlin
package by.iseu.schedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface { Text("ISEU") }
            }
        }
    }
}
```

`app/src/main/res/drawable/ic_launcher.xml`:
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#0A0A0A" android:pathData="M0,0h108v108h-108z" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="4" android:fillColor="#00000000"
        android:pathData="M28,32h52v48h-52z" />
    <path android:strokeColor="#FFFFFF" android:strokeWidth="4"
        android:pathData="M28,44h52M42,24v16M66,24v16" />
    <path android:fillColor="#FFFFFF" android:pathData="M38,54h8v8h-8zM62,54h8v8h-8zM38,68h8v8h-8z" />
</vector>
```

- [ ] **Step 5: Generate wrapper and build**

Run:
```bash
source scripts/env.sh
echo "sdk.dir=$ANDROID_HOME" > local.properties
gradle wrapper --gradle-version 8.11.1
./gradlew :app:assembleDebug
```
Expected: `BUILD SUCCESSFUL`, APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 6: Commit**

```bash
git add .
git commit -m "chore: scaffold Android Compose project"
```

---

### Task 2: Models and Pure Parsers

**Files:**
- Create: `app/src/main/java/by/iseu/schedule/data/model/Models.kt`
- Create: `app/src/main/java/by/iseu/schedule/data/parser/TextParsing.kt`
- Create: `app/src/main/java/by/iseu/schedule/data/parser/ScheduleParser.kt`
- Create: `app/src/main/java/by/iseu/schedule/data/parser/OptionParser.kt`
- Create: `app/src/main/java/by/iseu/schedule/data/parser/WeekSelector.kt`
- Copy fixtures: `/tmp/final.html` → `app/src/test/resources/fixtures/group_week.html`; `/tmp/teachers_final.html` → `teacher_week.html`; `/tmp/teachers_initial.html` → `teachers_page.html`
- Test: `TextParsingTest.kt`, `GroupScheduleParserTest.kt`, `TeacherScheduleParserTest.kt`, `OptionParserTest.kt`, `WeekSelectorTest.kt`

**Interfaces:**
- Produces:
```kotlin
enum class Mode { GROUP, TEACHER }
enum class LessonType(val label: String) { LECTURE("лекция"), PRACTICE("практика"), LAB("лабораторная"), OTHER("занятие") }
data class Option(val value: String, val label: String)
data class Lesson(
    val dayOfWeek: String,
    val date: LocalDate,
    val start: LocalTime,
    val end: LocalTime,
    val subgroup: Int?,
    val discipline: String,
    val type: LessonType,
    val staff: String?,
    val contingent: String?,
    val topic: String?,
    val audience: String?,
)
object TextParsing {
    fun parseDate(raw: String): LocalDate?
    fun parseDayAndDate(raw: String): Pair<String, LocalDate>?
    fun parseTimeRange(raw: String): Pair<LocalTime, LocalTime>?
    fun parseDiscipline(raw: String): Pair<String, LessonType>
    fun parseSubgroup(raw: String): Int?
    fun cleanOrNull(raw: String?): String?
}
object ScheduleParser {
    fun parseGroupTable(html: String): List<Lesson>
    fun parseTeacherTable(html: String): List<Lesson>
    fun parseMessage(html: String): String?
}
object OptionParser {
    fun parseOptions(html: String, selectName: String): List<Option>
}
object WeekSelector {
    fun mondayOf(date: LocalDate): LocalDate
    fun parseOptionDate(label: String): LocalDate?
    fun selectWeek(weeks: List<Option>, targetMonday: LocalDate): Option?
}
```

- [ ] **Step 1: Write failing text-parsing tests**

`TextParsingTest.kt` asserts:
```kotlin
assertEquals(LocalDate.of(2026, 11, 2), TextParsing.parseDate("02.11.2026"))
assertEquals("Понедельник" to LocalDate.of(2026, 11, 2), TextParsing.parseDayAndDate("Понедельник 02.11.2026"))
assertEquals(LocalTime.of(8, 30) to LocalTime.of(9, 50), TextParsing.parseTimeRange("08:30-09:50"))
assertEquals("Физика" to LessonType.LECTURE, TextParsing.parseDiscipline("лек. Физика"))
assertEquals("Физика" to LessonType.PRACTICE, TextParsing.parseDiscipline("практ. зан. Физика"))
assertEquals("Электричество и магнетизм" to LessonType.LAB, TextParsing.parseDiscipline("лаб. \"Электричество и магнетизм\""))
assertEquals("Физическая культура" to LessonType.OTHER, TextParsing.parseDiscipline("Физическая культура"))
assertEquals(1, TextParsing.parseSubgroup("[1п/гр]"))
assertNull(TextParsing.parseSubgroup(""))
assertNull(TextParsing.cleanOrNull("ауд. ,"))
assertNull(TextParsing.cleanOrNull("   "))
assertNull(TextParsing.parseTimeRange("нет пар"))
```

- [ ] **Step 2: Run tests, expect failure**

Run: `source scripts/env.sh && ./gradlew :app:testDebugUnitTest --tests '*TextParsingTest*'`
Expected: FAIL because `TextParsing` is missing.

- [ ] **Step 3: Implement `Models.kt` and `TextParsing.kt`**

Implement exactly the signatures above. Discipline parsing: trim; strip surrounding quotes after the type prefix; prefixes `лек.`, `практ. зан.`, `лаб.`, `сем.`; anything else is `OTHER` with the original text. Subgroup: regex `\[?\s*(\d)\s*п/гр\s*\]?` or a lone digit. `cleanOrNull`: collapse whitespace; return null for blank or `ауд. ,`.

- [ ] **Step 4: Write failing group-parser test**

`GroupScheduleParserTest.kt` reads `fixtures/group_week.html` and asserts:
```kotlin
val lessons = ScheduleParser.parseGroupTable(html)
assertTrue(lessons.isNotEmpty())
val first = lessons.first()
assertEquals("Понедельник", first.dayOfWeek)
assertEquals(LocalDate.of(2026, 11, 2), first.date)
assertEquals(LocalTime.of(8, 30), first.start)
assertEquals(LocalTime.of(9, 50), first.end)
assertEquals("Физика", first.discipline)
assertEquals(LessonType.LECTURE, first.type)
assertTrue(first.staff!!.contains("Луцевич"))
assertEquals("ауд. 103, К2", first.audience)
assertNull(first.subgroup)
assertEquals(LocalDate.of(2026, 11, 2), lessons[1].date) // row-spanned inheritance
val pe = lessons.first { it.discipline == "Физическая культура" }
assertNull(pe.staff)
assertNull(pe.audience)
```
Also parse a synthetic fragment with `cell-subgroup` = `2` and assert `subgroup == 2`, and a `<tr>` without `class` is still parsed.
Also assert:
```kotlin
val empty = ScheduleParser.parseGroupTable("""<table id="TT"><tr class="row row-empty"><td class="cell-date"><span class="day">Понедельник</span> <span class="date">02.11.2026</span></td><td></td></tr></table>""")
assertTrue(empty.isEmpty()) // пустая неделя — не ошибка
assertEquals("Физика" to LessonType.PRACTICE, TextParsing.parseDiscipline("практ. зан. Физика [1п/гр]"))
assertEquals(1, TextParsing.parseSubgroup("практ. зан. Физика [1п/гр]")) // маркер рисуется один раз
```

- [ ] **Step 5: Run test, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*GroupScheduleParserTest*'`
Expected: FAIL.

- [ ] **Step 6: Implement `ScheduleParser.parseGroupTable`**

Iterate `table#TT` rows in document order; skip `row-header` and `row-separator`; track `currentDay/currentDate` from `td.cell-date`; for each row with a time cell, read `cell-time`, `cell-subgroup`, `cell-discipline`, `cell-staff`, `cell-auditory`; inherit the date; skip rows with unparseable time. Use `TextParsing` for all fields; extract a subgroup marker from the discipline as a fallback.

- [ ] **Step 7: Write failing teacher-parser test**

`TeacherScheduleParserTest.kt` reads `fixtures/teacher_week.html` and asserts:
```kotlin
val lessons = ScheduleParser.parseTeacherTable(html)
assertTrue(lessons.isNotEmpty())
val first = lessons.first()
assertEquals(LocalDate.of(2026, 11, 2), first.date)
assertTrue(first.contingent!!.contains("ФМОС"))
assertEquals("Физика", first.discipline)
assertTrue(first.audience!!.contains("ауд.103"))
val lab = lessons.first { it.type == LessonType.LAB }
assertEquals(1, lab.subgroup)
assertNull(lab.topic) // marker removed
```

- [ ] **Step 8: Run test, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*TeacherScheduleParserTest*'`
Expected: FAIL.

- [ ] **Step 9: Implement `ScheduleParser.parseTeacherTable` and `parseMessage`**

Teacher table uses the same CSS classes but different semantics: `td.cell-subgroup` is the contingent, `td.cell-staff` is the topic/subgroup marker, and the time cell has no class (it is the cell right after `cell-date`, or the first cell when the date is inherited). Parse by position after the date cell. Remove the `[Nп/гр]` marker from the topic. `parseMessage` returns trimmed text of `[id=lMessage]` or null.

- [ ] **Step 10: Write failing option and week tests**

`OptionParserTest.kt`:
```kotlin
val teachers = OptionParser.parseOptions(readFixture("teachers_page.html"), "DropDownList1")
assertEquals(241, teachers.size)
assertEquals("-414", teachers[1].value)
assertEquals("Авдеева Екатерина Владимировна (пр.)", teachers[1].label)
val weeks = OptionParser.parseOptions(readFixture("group_week.html"), "ddlWeek")
assertTrue(weeks.isNotEmpty())
assertTrue(weeks.first().value.endsWith(" 0:00:00"))
```

`WeekSelectorTest.kt`:
```kotlin
val weeks = listOf(Option("02.11.2026 0:00:00", "02.11.2026"), Option("09.11.2026 0:00:00", "09.11.2026"))
assertEquals(weeks[0], WeekSelector.selectWeek(weeks, LocalDate.of(2026, 11, 2)))
assertEquals(weeks[1], WeekSelector.selectWeek(weeks, LocalDate.of(2026, 11, 8)))
assertNull(WeekSelector.selectWeek(emptyList(), LocalDate.of(2026, 11, 2)))
```

- [ ] **Step 11: Run tests, expect failure; implement `OptionParser` and `WeekSelector`**

Run: `./gradlew :app:testDebugUnitTest --tests '*OptionParserTest*' --tests '*WeekSelectorTest*'`
Expected: FAIL, then implement; `selectWeek` returns exact label-date match or the nearest week by absolute day distance.

- [ ] **Step 12: Run all parser tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 13: Commit**

```bash
git add app/src/main app/src/test docs
git commit -m "feat: add schedule models and HTML parsers"
```

---

### Task 3: ASP.NET Network Session

**Files:**
- Create: `app/src/main/java/by/iseu/schedule/data/network/MemoryCookieJar.kt`
- Create: `app/src/main/java/by/iseu/schedule/data/network/AspNetSession.kt`
- Test: `app/src/test/java/by/iseu/schedule/data/network/AspNetSessionTest.kt`
- Test: `app/src/test/java/by/iseu/schedule/data/network/LiveFetchTest.kt`

**Interfaces:**
- Consumes: `Option`, `OptionParser`.
- Produces:
```kotlin
class MemoryCookieJar : CookieJar {
    fun clear()
}

class AspNetSession(
    private val client: OkHttpClient,
    private val url: String,
    private val cookieJar: MemoryCookieJar,
) {
    fun options(selectName: String): List<Option>
    fun postback(eventTarget: String, values: Map<String, String> = emptyMap())
    fun submit(values: Map<String, String> = emptyMap()): String
    fun message(): String?
}
```

- [ ] **Step 1: Write failing cookie-jar test**

Use `MockWebServer`: first response sets `Set-Cookie: ASP.NET_SessionId=abc; Path=/`; second request must contain `Cookie: ASP.NET_SessionId=abc`. Assert `MemoryCookieJar.loadForRequest` returns it and `clear()` empties it.

- [ ] **Step 2: Run test, expect failure; implement `MemoryCookieJar`**

Run: `./gradlew :app:testDebugUnitTest --tests '*AspNetSessionTest*'`
Expected: FAIL, then implement with a `MutableList<Cookie>`, replacing cookies by name+domain+path on save.

- [ ] **Step 3: Write failing postback-form test**

Use `MockWebServer` with two responses:
1. HTML with `<form>` containing `<input type="hidden" name="__VIEWSTATE" value="STATE1">`, `<select name="ddlFac"><option value="2">Факультет</option></select>`, and `<span id="lMessage"></span>`.
2. Same form with `__VIEWSTATE=STATE2` and a `#lMessage` text.

Record the second request body and assert it contains `__EVENTTARGET=ddlFac`, `__VIEWSTATE=STATE1`, and `ddlFac=2`. Assert `session.options("ddlFac")` returns the option and `session.postback("ddlFac", mapOf("ddlFac" to "2"))` succeeds.

- [ ] **Step 4: Run test, expect failure; implement `AspNetSession`**

GET the URL with `User-Agent` and `Referer`; parse the first `<form>`; collect all named `input` (skip submit/button/image/reset/file; include checked checkbox/radio), `select` (selected or first option), and `textarea` values; add `__EVENTTARGET` and `__EVENTARGUMENT`; POST as `application/x-www-form-urlencoded`; replace the document with the response. `submit` also updates the document and returns HTML. `message()` returns `#lMessage` text or null. Retry once on `IOException`/5xx inside the repository, not in the session.

- [ ] **Step 5: Run network tests**

Run: `./gradlew :app:testDebugUnitTest --tests '*AspNetSessionTest*'`
Expected: PASS.

- [ ] **Step 6: Write live integration test**

`LiveFetchTest.kt`: skip unless `System.getenv("ISEU_LIVE") == "1"` (`Assume.assumeTrue`). It must:
1. Open `http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx`.
2. Read `ddlFac`, pick the first option, postback, read `ddlDep`, pick first, postback, read `ddlCourse`, pick first, postback, read `ddlGroup`, pick first, postback, read `ddlWeek`, pick the first option, postback, submit `ShowTT=Показать`.
3. Assert `ScheduleParser.parseGroupTable(html)` does not throw and `ddlWeek` was non-empty.
4. Open `umuteachers.aspx`, pick the first teacher option with `value != "0"`, postback, pick the first week, postback, submit `Show=Показать` + `cbShowDraftForTeacher=on`.
5. Assert `ScheduleParser.parseTeacherTable(html)` does not throw.

- [ ] **Step 7: Run live test**

Run:
```bash
ISEU_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*LiveFetchTest*' --info
```
Expected: PASS (lessons may be empty for a random group/week; the test asserts parsing and weeks, not non-empty schedules).

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add ASP.NET webforms session and live fetch test"
```

---

### Task 4: Persistence, Cache Policy, Repository

**Files:**
- Create: `data/db/Entities.kt`, `data/db/Daos.kt`, `data/db/AppDatabase.kt`
- Create: `data/prefs/SelectionStore.kt`
- Create: `data/repo/CachePolicy.kt`, `data/repo/ScheduleRepository.kt`
- Create: `IseuApp.kt`
- Test: `app/src/test/java/by/iseu/schedule/data/repo/CachePolicyTest.kt`

**Interfaces:**
- Consumes: `AspNetSession`, parsers, `MemoryCookieJar`, `Option`, `Lesson`, `WeekSchedule`, `Mode`, `ThemeMode`.
- Produces:
```kotlin
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class ScheduleSelection(
    val mode: Mode,
    val faculty: Option? = null,
    val form: Option? = null,
    val course: Option? = null,
    val group: Option? = null,
    val teacher: Option? = null,
) {
    val targetId: String
    val targetLabel: String
    val cacheKey: String
}

data class WeekSchedule(
    val weekMonday: LocalDate,
    val mode: Mode,
    val targetId: String,
    val targetLabel: String,
    val lessons: List<Lesson>,
    val siteMessage: String?,
    val fetchedAt: Instant,
)

data class SchedulePage(val weeks: List<Option>, val schedule: WeekSchedule)

sealed class IseuException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class Network(cause: Throwable) : IseuException("Нет соединения с сайтом", cause)
    class Site(val status: Int) : IseuException("Сайт ответил ошибкой $status")
    class Parse(cause: Throwable) : IseuException("Не удалось разобрать ответ сайта", cause)
    class CatalogStale : IseuException("Сохранённая группа больше не доступна")
}

interface ScheduleRepository {
    fun selectionFlow(): Flow<ScheduleSelection?>
    fun themeFlow(): Flow<ThemeMode>
    suspend fun saveSelection(selection: ScheduleSelection)
    suspend fun setTheme(mode: ThemeMode)
    suspend fun loadFaculties(force: Boolean = false): List<Option>
    suspend fun loadForms(faculty: Option, force: Boolean = false): List<Option>
    suspend fun loadCourses(faculty: Option, form: Option, force: Boolean = false): List<Option>
    suspend fun loadGroups(faculty: Option, form: Option, course: Option, force: Boolean = false): List<Option>
    suspend fun loadTeachers(force: Boolean = false): List<Option>
    fun cachedWeek(selection: ScheduleSelection, monday: LocalDate): Flow<WeekSchedule?>
    suspend fun cachedWeeks(selection: ScheduleSelection): List<Option>
    suspend fun loadWeek(selection: ScheduleSelection, monday: LocalDate, force: Boolean = false): SchedulePage
    suspend fun clearCache()
}

class DefaultScheduleRepository(
    private val client: OkHttpClient,
    private val cookieJar: MemoryCookieJar,
    private val weekDao: WeekCacheDao,
    private val catalogDao: CatalogDao,
    private val selectionStore: SelectionStore,
    private val now: () -> Instant = Instant::now,
) : ScheduleRepository
```

- [ ] **Step 1: Write failing cache-policy test**

`CachePolicyTest.kt`:
```kotlin
val now = Instant.parse("2026-11-02T10:00:00Z")
assertFalse(CachePolicy.shouldRefresh(now.minusSeconds(29 * 60), now))
assertTrue(CachePolicy.shouldRefresh(now.minusSeconds(31 * 60), now))
assertTrue(CachePolicy.shouldRefresh(null, now))
```

- [ ] **Step 2: Run test, expect failure; implement `CachePolicy`**

```kotlin
object CachePolicy {
    val weekTtl: Duration = Duration.ofMinutes(30)
    val catalogTtl: Duration = Duration.ofHours(12)
    fun shouldRefresh(fetchedAt: Instant?, now: Instant, ttl: Duration = weekTtl): Boolean
}
```
Return `true` when `fetchedAt == null` or older than `ttl`.

- [ ] **Step 3: Implement Room entities and DAOs**

`Entities.kt`:
```kotlin
@Entity(tableName = "week_cache")
data class WeekCacheEntity(
    @PrimaryKey val cacheKey: String,
    val mode: String,
    val targetId: String,
    val targetLabel: String,
    val weekMonday: String,
    val lessonsJson: String,
    val siteMessage: String?,
    val fetchedAtEpochMs: Long,
)

@Entity(tableName = "catalog_cache")
data class CatalogEntry(
    @PrimaryKey val key: String,
    val json: String,
    val fetchedAtEpochMs: Long,
)
```

`Daos.kt`:
```kotlin
@Dao interface WeekCacheDao {
    @Query("SELECT * FROM week_cache WHERE cacheKey = :key") suspend fun get(key: String): WeekCacheEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: WeekCacheEntity)
    @Query("DELETE FROM week_cache") suspend fun clear()
}

@Dao interface CatalogDao {
    @Query("SELECT * FROM catalog_cache WHERE key = :key") suspend fun get(key: String): CatalogEntry?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: CatalogEntry)
    @Query("DELETE FROM catalog_cache") suspend fun clear()
}
```

`AppDatabase.kt`: `@Database(entities = [WeekCacheEntity::class, CatalogEntry::class], version = 1, exportSchema = false)` with abstract DAOs.

Serialization: add `@Serializable data class LessonDto(...)` with string date/time and a `Lesson.toDto()`/`LessonDto.toLesson()` pair; `Option` is `@Serializable`.

- [ ] **Step 4: Implement `SelectionStore`**

DataStore keys: `mode`, `faculty_value`, `faculty_label`, `form_value`, `form_label`, `course_value`, `course_label`, `group_value`, `group_label`, `teacher_value`, `teacher_label`, `theme`. Expose `selectionFlow(): Flow<ScheduleSelection?>` (null until all required values exist), `saveSelection`, `themeFlow`, `setTheme`, `clear`.

- [ ] **Step 5: Implement `DefaultScheduleRepository`**

- Catalog cache key: `faculties`, `forms:<facultyValue>`, `courses:<faculty>:<form>`, `groups:<faculty>:<form>:<course>`, `weeks:<selection.cacheKey>`, `teachers`.
- `catalogTtl` 12h; `force` bypasses.
- Group fetch: `AspNetSession` on `umu.aspx`; postbacks `ddlFac`, `ddlDep`, `ddlCourse`, `ddlGroup`; read weeks; select week; postback `ddlWeek`; submit `ShowTT`; parse.
- Teacher fetch: `AspNetSession` on `umuteachers.aspx`; postback `DropDownList1`; read weeks; select week; postback `ddlWeek`; submit `Show` + `cbShowDraftForTeacher=on`; parse.
- `loadWeek`: if cached and not stale and not force, return `SchedulePage(cachedWeeks, cachedSchedule)`; otherwise fetch with one retry, save week and weeks cache, return.
- `loadTeachers`: filter `value != "0"` and label not containing "Выберите".
- Empty dependent option lists after a postback → throw `IseuException.CatalogStale`.
- Wrap `IOException` as `Network`, non-2xx as `Site`, Jsoup/parse exceptions as `Parse`.
- Guard network with `Mutex` so only one cascade runs at a time.

- [ ] **Step 6: Implement `IseuApp` container**

```kotlin
class IseuApp : Application() {
    lateinit var repository: ScheduleRepository
    override fun onCreate() {
        super.onCreate()
        val cookieJar = MemoryCookieJar()
        val client = OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .callTimeout(25, TimeUnit.SECONDS)
            .build()
        val db = Room.databaseBuilder(this, AppDatabase::class.java, "iseu.db").build()
        val store = SelectionStore(this)
        repository = DefaultScheduleRepository(client, cookieJar, db.weekCacheDao(), db.catalogDao(), store)
    }
}
```

- [ ] **Step 7: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS and `BUILD SUCCESSFUL`.

- [ ] **Step 8: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add Room cache, DataStore selection and schedule repository"
```

---

### Task 5: Monochrome Theme and App Shell

**Files:**
- Create: `ui/theme/Theme.kt`
- Modify: `MainActivity.kt`
- Create: `ui/common/Components.kt` (banners/empty/error placeholders used later)

**Interfaces:**
- Consumes: `ScheduleRepository`, `ThemeMode`.
- Produces:
```kotlin
@Composable fun IseuTheme(mode: ThemeMode, content: @Composable () -> Unit)
```

- [ ] **Step 1: Implement the theme**

Define light and dark `ColorScheme` exactly as the spec: light background `#FFFFFF`, surface `#FFFFFF`, surfaceVariant `#F5F5F5`, onBackground `#0A0A0A`, onSurfaceVariant `#6B6B6B`, outline `#D4D4D4`; dark background `#0A0A0A`, surface `#0A0A0A`, surfaceVariant `#161616`, onBackground `#F5F5F5`, onSurfaceVariant `#A3A3A3`, outline `#2E2E2E`. Set `error = onBackground` and `onError = background` (monochrome errors). Use `lightColorScheme`/`darkColorScheme` and the system setting for `ThemeMode.SYSTEM`.

- [ ] **Step 2: Wire `MainActivity`**

Collect `repository.themeFlow()` as state; call `enableEdgeToEdge()`, `IseuTheme(theme)`, and show a `Scaffold` with a placeholder `Text("ISEU")`.

- [ ] **Step 3: Build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main
git commit -m "feat: add monochrome theme and app shell"
```

---

### Task 6: Selection Screen

**Files:**
- Create: `ui/select/SelectViewModel.kt`, `ui/select/SelectScreen.kt`
- Modify: `MainActivity.kt` (navigation)
- Test: `app/src/test/java/by/iseu/schedule/ui/select/SelectViewModelTest.kt`

**Interfaces:**
- Consumes: `ScheduleRepository`, `Mode`, `Option`, `ScheduleSelection`.
- Produces:
```kotlin
data class SelectUiState(
    val mode: Mode = Mode.GROUP,
    val faculties: List<Option> = emptyList(),
    val forms: List<Option> = emptyList(),
    val courses: List<Option> = emptyList(),
    val groups: List<Option> = emptyList(),
    val teachers: List<Option> = emptyList(),
    val faculty: Option? = null,
    val form: Option? = null,
    val course: Option? = null,
    val group: Option? = null,
    val teacher: Option? = null,
    val teacherQuery: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

class SelectViewModel(
    private val repository: ScheduleRepository,
    private val onSaved: () -> Unit,
) : ViewModel() {
    val state: StateFlow<SelectUiState>
    fun setMode(mode: Mode)
    fun loadFaculties()
    fun loadTeachers()
    fun selectFaculty(option: Option)
    fun selectForm(option: Option)
    fun selectCourse(option: Option)
    fun selectGroup(option: Option)
    fun selectTeacher(option: Option)
    fun setTeacherQuery(query: String)
    fun save()
    fun clearError()
}
```

- [ ] **Step 1: Write failing ViewModel test**

Use a `FakeRepository` implementing `ScheduleRepository` with fixed lists and a `saved` field. Assert:
```kotlin
vm.selectFaculty(Option("2", "ФМОС"))
vm.selectForm(Option("2", "дневная"))
vm.selectCourse(Option("1", "1 курс"))
vm.selectGroup(Option("369", "А61ИТ1"))
vm.save()
assertEquals(Option("369", "А61ИТ1"), fake.saved!!.group)
```
And: after `selectCourse`, `state.value.form` is set; after changing `selectFaculty` to another option, `form`, `course`, `group` are null and `groups` is empty.
And: when the fake `loadGroups` throws `IseuException.CatalogStale`, `state.value.error` is not null and `state.value.group` is null.

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*SelectViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `SelectViewModel`**

On init: collect saved selection; prefill fields and mode; load faculties and teachers; if a saved group chain exists, load forms → courses → groups sequentially. Each cascade method resets dependent fields before loading. `save()` builds `ScheduleSelection`; for `GROUP` require group, for `TEACHER` require teacher; then `repository.saveSelection` and `onSaved()`. Catch `IseuException.CatalogStale`: clear dependent fields and set a message.

- [ ] **Step 4: Implement `SelectScreen`**

- `SingleChoiceSegmentedButtonRow` for «Группа | Преподаватель».
- Group mode: four `ExposedDropdownMenuBox` fields; each disabled until its parent list is loaded; show a small progress indicator while loading; a primary `Button("Показать расписание")` enabled when `group != null`.
- Teacher mode: `OutlinedTextField` search with `Icons.Default.Search`; `LazyColumn` of filtered teachers; tap selects and saves immediately.
- Error banner with «Повторить».
- Use `LazyColumn` for the whole screen; keep monochrome styling.
- Content descriptions for icon buttons.

- [ ] **Step 5: Wire navigation**

`MainActivity` uses `NavHost` with routes `select`, `schedule`, `settings`. `SelectScreen(onSaved = { navController.navigate("schedule") { popUpTo("select") { inclusive = true } } })`. Start at `select`; if a saved selection exists, `SelectViewModel`/root redirects to schedule after load. Simpler: `RootScreen` collects `selectionFlow`; if non-null, show `ScheduleScreen`; otherwise `SelectScreen`. Use navigation only for settings. This avoids a flash.

- [ ] **Step 6: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add group and teacher selection screen"
```

---

### Task 7: Schedule Screen

**Files:**
- Create: `ui/schedule/ScheduleViewModel.kt`, `ui/schedule/ScheduleScreen.kt`
- Modify: `MainActivity.kt`
- Test: `app/src/test/java/by/iseu/schedule/ui/schedule/ScheduleViewModelTest.kt`

**Interfaces:**
- Consumes: `ScheduleRepository`, `ScheduleSelection`, `WeekSchedule`, `SchedulePage`, `WeekSelector`, `Lesson`.
- Produces:
```kotlin
data class ScheduleUiState(
    val selection: ScheduleSelection? = null,
    val weekMonday: LocalDate = WeekSelector.mondayOf(LocalDate.now()),
    val weeks: List<Option> = emptyList(),
    val lessons: List<Lesson> = emptyList(),
    val siteMessage: String? = null,
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val isFromCache: Boolean = false,
    val fetchedAt: Instant? = null,
)

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val today: () -> LocalDate = { LocalDate.now() },
) : ViewModel() {
    val state: StateFlow<ScheduleUiState>
    fun refresh()
    fun retry()
    fun previousWeek()
    fun nextWeek()
    fun currentWeek()
}
```

- [ ] **Step 1: Write failing ViewModel test**

`FakeRepository` returns weeks `02.11`, `09.11`, and a cached schedule for `02.11`. Assert:
```kotlin
vm.state.value.weeks == listOf(week1, week2)
vm.nextWeek()
assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.weekMonday)
vm.nextWeek() // already last
assertEquals(LocalDate.of(2026, 11, 9), vm.state.value.weekMonday)
```
Use `ScheduleViewModel(fakeRepository) { LocalDate.of(2026, 11, 2) }` and a `MainDispatcherRule` that calls `Dispatchers.setMain(StandardTestDispatcher())` in `@Before` and `Dispatchers.resetMain()` in `@After`; call `advanceUntilIdle()` after actions.
And a second test: when `loadWeek` throws `IseuException.Network` and a cache exists, `state.error` is not null, `state.lessons` is not empty, `state.isFromCache` is true.

- [ ] **Step 2: Run test, expect failure**

Run: `./gradlew :app:testDebugUnitTest --tests '*ScheduleViewModelTest*'`
Expected: FAIL.

- [ ] **Step 3: Implement `ScheduleViewModel`**

- On init: collect `selectionFlow`; on first non-null selection, load cached weeks, then `loadWeek(mondayOf(today))`.
- `loadWeek(monday, force)`: cancel the previous job; set `loading` (or `refreshing` when data exists); collect the first cached value and set `lessons`/`isFromCache`; call `repository.loadWeek`; on success update weeks, lessons, `weekMonday = page.schedule.weekMonday`, `fetchedAt`; on failure keep cache and set `error` (use `IseuException` message; otherwise generic). Clear `loading`/`refreshing`.
- `previousWeek`/`nextWeek`: sort weeks by parsed date; find the index of `weekMonday`; move within bounds; if weeks empty, move ±7 days.
- `currentWeek`: select the exact current Monday if present, else nearest; call `loadWeek`.
- `refresh`/`retry`: `loadWeek(state.weekMonday, force = true)`.

- [ ] **Step 4: Implement `ScheduleScreen`**

- `Scaffold` + `TopAppBar`: title `ISEU`, subtitle target label, actions: change target (text button «Сменить»), settings icon.
- `WeekHeader`: left/right arrows with content descriptions «Предыдущая неделя»/«Следующая неделя», range `dd.MM – dd.MM`, text button «Текущая».
- `PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = vm::refresh)` (annotate the composable with `@OptIn(ExperimentalMaterial3Api::class)`).
- `LazyColumn` with `rememberLazyListState`; day sections Mon–Sun. Today header uses `MaterialTheme.colorScheme.onBackground` background and `background` text (inverted). Empty days render a compact «Нет занятий» row.
- `LessonCard`: rows with time column, a vertical line, content. Subgroup badge (`«1 п/гр»`) as a small chip; lessons with a subgroup use a dashed left border, general lessons a solid border. Type label as a small outlined chip. In group mode show `staff`; in teacher mode show `contingent` and `topic` if present. Show audience when non-null.
- Cache banner when `isFromCache`: «Показано из кэша · обновлено ЧЧ:мм» with a refresh text button.
- Error banner when `error != null` and lessons exist; full error state when lessons are empty; empty state «Пар нет» + `siteMessage` when lessons are empty and no error.
- Loading: centered `CircularProgressIndicator` when `loading && lessons.isEmpty()`.
- `LaunchedEffect(selection)` scroll to today when the current week is shown.
- All colors from `MaterialTheme.colorScheme`; no hardcoded colors outside the theme.

- [ ] **Step 5: Run tests and build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: add schedule screen with week navigation and offline cache"
```

---

### Task 8: Settings Screen

**Files:**
- Create: `ui/settings/SettingsScreen.kt`
- Modify: `MainActivity.kt`

**Interfaces:**
- Consumes: `ScheduleRepository`, `ThemeMode`.
- Produces: `@Composable fun SettingsScreen(repository: ScheduleRepository, onChangeTarget: () -> Unit, onBack: () -> Unit)`

- [ ] **Step 1: Implement settings UI**

- Top app bar with back arrow.
- Theme section: three rows with `RadioButton` («Системная», «Светлая», «Тёмная») calling `repository.setTheme`.
- «Сменить группу или преподавателя» → `onChangeTarget`.
- «Очистить кэш» → `repository.clearCache()` in a coroutine; show a `Snackbar` «Кэш очищен».
- «Источник данных» → `Intent(ACTION_VIEW, Uri.parse("http://rsp.iseu.by/Raspisanie/TimeTable/umu.aspx"))`; wrap in try/catch.
- «Версия 1.0».

- [ ] **Step 2: Wire navigation and build**

Run: `./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add app/src/main
git commit -m "feat: add settings screen"
```

---

### Task 9: Polish, Release Build, README

**Files:**
- Modify: `app/build.gradle.kts` (confirm release signing with debug key)
- Create: `README.md`

**Interfaces:**
- Produces: `app/build/outputs/apk/debug/app-debug.apk` and `app/build/outputs/apk/release/app-release.apk`.

- [ ] **Step 1: Add README**

Include: what the app does, source endpoints, build prerequisites (`scripts/env.sh`), commands `./gradlew :app:testDebugUnitTest`, `./gradlew :app:assembleDebug`, `./gradlew :app:assembleRelease`, APK paths, install command `adb install -r app/build/outputs/apk/debug/app-debug.apk`, note that the release APK is signed with the debug key and updates require the same machine keystore, and the cleartext-HTTP caveat.

- [ ] **Step 2: Build both APKs**

Run:
```bash
source scripts/env.sh
./gradlew clean :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
ls -lh app/build/outputs/apk/debug/app-debug.apk app/build/outputs/apk/release/app-release.apk
```
Expected: both APKs exist; release is smaller; `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check APK contents**

Run:
```bash
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer apk summary app/build/outputs/apk/release/app-release.apk
$ANDROID_HOME/cmdline-tools/latest/bin/apkanalyzer manifest permissions app/build/outputs/apk/release/app-release.apk
```
Expected: package `by.iseu.schedule`, only `android.permission.INTERNET`.

- [ ] **Step 4: Commit**

```bash
git add README.md app/build.gradle.kts
git commit -m "docs: add build and install instructions"
```

---

### Task 10: Final Verification

**Files:** none new

- [ ] **Step 1: Run the full unit suite**

Run: `source scripts/env.sh && ./gradlew :app:testDebugUnitTest`
Expected: `BUILD SUCCESSFUL`, all tests pass.

- [ ] **Step 2: Run the live integration test**

Run: `ISEU_LIVE=1 ./gradlew :app:testDebugUnitTest --tests '*LiveFetchTest*' --rerun-tasks`
Expected: PASS against the live site.

- [ ] **Step 3: Build the final debug APK**

Run: `./gradlew :app:assembleDebug`
Expected: `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 4: Manual smoke checklist (if a device is connected)**

Run: `adb devices`
If a device is present: `adb install -r app/build/outputs/apk/debug/app-debug.apk`, open the app, choose a group, verify the current week, switch weeks, switch to a teacher, enable airplane mode and verify the cache banner, switch theme in settings. If no device, report the APK path and state that the on-device smoke test was not possible.

- [ ] **Step 5: Commit any final fixes**

```bash
git add .
git commit -m "chore: final verification fixes"
```
