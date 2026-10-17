plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.typix"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.typix.keyboard"; minSdk = 24; targetSdk = 34
        // every CI build gets a higher versionCode, so a newer APK always installs over an older one
        versionCode = 9 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0); versionName = "0.7.0"
    }
    // One fixed key for every build: without it each CI build is signed with a random key and Android refuses to update
    // the installed app ("App not installed") until you uninstall it.
    signingConfigs { create("typix") { storeFile = file("typix-debug.keystore"); storePassword = "typix123"; keyAlias = "typix"; keyPassword = "typix123" } }
    buildTypes {
        debug { signingConfig = signingConfigs.getByName("typix") }
        release { signingConfig = signingConfigs.getByName("typix"); isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }  // no runtime libraries: keeps it light
