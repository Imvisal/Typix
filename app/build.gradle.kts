plugins { id("com.android.application"); id("org.jetbrains.kotlin.android"); id("com.google.gms.google-services") }
android {
    namespace = "app.typix"
    compileSdk = 34
    defaultConfig {
        applicationId = "app.typix.keyboard"; minSdk = 24; targetSdk = 34
        resourceConfigurations += listOf("en", "si")
        // every CI build gets a higher versionCode, so a newer APK always installs over an older one
        versionCode = 9 + (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0); versionName = "0.7.2"
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
dependencies {
    testImplementation("junit:junit:4.13.2")
    // Only used by the optional "Sign in with Google" settings sync (SettingsActivity); the keyboard itself never touches the network.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
}
