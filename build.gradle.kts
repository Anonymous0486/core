import java.util.Properties

plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    alias(libs.plugins.kotlinKsp)

    id("kotlin-kapt") //TODO: For use databinding
}

val props = Properties()
file("local.properties").inputStream().use { props.load(it) }

android {
    namespace = "org.app.core"
    compileSdk = 35

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        buildConfigField("String", "dm", props.getProperty("domain"))
        buildConfigField("String", "iv", props.getProperty("iv"))
        buildConfigField("String", "secret", props.getProperty("secret"))
        buildConfigField("String", "fbV1Dm", props.getProperty("fbv1domain"))
        buildConfigField("String", "translateDm", props.getProperty("translatedm"))
        buildConfigField("String", "translateWebDm", props.getProperty("translatewebdm"))
        buildConfigField("String", "crawldm", props.getProperty("crawldm"))
        buildConfigField("String", "socialdm", props.getProperty("socialdm"))
        buildConfigField("String", "ttdm", props.getProperty("ttdm"))
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

    packaging {
        resources {
            excludes += "META-INF/DEPENDENCIES"
            excludes += "META-INF/LICENSE"
            excludes += "META-INF/LICENSE.txt"
        }
    }

    flavorDimensions.add("platform")
    productFlavors {
        create("applovin") {
            dimension = "platform"
        }
        create("admob") {
            dimension = "platform"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        dataBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.locale.helper.android)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.glide)
    implementation(libs.lottie)
    implementation(libs.shimmer)
    ksp(libs.compiler)
    implementation(libs.androidx.lifecycle.extensions)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.jsoup)

    // Translate
//    implementation(libs.translate)

    implementation(libs.firebase.config.ktx)
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.crashlytics.ktx)

    "admobImplementation"(libs.play.services.ads)
    "admobImplementation"(libs.vungle)
    "admobImplementation"(libs.pangle)

    implementation(libs.timber)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.room.ktx)

    // Networking
    implementation(libs.converter.gson)
    implementation(libs.logging.interceptor)
    implementation(libs.library)
    implementation(libs.converter.scalars)
    implementation(libs.gson)
    implementation(libs.retrofit)

    implementation(libs.sdp.android)
    implementation(libs.ssp.android)

    //noinspection GradleDynamicVersion
    "applovinImplementation"("com.applovin:applovin-sdk:+")
    "applovinImplementation"(libs.play.services.ads.identifier)
    //noinspection GradleDynamicVersion
    "applovinImplementation"("com.applovin.mediation:google-adapter:+")

    implementation("com.google.android.gms:play-services-drive:17.0.0")
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.google.http-client:google-http-client-gson:1.42.2")
    implementation("com.google.api-client:google-api-client-android:1.30.7")
    implementation("com.google.apis:google-api-services-drive:v3-rev188-1.25.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}