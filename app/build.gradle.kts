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
        minSdk = 24 // Android 7.0
        targetSdk = 36
        versionCode = 42
        versionName = "0.19.0-android7"
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
        // Android 7 : java.util.Base64 (code Java pur du serveur ESPHome et de Sendspin) n'arrive qu'avec Android 8
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    // Détection de « Hey Jarvis » sur la tablette (modèles openWakeWord au format TFLite)
    implementation("org.tensorflow:tensorflow-lite:2.16.1")
}
