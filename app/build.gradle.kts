plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Milisegundos crudos en vez de formatear aquí: el paquete "java" queda
// sombreado dentro de la DSL de Android Gradle Plugin (java.time/java.util/
// java.text no resuelven ahí ni siquiera a nivel de script), así que el
// formato legible se hace en runtime (ver BuildInfo.kt) a partir de este Long.
val buildTimestampMillis: Long = System.currentTimeMillis()

android {
    namespace = "com.dairoroberto.felicitywatch"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dairoroberto.felicitywatch"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        // Visible en Ajustes → Acerca de: para confirmar sin ambigüedad qué
        // build exacto está instalado en el teléfono cuando se reportan bugs.
        buildConfigField("long", "BUILD_TIMESTAMP_MILLIS", "${buildTimestampMillis}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)

    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.kotlinx.coroutines.test)

    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    testImplementation(libs.junit)
}

kapt {
    correctErrorTypes = true
}

// Necesario para AppDatabase.exportSchema = true — sin esto Room no genera
// los JSON de esquema por versión, que son la referencia que usa para
// validar futuras migraciones (y detectar en compilación si una Migration
// escrita a mano no calza con el esquema real de la entidad).
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}
