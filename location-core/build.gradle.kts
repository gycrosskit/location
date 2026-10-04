plugins { kotlin("multiplatform"); id("com.android.library"); `maven-publish` }
kotlin {
    androidTarget { publishLibraryVariants("release") }
    iosArm64(); iosSimulatorArm64(); iosX64(); jvm(); ohosArm64()
    sourceSets {
        commonMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2-1.0.0") }
        androidMain.dependencies { implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2") }
        commonTest.dependencies { implementation(kotlin("test")) }
    }
}
android {
    namespace = "io.github.gycrosskit.location"
    compileSdk = 36
    defaultConfig { minSdk = 24 }
}
publishing { repositories { maven { name = "staging"; url = uri(rootProject.layout.buildDirectory.dir("maven")) } } }
