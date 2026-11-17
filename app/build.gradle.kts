import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    // Compose compiler for Glance's @Composable widget content.
    alias(libs.plugins.kotlin.compose)
}

// Release signing is read from a properties file kept out of the repository: the path in
// FWH_KEYSTORE_PROPERTIES, or keystore.properties in the project root. It holds storeFile,
// storePassword, keyAlias and keyPassword.
val keystorePropertiesFile: File =
    System.getenv("FWH_KEYSTORE_PROPERTIES")?.let(::File) ?: rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) keystorePropertiesFile.inputStream().use(::load)
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

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("release") {
                // A relative storeFile is resolved against the properties file's directory.
                storeFile = keystorePropertiesFile.resolveSibling(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Null without a keystore, which leaves the release APK unsigned.
            signingConfig = signingConfigs.findByName("release")
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
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    // Android's bundled org.json is stubbed in local JVM tests; use the real one there.
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
