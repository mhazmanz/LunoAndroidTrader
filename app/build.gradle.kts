plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    // Compose Compiler Gradle plugin – required for Kotlin 2.x + Compose
    // Version is already on the classpath (2.0.21), so we do NOT specify it here.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.hazman.lunoandroidtrader"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.hazman.lunoandroidtrader"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    // When using the Compose Compiler Gradle plugin, this block is optional.
    /*
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.3"
    }
    */

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    val composeBom = platform("androidx.compose:compose-bom:2024.04.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.5")
    implementation("androidx.activity:activity-compose:1.9.2")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")

    // Material icon set used by our bottom navigation (Home / Settings)
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // ViewModel + Compose integration
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.5")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    // Retrofit + Moshi
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")

    // Retrofit Scalars converter (plain text / string responses)
    implementation("com.squareup.retrofit2:converter-scalars:2.11.0")

    // Retrofit Gson converter (for public ticker models)
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // Gson core (for @SerializedName, JSON parsing)
    implementation("com.google.code.gson:gson:2.11.0")

    // OkHttp logging
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Encrypted SharedPreferences
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
}
