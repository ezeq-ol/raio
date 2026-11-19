plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.raio.browser"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.raio.browser"
        minSdk = 21
        targetSdk = 34
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
        versionCode = 3
        versionName = "1.2"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Assina com a chave de debug para o APK de release ja sair instalavel
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions { unitTests.isIncludeAndroidResources = true }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
    }
}

// Zero dependencias externas: APK minusculo e abertura rapida.

// Apenas para testes JVM; n�o aumenta o APK.
dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}

tasks.withType<Test>().configureEach {
    maxHeapSize = "1024m"
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
}
