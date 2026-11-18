plugins { id("com.android.application"); kotlin("android") }
android {
 namespace = "com.crossybot"
 compileSdk = 35
 defaultConfig {
  applicationId = "com.crossybot"
  minSdk = 26
  targetSdk = 35
  versionCode = 1
  versionName = "0.1.0-calibrated"
  providers.gradleProperty("botAbi").orNull?.let { abi -> ndk { abiFilters += abi.split(",") } }
  testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
 }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
 kotlinOptions { jvmTarget = "17" }
 buildTypes { release { isMinifyEnabled = false } }
 packaging { jniLibs { useLegacyPackaging = true } }
 lint { abortOnError = true }
}
dependencies {
 implementation(project(":core"))
 androidTestImplementation("androidx.test:runner:1.6.2")
 androidTestImplementation("androidx.test:rules:1.6.1")
 androidTestImplementation("androidx.test.ext:junit:1.2.1")
 implementation("org.opencv:opencv:4.10.0")
 implementation("com.google.mlkit:text-recognition:16.0.1")
}
