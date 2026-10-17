plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "app.typix"
    compileSdk = 34
    defaultConfig { applicationId = "app.typix.keyboard"; minSdk = 24; targetSdk = 34; versionCode = 5; versionName = "0.4.2" }
    buildTypes { release { isMinifyEnabled = true; isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { testImplementation("junit:junit:4.13.2") }  // no runtime libraries: keeps it light
