plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val googleWebClientId = providers.gradleProperty("CAREBINDER_GOOGLE_WEB_CLIENT_ID").orElse("").get()

android {
    namespace = "com.familycare.carebinder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.familycare.carebinder"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.0.1"
        buildConfigField("String", "CAREBINDER_API_BASE_URL", "\"http://10.0.2.2:8080\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.google.identity.googleid)
    testImplementation(libs.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
