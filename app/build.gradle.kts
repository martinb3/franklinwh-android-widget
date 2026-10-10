plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "org.mbs3.franklinwh.widget"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.mbs3.franklinwh.widget"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Forward the opt-in live-gateway settings (see GatewayLiveTest) into the test JVM.
tasks.withType<Test>().configureEach {
    systemProperty("fwh.host", System.getenv("FWH_HOST") ?: "")
    systemProperty("fwh.port", System.getenv("FWH_PORT") ?: "")
    testLogging.showStandardStreams = true
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)

    // Android's bundled org.json is stubbed in local JVM tests; use the real one there.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
