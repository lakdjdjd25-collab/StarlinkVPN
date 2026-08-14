plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

val generatedLicenseAssets = layout.buildDirectory.dir("generated/license-assets")
val generateLicenseAssets = tasks.register<org.gradle.api.tasks.Sync>("generateLicenseAssets") {
    from(rootProject.layout.projectDirectory.file("LICENSE"))
    from(rootProject.layout.projectDirectory.file("NOTICE"))
    into(generatedLicenseAssets.map { it.dir("licenses") })
}

// GitHub text-only file mutations can corrupt binary resources if a WebP is
// written as ordinary UTF-8 content. Keep the approved nimHUB artwork as
// deterministic Base64 chunks and reconstruct the exact verified WebP before
// Android resource merging. The size + digest assertions make a truncated logo
// a hard build failure instead of a broken launcher/login image in production.
val generatedBrandingRes = layout.buildDirectory.dir("generated/branding-res")
val nimHubLogoChunks = fileTree(layout.projectDirectory.dir("src/main/branding")) {
    include("nimhub_logo.*.b64")
}
val generateBrandingRes = tasks.register("generateBrandingRes") {
    val outputFile = generatedBrandingRes.map { it.file("drawable/nimhub_logo.webp") }
    inputs.files(nimHubLogoChunks)
    outputs.file(outputFile)

    doLast {
        val chunks = nimHubLogoChunks.files.sortedBy { it.name }
        require(chunks.size == 7) {
            "Expected 7 nimHUB logo chunks, found ${chunks.size}"
        }
        val encoded = chunks.joinToString(separator = "") { it.readText().trim() }
        val bytes = java.util.Base64.getDecoder().decode(encoded)
        require(bytes.size == 36_208) {
            "Unexpected nimHUB logo size: ${bytes.size}"
        }
        require(bytes.copyOfRange(0, 4).decodeToString() == "RIFF") {
            "nimHUB logo is not a RIFF WebP"
        }
        require(bytes.copyOfRange(8, 12).decodeToString() == "WEBP") {
            "nimHUB logo has an invalid WebP header"
        }
        val sha256 = java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
        require(sha256 == "60d37f4e8b1984ad999f4b20e4459259810a60fb0fa7ee0d748b26c80ae3c039") {
            "nimHUB logo digest mismatch: $sha256"
        }

        outputFile.get().asFile.apply {
            parentFile.mkdirs()
            writeBytes(bytes)
        }
    }
}

android {
    namespace = "org.quickping.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "org.quickping"
        minSdk = 23
        targetSdk = 36
        versionCode = 160170
        versionName = "2.6.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${providers.gradleProperty("QUICKPING_API_BASE_URL").orNull ?: "https://control-plane-production-a517.up.railway.app"}\"",
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets.getByName("main").assets.srcDir(generatedLicenseAssets)
    sourceSets.getByName("main").res.srcDir(generatedBrandingRes)

    packaging {
        jniLibs.useLegacyPackaging = true
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/LICENSE*",
            "/META-INF/NOTICE*",
        )
    }
}

tasks.named("preBuild").configure {
    dependsOn(generateLicenseAssets)
    dependsOn(generateBrandingRes)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(files("libs/libbox.aar"))

    implementation(platform(libs.compose.bom))
    androidTestImplementation(platform(libs.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.runtime)
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.code.scanner)
    implementation("androidx.credentials:credentials:1.7.0-alpha03")
    implementation("androidx.credentials:credentials-play-services-auth:1.7.0-alpha03")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.0")

    testImplementation(libs.junit)
    testImplementation("org.json:json:20250517")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)
}
