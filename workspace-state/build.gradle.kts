plugins {
    alias(libs.plugins.kotlin.jvm)
    // 仅出报告,不设阈值：页面状态模块的覆盖目标尚未裁决
    alias(libs.plugins.kover)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
}

dependencies {
    api(project(":domain"))
    testImplementation(libs.junit)
}
