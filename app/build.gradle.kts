plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.kizeo.ps3icontool"
    compileSdk = 34 // Ajustado a una versión estable común

    defaultConfig {
        applicationId = "com.kizeo.ps3icontool"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // --- BLOQUE DE FIRMA AÑADIDO ---
    signingConfigs {
        create("release") {
            // Usamos dobles barras \\ para que Windows entienda la ruta correctamente
            storeFile = file("C:\\Users\\LXCXS\\Desktop\\youtu\\kizeokey.jks")
            storePassword = "mixiaomi16"
            keyAlias = "hfw"
            keyPassword = "mixiaomi16"

            // Forzamos ambas firmas para evitar bloqueos de Play Protect
            enableV1Signing = true
            enableV2Signing = true
        }
    }

    buildTypes {
        release {
            // Vinculamos la firma que creamos arriba
            signingConfig = signingConfigs.getByName("release")

            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("commons-net:commons-net:3.10.0") // Para el FTP
    implementation("io.coil-kt:coil-compose:2.6.0")   // Para mostrar imágenes
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}