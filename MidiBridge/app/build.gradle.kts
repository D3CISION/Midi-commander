plugins { id("com.android.application") }
android {
    namespace = "hu.spektrumhiba.midibridge"
    compileSdk = 36
    defaultConfig {
        applicationId = "hu.spektrumhiba.midibridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        release { isMinifyEnabled = false }
    }
    lint { abortOnError = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
