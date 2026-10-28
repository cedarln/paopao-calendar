import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// 签名材料只从 signing/keystore.properties 读，整个目录在 .gitignore 里。
// 没有这个文件时 release 退回「构建得出来但没签名」，而不是把构建打红：
// 干净 clone 与 fork 仍然能编译，CI 有 secrets 时才产出可安装的包。
val keystoreProps = Properties().apply {
    val file = rootProject.file("signing/keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasSigningKey = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "com.paopao.calendar"
    // androidx.core 1.15 / lifecycle 2.8 的 AAR metadata 要求 compileSdk >= 35。
    // targetSdk 仍留在 34：35 会强制 edge-to-edge，需要先把 insets 逐个视图处理干净。
    compileSdk = 35

    defaultConfig {
        applicationId = "com.paopao.calendar"
        minSdk = 24
        targetSdk = 34
        // 0.1.0 已经发出去了，且数据库 schema 从 1 走到 2：versionCode 必须递增，
        // 否则覆盖安装直接被系统拒（INSTALL_FAILED_VERSION_DOWNGRADE 的反面是同版本重装限制），
        // 应用内更新那条路也永远走不通。
        versionCode = 2
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("zh", "en")
    }

    signingConfigs {
        if (hasSigningKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // 缺签名文件时这里是 null，packageRelease 产出 -unsigned 包；
            // 那样的包装不上设备，但构建与 CI 不因此失败。
            signingConfig = signingConfigs.findByName("release")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)

    implementation(libs.lunar)

    coreLibraryDesugaring(libs.desugar.jdk.libs)
    testImplementation(libs.junit)
    testImplementation(libs.json)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
