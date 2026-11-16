# GladysWallDock — Politique de confidentialité

GladysWallDock est un dérivé de [WallDock](https://github.com/psycho1807/WallDock) adapté pour Gladys Assistant.

**En bref : GladysWallDock ne collecte aucune donnée personnelle. Rien n'est envoyé au développeur ni à un tiers.** L'appli échange uniquement avec les services que vous configurez sur votre réseau : votre Gladys Assistant, votre broker MQTT et, si vous l'utilisez, votre Home Assistant.

## Données traitées sur la tablette

- **Réglages** (adresse du tableau de bord, code administrateur, adresse et identifiants du broker MQTT…) : enregistrés uniquement sur la tablette, en clair. Une sauvegarde n'est créée que si vous la demandez, dans un fichier que vous choisissez ; elle contient ces identifiants.
- **Caméra** (détection de mouvement, si vous l'activez) : les images sont analysées sur la tablette, puis immédiatement oubliées. Aucune image n'est enregistrée ni envoyée ; seul l'état « mouvement / pas de mouvement » est publié sur votre broker MQTT et transmis à votre Home Assistant.
- **Capteurs publiés sur votre broker MQTT** : batterie, température de la batterie, en charge, charge demandée, mouvement, état de l'écran.
- **Micro** (assistant vocal Jarvis, si vous l'activez, avec Home Assistant) : le son est analysé sur la tablette pour reconnaître le mot d'activation ; après ce mot, votre phrase est envoyée à votre Home Assistant. Aucun enregistrement n'est conservé.
- **Capture d'écran** : faite uniquement quand votre Home Assistant la demande, et envoyée seulement à celui-ci.
- **Service d'accessibilité** (facultatif) : sert à savoir si quelqu'un utilise l'appli ouverte depuis le dock. Il ne lit pas le contenu des autres applis.

## Connexions Internet faites par l'appli

- Vers les adresses que vous configurez (tableau de bord, broker MQTT, Immich, Home Assistant).
- Si vous activez « Hey Jarvis » sur la tablette : téléchargement, une seule fois, de deux modèles de reconnaissance vocale publics depuis GitHub (projet openWakeWord).

L'appli ne vérifie pas les mises à jour sur Internet.

## Ce que GladysWallDock ne fait pas

Pas de compte, pas de publicité, pas de statistiques d'utilisation, pas de traceur, pas de revente de données.
