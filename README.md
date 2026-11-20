# IR PRO TV

Lecteur IPTV (Xtream / M3U) pour box Android TV — style console, contenu français en priorité.

## Télécharger

Dernière version (lien fixe, utilisable avec Downloader) :

https://github.com/alaeddine133/IRproTV/releases/latest/download/IRPRO-TV.apk

## Fonctionnement

- Chaque modification envoyée sur `main` lance GitHub Actions, qui construit l'APK et publie une nouvelle version.
- La clé de signature est rangée dans les secrets GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`). Elle n'est jamais dans le code.

## Installer avec Downloader

1. Va sur **aftv.news**, colle le lien ci-dessus : tu obtiens un code en chiffres.
2. Sur la box : Downloader → tape le code → Go → Installer.
3. Le code reste valable pour toujours (le lien pointe toujours vers la dernière version).

## Fonctions

- Intro vidéo 4K/1080p (complète au 1er lancement, courte ensuite) avec le vrai nombre de chaînes
- Ajout de playlist : téléphone (QR code), Xtream Codes, lien M3U — plusieurs playlists
- Contenu français en priorité (FR, France, VF, VOSTFR, TNT…), réglable
- Accueil console, TV en direct avec aperçu, lecteur plein écran (zapping, liste rapide, favoris)
- Guide TV (Xtream ou XMLTV) avec rappels
- Films et séries séparés : rangées, fiches, reprise, épisode suivant automatique
- Lecteurs : ExoPlayer (+ décodeur audio FFmpeg pour Dolby/DTS) et VLC en secours automatique
- Recherche, paramètres complets, sons de navigation
- Mise à jour du contenu à chaque lancement + bouton manuel
- Mise à jour automatique de l'appli depuis GitHub

## Avancement

- [x] Étapes 1 à 13
- [ ] Étape 14 — Créer le code Downloader sur aftv.news (à faire par le propriétaire)

## Licences

Utilise ExoPlayer (Apache 2.0), VLC (LGPL 2.1) et le décodeur FFmpeg de Jellyfin (GPL 3). Le code source est public dans ce dépôt.
