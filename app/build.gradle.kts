import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 开发期模型配置：环境变量优先，其次仓库根目录的 local.properties。
// 只注入 debug 变体——release 包里的三项为空，密钥不会随发布包外泄。
// local.properties 已在 .gitignore 中，密钥不进入版本库。
val llmProperties: Properties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun llmValue(envKey: String, propertyKey: String): String =
    System.getenv(envKey)?.takeIf { it.isNotBlank() }
        ?: llmProperties.getProperty(propertyKey)?.takeIf { it.isNotBlank() }
        ?: ""

fun buildConfigString(value: String): String =
    "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "com.attention.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.attention.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    sourceSets["androidTest"].assets.srcDirs("$projectDir/schemas")

    buildTypes {
        debug {
            buildConfigField("String", "LLM_BASE_URL", buildConfigString(llmValue("ATTENTION_LLM_BASE_URL", "llm.baseUrl")))
            buildConfigField("String", "LLM_MODEL", buildConfigString(llmValue("ATTENTION_LLM_MODEL", "llm.model")))
            buildConfigField("String", "LLM_API_KEY", buildConfigString(llmValue("ATTENTION_LLM_API_KEY", "llm.apiKey")))
        }
        release {
            // 发布包不携带任何模型配置：助手在 release 下保持「不可用」，不静默降级
            buildConfigField("String", "LLM_BASE_URL", "\"\"")
            buildConfigField("String", "LLM_MODEL", "\"\"")
            buildConfigField("String", "LLM_API_KEY", "\"\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":workspace-state"))
    // 单向依赖:助手能力件(:agent)只在 :app 组合根装配,反向依赖不存在(ADR-0009)
    implementation(project(":agent"))
    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    debugImplementation(libs.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.espresso.intents)
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.room.testing)
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
