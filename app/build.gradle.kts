plugins { id("com.android.application") }
android {
    namespace = "app.zapretvtb"
    compileSdk = 34
    defaultConfig { applicationId = "app.zapretvtb"; minSdk = 24; targetSdk = 33; versionCode = 1; versionName = "1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    androidResources { noCompress += "txt" }
}
