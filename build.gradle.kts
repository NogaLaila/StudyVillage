plugins {
    // Fixed version for stability with Gradle 8.13
    id("com.android.application") version "9.1.0" apply false

    // Aligning Kotlin to a stable 2.1.x release
    id("org.jetbrains.kotlin.android") version "2.2.10" apply false

    id("androidx.navigation.safeargs.kotlin") version "2.9.6" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false

    // KSP MUST match the first 3 digits of your Kotlin version
    id("com.google.devtools.ksp") version "2.3.2" apply false
}