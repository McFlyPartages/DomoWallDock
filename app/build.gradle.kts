plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "app.walldock"
    compileSdk = 36

    defaultConfig {
        // Dérivé de WallDock (Home Assistant et Gladys Assistant) : identifiant propre, pour cohabiter avec l'appli d'origine.
        // Le code reste dans le paquet app.walldock (fusions depuis le projet d'origine plus simples).
        applicationId = "fr.mcflypartages.domowalldock"
        minSdk = 26
        targetSdk = 36
        versionCode = 45
        versionName = "0.20.1"
        // Pas de mise à jour par l'appli : les releases GitHub de WallDock sont signées avec une autre clé
        buildConfigField("boolean", "SELF_UPDATE", "false")
    }

    buildFeatures {
        buildConfig = true
    }

    // Clé de signature de DomoWallDock : gardée hors du dépôt, chemin et mot de passe passés par l'environnement
    val releaseStore = System.getenv("WALLDOCK_KEYSTORE")
    val releasePassword = System.getenv("WALLDOCK_KEY_PASSWORD")
    signingConfigs {
        if (releaseStore != null && releasePassword != null) {
            create("release") {
                storeFile = file(releaseStore)
                storeType = "PKCS12"
                storePassword = releasePassword
                keyAlias = "gladyswalldock" // clé créée sous l'ancien nom GladysWallDock, gardée telle quelle
                keyPassword = releasePassword
            }
        }
    }

    // Deux versions : « github » (APK GitHub / Obtainium, complète, mises à jour automatiques)
    // et « play » (Google Play : ni mise à jour par l'appli, ni service d'accessibilité).
    flavorDimensions += "store"
    productFlavors {
        create("github") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "false")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "PLAY_STORE", "true")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // Aperçu de l'écran des réglages sans tablette : ./gradlew testGithubDebugUnitTest
    // (Robolectric dessine le vrai écran en PNG dans app/build/preview)
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                it.maxHeapSize = "1g"
                it.systemProperty("preview.dir", layout.buildDirectory.dir("preview").get().asFile.path)
            }
        }
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Détection de « Hey Jarvis » sur la tablette (modèles openWakeWord au format TFLite)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
