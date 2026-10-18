plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.localai.assistant"
    compileSdk = 35
    defaultConfig { applicationId = "com.localai.assistant"; minSdk = 26; targetSdk = 35; versionCode = 2; versionName = "0.2.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}
dependencies {
    implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}
