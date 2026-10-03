plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.froster.app"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.froster.app"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11)
        }
    }

    buildFeatures {
        viewBinding = true
    }
}

// Room guarda aquí un JSON con el esquema de cada versión de la base (1.json,
// 2.json...). Sirve para revisar la migración y para probarla.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity.ktx)
    implementation(libs.constraintlayout)

    // Preferences DataStore — persistencia clave-valor asincrona
    implementation(libs.datastore.preferences)

    // Corrutinas y ciclo de vida, para recolectar Flow sin fugas
    implementation(libs.coroutines.android)
    // .await() sobre las Task de Firebase
    implementation(libs.coroutines.play.services)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)

    // Cliente MQTT (broker Mosquitto de la Semana 3)
    implementation(libs.paho.mqtt)

    // Firebase Realtime Database (Semana 5). Se usa el BoM para que las
    // versiones de los modulos sean coherentes entre si.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.database)
    implementation(libs.firebase.auth)

    // Room (Semana 6): base de datos local SQLite con verificación en compilación
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)          // funciones suspend y Flow en los DAO
    ksp(libs.room.compiler)                // genera el código del DAO al compilar
    implementation(libs.recyclerview)

    testImplementation(libs.junit)
    testImplementation(libs.json)

    // Prueba instrumentada de la migración de Room (corre en el dispositivo)
    androidTestImplementation(libs.room.testing)
    androidTestImplementation(libs.test.runner)
    androidTestImplementation(libs.ext.junit)

    /* La prueba instrumentada comparte las clases con el APK de la app. La app
       traía kotlinx-serialization-core 1.7.3 y room-testing necesita la 1.8.x:
       Gradle bajaba la de la prueba a 1.7.3 y fallaba con AbstractMethodError
       al leer los JSON del esquema. Se sube la de la app para que coincidan. */
    constraints {
        implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.8.1") {
            because("room-testing 2.8 usa kotlinx-serialization-json 1.8.1")
        }
    }
}
