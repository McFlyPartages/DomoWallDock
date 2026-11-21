# DomoWallDock

> **Branche `android7` : version figée pour les tablettes Android 7.x.** Elle ne reçoit que des corrections ponctuelles ; les nouveautés arrivent sur `main` (Android 8 minimum). Pour une tablette Android 8 ou plus, prenez les versions de `main`.

**Transformez une tablette Android en tableau de bord mural pour [Home Assistant](https://www.home-assistant.io/) ou [Gladys Assistant](https://gladysassistant.com/).**

DomoWallDock (anciennement GladysWallDock) est un dérivé (*fork*) de [WallDock](https://github.com/psycho1807/WallDock), de psycho1807. WallDock est pensé pour Home Assistant ; tout ce qu'il sait faire reste présent ici, et Gladys Assistant s'y ajoute. Licence GPL-3.0, comme l'original.

## Ce que fait DomoWallDock

**Home Assistant ou Gladys, au choix**
- Réglages → « Tableau de bord affiché » : un bouton radio choisit la plateforme lancée. Chacune a sa colonne (côte à côte sur un écran large ou une tablette en paysage, l'une sous l'autre sinon) : adresse, compte, mot de passe et tableau de bord par défaut (facultatif, ex. `/lovelace/0?kiosk` ou `/dashboard/salon`).
- **Connexion automatique** : avec un compte et un mot de passe, la tablette se connecte toute seule quand la page de connexion s'affiche (Home Assistant : comme « Rester connecté » ; pas avec l'authentification à deux facteurs). Conseil : un compte dédié à la tablette, sans droits d'administration.
- Les fonctions qui passent par Home Assistant (Jarvis, proxy Bluetooth, caméras, sons) restent disponibles quand Gladys est affiché, tant que l'adresse de HA est remplie.

**Proxy Bluetooth ESPHome** (Home Assistant)
- Réglages → « Proxy Bluetooth (ESPHome) » : la tablette écoute les appareils Bluetooth Low Energy autour d'elle (thermomètres Xiaomi, capteurs BTHome, balises…) et transmet leurs annonces à Home Assistant par l'intégration ESPHome, exactement comme un module ESPHome `bluetooth_proxy`. Les appareils apparaissent dans HA comme s'ils étaient à portée de son propre Bluetooth.
- Écoute seule : HA ne peut pas se *connecter* aux appareils à travers la tablette (appareils qui demandent une connexion GATT, appairage).
- Autorisations Bluetooth et position demandées à l'enregistrement (sans la position, Android masque les balises). L'écoute ne tourne que lorsque HA est abonné.
- Gladys ne sait pas utiliser un proxy Bluetooth : l'option ne sert qu'avec Home Assistant.

**Avec Gladys Assistant**
- **Connexion par MQTT** (commun aux deux plateformes) : réglages → « MQTT », avec l'adresse du broker, l'utilisateur et le mot de passe (dans Gladys : Intégrations → MQTT → Configuration). La tablette s'annonce toute seule (format de découverte MQTT de Home Assistant, compris par les versions récentes de Gladys 5) : Intégrations → MQTT → appareils découverts, ajouter « DomoWallDock … ».
- Dans Gladys : batterie, température de la batterie, en charge, mouvement, écran (allumer / éteindre), boutons « Recharger » et « Retour au tableau de bord ».
- **Gestion de la charge** pour une tablette toujours branchée : réglages → « Batterie ». Android n'autorise pas une appli à couper la charge ; DomoWallDock publie donc un capteur « Charge demandée » (oui sous le seuil bas, 30 % par défaut ; non au-dessus du seuil haut, 80 % par défaut) et deux scènes Gladys pilotent la prise connectée du chargeur :
  - « Charge demandée » passe à 1 → allumer la prise ;
  - « Charge demandée » passe à 0 → éteindre la prise.

  Si la tablette disparaît du réseau (plantage, Wi-Fi coupé), le broker remet tout seul « Charge demandée » à oui (testament MQTT), pour que la batterie ne se vide pas.
- **Écran piloté par la caméra frontale** : l'écran s'allume quand quelqu'un passe et s'éteint après un délai sans mouvement ni toucher (réglages → détection de mouvement → délai en minutes). Les images sont analysées sur la tablette et ne sont jamais envoyées ni enregistrées.

**Kiosque** (hérité de WallDock)
- Plein écran, barres système masquées, écran toujours allumé, réglages protégés par un code.
- Dock d'applis autorisées, retour automatique au tableau de bord après inactivité.
- Peut devenir l'écran d'accueil de la tablette ; se relance au démarrage (coupure de courant).
- Zoom du tableau de bord (50 à 200 %), écran de veille (horloge, page au choix, diaporama Immich ou dossier de photos).
- Planification : écran éteint chaque nuit. Liste blanche d'adresses. Écran de secours quand le serveur ne répond pas.
- Sauvegarde et restauration des réglages dans un fichier (les sauvegardes de WallDock sont acceptées).

**Avec Home Assistant** (hérité de WallDock, toujours fonctionnel)
- Intégration native ESPHome chiffrée, assistant vocal Jarvis, lecteur multimédia, lecteur Music Assistant synchronisé (Sendspin), réveils, calendrier. Ces fonctions ont besoin de Home Assistant ; avec Gladys seul, décochez l'écoute de « Hey Jarvis » dans les réglages.

## Sujets MQTT

`<appareil>` vaut `domowalldock-xxxxxx` (affiché dans les réglages).

| Sujet | Sens | Valeurs |
|---|---|---|
| `domowalldock/<appareil>/battery` | état | 0 à 100 |
| `domowalldock/<appareil>/battery_temperature` | état | °C |
| `domowalldock/<appareil>/charging` | état | `ON` / `OFF` |
| `domowalldock/<appareil>/charge_wanted` | état | `ON` / `OFF` (si la gestion de la charge est activée) |
| `domowalldock/<appareil>/motion` | état | `ON` / `OFF` |
| `domowalldock/<appareil>/screen` | état | `ON` / `OFF` |
| `domowalldock/<appareil>/screen/set` | commande | `ON` / `OFF` |
| `domowalldock/<appareil>/reload/set` | commande | n'importe quelle valeur |
| `domowalldock/<appareil>/dashboard/set` | commande | n'importe quelle valeur |

## Installation

1. Installer `DomoWallDock-x.y.z.apk` sur la tablette (autoriser l'installation depuis cette source). L'appli a son propre identifiant (`fr.mcflypartages.domowalldock`) : elle peut cohabiter avec WallDock.
2. Au premier lancement : plateforme (Home Assistant ou Gladys), son adresse (et si besoin compte, mot de passe, tableau de bord), code administrateur, applis du dock, puis **Enregistrer**.
3. Dans les réglages : écran d'accueil, affichage par-dessus les autres applis, caméra (détection de mouvement), puis la partie MQTT.
4. Désactiver l'optimisation de batterie d'Android pour DomoWallDock, sinon le système peut couper l'appli ([dontkillmyapp.com](https://dontkillmyapp.com) détaille chaque marque).

**Mises à jour** : à la main, en installant le nouvel APK par-dessus l'ancien (même clé de signature, les réglages sont conservés). L'appli ne se met pas à jour toute seule.

## Compilation

`gradle assembleGithubRelease` avec Gradle 8.14, JDK 17 et le SDK Android 36. Pour signer, donner le chemin du magasin de clés (PKCS12, alias `gladyswalldock`, nom d'origine de la clé) dans `WALLDOCK_KEYSTORE` et son mot de passe dans `WALLDOCK_KEY_PASSWORD` ; la clé n'est pas dans le dépôt. Sans elles, l'APK n'est pas signé (`assembleGithubDebug` donne un APK de test).

Le code garde le paquet `app.walldock` de l'original, pour reprendre facilement ses évolutions. Le client MQTT (`app/src/main/java/app/walldock/mqtt`) et le serveur ESPHome (`…/esphome`) sont en Java pur et se testent sur une JVM.

## Crédits

- **DomoWallDock** : adapté et maintenu par **McFly** — site [mcflypartages.fr](https://mcflypartages.fr), Mastodon `mcflypartages.fr`, [Facebook](https://www.facebook.com/McFlyPartages), [X](https://x.com/McFlyPartages).
- **[WallDock](https://github.com/psycho1807/WallDock)** de psycho1807 (GPL-3.0) : toute la base de l'appli. DomoWallDock n'est ni affilié à son auteur ni soutenu par lui ; WallDock Pro est un produit distinct.
- Mot « Stop » : modèle [microWakeWord](https://github.com/kahrendt/microWakeWord) de Kevin Ahrendt (Apache 2.0) ; extraction des caractéristiques portée du micro frontend de TensorFlow Lite Micro (Apache 2.0).
- Mot d'activation : modèles [openWakeWord](https://github.com/dscripka/openWakeWord) (téléchargés au premier usage, CC BY-NC-SA 4.0), exécutés avec TensorFlow Lite.
- Protocoles : [API native ESPHome](https://esphome.io/components/api.html), découverte MQTT de Home Assistant.

## English

**DomoWallDock** (formerly GladysWallDock) turns an Android tablet into a wall panel for [Home Assistant](https://www.home-assistant.io/) or [Gladys Assistant](https://gladysassistant.com/), chosen with a radio button in the settings, with automatic sign-in. It is a fork of [WallDock](https://github.com/psycho1807/WallDock) by psycho1807 (GPL-3.0). It adds an ESPHome Bluetooth proxy (the tablet forwards nearby BLE advertisements to Home Assistant, listening only) and, for Gladys: MQTT connection with automatic discovery (battery, charging, motion, screen switch, two buttons), charge management for always-plugged tablets (a “Charge wanted” sensor drives the charger's smart plug through two Gladys scenes), and a screen that turns on with motion and off when nobody is there. Everything WallDock does for Home Assistant is still there.

License: GPL-3.0.
