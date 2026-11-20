# IR PRO TV

Lecteur IPTV (Xtream / M3U) pour box Android TV — style console, contenu français en priorité.

## Télécharger

Dernière version (lien fixe, utilisable avec Downloader) :

https://github.com/alaeddine133/IRproTV/releases/latest/download/IRPRO-TV.apk

## Fonctionnement

- Chaque modification envoyée sur `main` lance GitHub Actions, qui construit l'APK et publie une nouvelle version.
- La clé de signature est rangée dans les secrets GitHub (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`). Elle n'est jamais dans le code.

## Avancement

- [x] Étape 1 — Fondations (projet Android TV, icône, bannière, build automatique)
- [ ] Étape 2 — Intro
- [ ] Étape 3 — Ajout de playlist
- [ ] Étape 4 — Chargement du contenu
- [ ] Étape 5 — Accueil console
- [ ] Étape 6 — TV en direct
- [ ] Étape 7 — Lecteur plein écran
- [ ] Étape 8 — Guide TV
- [ ] Étape 9 — Films
- [ ] Étape 10 — Séries
- [ ] Étape 11 — Mise à jour automatique
- [ ] Étape 12 — Ajout par téléphone (QR)
- [ ] Étape 13 — Finitions
- [ ] Étape 14 — Code Downloader
