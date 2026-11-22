import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File
import java.security.MessageDigest
import java.time.LocalDateTime
import java.util.Properties

plugins {
    id("com.android.application")
    // AGP 9 内置 Kotlin：不再应用 org.jetbrains.kotlin.android。
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "io.github.mianxyz.fanqiecustomizer"

    // material3 1.5.0-rc01 与 Compose 1.12.x 的 AAR 元数据要求 minCompileSdk=37。
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.mianxyz.fanqiecustomizer"
        minSdk = 28
        targetSdk = 35
        versionCode = 100
        versionName = "6.0"
        testInstrumentationRunner = "io.github.mianxyz.fanqiecustomizer.ConfigBridgeInstrumentation"
    }

    // 单一事实来源：Java Hook 树留在 ../src（build.ps1 也编译它）。
    // 注意：内置 Kotlin 只支持通过 android.sourceSets 的 kotlin 集添加 Kotlin 目录。
    sourceSets["main"].java.srcDirs("src/main/java", "../src")
    sourceSets["main"].kotlin.srcDirs("src/main/kotlin")
    sourceSets["main"].res.srcDirs("src/main/res", "../res")
    // LSPosed 元数据（module.prop / scope.list / java_init.list）与实际源码同源，
    // build.ps1 也会手工塞进 APK；这里让 AGP 自动打包，两条链路产物一致。
    sourceSets["main"].resources.srcDirs("../resources")

    // ─────────────────────────────────────────────────────────────
    // Release 签名：密钥只从 local.properties 读取，绝不进仓库
    //
    // local.properties 已被 .gitignore 忽略，在其中提供四项：
    //   pulp.storeFile=<绝对路径>\fanqie-dev.jks
    //   pulp.storePassword=<密码>
    //   pulp.keyAlias=fanqie-dev
    //   pulp.keyPassword=<密码>
    // 四项齐全才启用 release 签名；缺任一项时 release 保持未签名，
    // assembleRelease 仍可产出未签名包，不会让构建失败。
    // ─────────────────────────────────────────────────────────────
    val signingProps = Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.isFile) file.inputStream().use { load(it) }
    }

    fun signingProp(name: String): String? =
        signingProps.getProperty(name)?.takeIf { it.isNotBlank() }

    val releaseStore = signingProp("pulp.storeFile")
    val releaseStorePassword = signingProp("pulp.storePassword")
    val releaseAlias = signingProp("pulp.keyAlias")
    val releaseKeyPassword = signingProp("pulp.keyPassword")
    val hasReleaseSigning = releaseStore != null && releaseStorePassword != null &&
        releaseAlias != null && releaseKeyPassword != null

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = File(releaseStore!!)
                storePassword = releaseStorePassword
                keyAlias = releaseAlias
                keyPassword = releaseKeyPassword
                // 与 5.0.5 公开包一致：v1 + v2 + v3 全签，保证可覆盖升级。
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // 未配置签名时保留 null，产出未签名 APK 而不是构建失败。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/*.kotlin_module",
            "kotlin/**",
        )
    }

    androidResources {
        // 模块只发布中英文案；裁掉无关库语言包以缩小 APK。
        localeFilters += listOf("zh", "en")
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        // 内置 Kotlin 下 jvmTarget 默认跟随 compileOptions.targetCompatibility，
        // 这里显式写出以保留原有 Java 17 目标。
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // libxposed API 102 —— 运行时由框架提供，不打包进 APK。
    compileOnly(files("../libs/api-102.0.0.jar"))
    // service + interface 垫片随 APK 一起 dex，与 build.ps1 的做法一致。
    implementation(files("../libs/service-102.0.0.jar"))
    implementation(files("../libs/interface-102.0.0.jar"))

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.12.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")

    // Material 3 Expressive：LinearWavyProgressIndicator 从 1.5.0 起才有。
    // BOM 2026.09.00 固定的是 1.4.0，因此在这里显式覆盖版本。
    implementation("androidx.compose.material3:material3:1.5.0-rc01")

    // 可选的液态玻璃（实时高斯模糊）。
    implementation("dev.chrisbanes.haze:haze:2.1.0")
    implementation("dev.chrisbanes.haze:haze-blur:2.1.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}


// ─────────────────────────────────────────────────────────────
// 构建产物统一归档到 D:\FQMK\release
//
// 需求：模块 APK 之后都放在 D:\FQMK\release。
// 做成 Gradle 任务而不是手动复制，是为了让「构建即归档」成为默认行为——
// 忘记手动复制就会留下一堆版本不清的散落 APK，这个项目已经出现过多份
// app-debug.apk 覆盖同名文件的情况。
//
// 归档两份：
//   1. pulp-<versionName>-<versionCode>-<variant>.apk   带版本，历史可追溯
//   2. pulp-latest-<variant>.apk                       固定名，方便脚本/链接引用
// 同时写一份 build-info.txt 记录版本号、构建时间、APK 大小与 SHA-256，
// 便于核对某个安装包究竟对应哪次构建。
// ─────────────────────────────────────────────────────────────

val releaseDir = rootProject.layout.projectDirectory.dir("../release").asFile

/** 把某个 variant 的 APK 归档到 release 目录。 */
fun archiveApk(variant: String, apk: File) {
    if (!apk.exists()) return
    releaseDir.mkdirs()

    val base = "pulp-${android.defaultConfig.versionName}-${android.defaultConfig.versionCode}-$variant"
    val versioned = File(releaseDir, "$base.apk")
    apk.copyTo(versioned, overwrite = true)
    apk.copyTo(File(releaseDir, "pulp-latest-$variant.apk"), overwrite = true)

    // 注意：Kotlin DSL 里 `java` 指向 JavaPluginExtension，
    // 所以 `java.security.MessageDigest` 会报 Unresolved reference ——
    // 必须像上面那样在文件顶部 import 后再用短名。
    val sha = MessageDigest.getInstance("SHA-256")
        .digest(versioned.readBytes())
        .joinToString("") { "%02x".format(it) }

    File(releaseDir, "build-info.txt").writeText(
        buildString {
            appendLine("module      : Pulp (fanqie-customizer)")
            appendLine("package     : ${android.defaultConfig.applicationId}")
            appendLine("versionName : ${android.defaultConfig.versionName}")
            appendLine("versionCode : ${android.defaultConfig.versionCode}")
            appendLine("variant     : $variant")
            appendLine("builtAt     : ${LocalDateTime.now()}")
            appendLine("size        : ${"%,d".format(versioned.length())} bytes")
            appendLine("sha256      : $sha")
            appendLine("source      : ${apk.absolutePath}")
        }
    )
    println("[release] $variant -> ${versioned.absolutePath} (${"%.2f".format(versioned.length() / 1048576.0)} MB)")
}

tasks.register("archiveDebugApk") {
    group = "distribution"
    description = "把 debug APK 归档到 D:\\FQMK\\release"
    dependsOn("assembleDebug")
    doLast {
        archiveApk("debug", layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile)
    }
}

tasks.register("archiveReleaseApk") {
    group = "distribution"
    description = "把 release APK 归档到 D:\\FQMK\\release"
    dependsOn("assembleRelease")
    doLast {
        val dir = layout.buildDirectory.dir("outputs/apk/release").get().asFile
        val apk = dir.listFiles()?.firstOrNull { it.extension == "apk" && !it.name.contains("unsigned") }
        if (apk != null) archiveApk("release", apk)
    }
}

// 让 assemble* 之后自动归档，避免忘记手动执行。
tasks.matching { it.name == "assembleDebug" || it.name == "assembleRelease" }.configureEach {
    finalizedBy(if (name == "assembleDebug") "archiveDebugApk" else "archiveReleaseApk")
}
