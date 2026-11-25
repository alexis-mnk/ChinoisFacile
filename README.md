# ChinoisFacile — Apprendre le chinois mandarin (HSK 1 à 6)

Application Android native d'apprentissage du chinois mandarin destinée aux francophones, inspirée de Duolingo, **100 % hors-ligne**, gratuite, sans publicité et sans système de vies.

---

## Fonctionnalités principales

- **Programme complet HSK 1 à HSK 6 (5 012 mots uniques)** :
  - **HSK 1 :** 150 mots (6 unités, 30 leçons)
  - **HSK 2 :** 148 mots (7 unités, 30 leçons)
  - **HSK 3 :** 298 mots (7 unités, 60 leçons)
  - **HSK 4 :** 601 mots (7 unités, 78 leçons)
  - **HSK 5 :** 1 298 mots (8 unités, 133 leçons)
  - **HSK 6 :** 2 517 mots (8 unités, 255 leçons)
  - La Leçon 1 de chaque niveau HSK est débloquée d'emblée pour commencer directement au niveau souhaité.
- **Parcours ludique façon Duolingo** : chemin sinueux par unités avec nœuds circulaires 3D, aperçu des mots avant démarrage, bouton *Reprendre* et **Révision rapide** ciblée.
- **Sessions de 10 exercices variés** :
  - QCM **Hanzi → Français** (option pour afficher/masquer le pinyin en un geste)
  - QCM **Audio → Hanzi** (`TextToSpeech` `zh-CN` en vitesse normale **1.0x** et lente **0.7x**)
  - **Remise en ordre de mots** pour former des phrases complètes
  - Feedback immédiat avec explication, phrase d'exemple écoutable et réinsertion des erreurs sans perte de vies.
- **Répétition espacée (SM-2)** : calcul local des intervalles de révision pour chaque mot, suivi de la série quotidienne et des XP.
- **Lexique complet (Grille & Liste compacte)** :
  - Vue grille 3 colonnes ou liste dense, barre de progression segmentée, filtres par statut (*Tous*, *À revoir*, *En cours*, *Maîtrisés*, *Nouveaux*), recherche instantanée par hanzi, pinyin (avec ou sans tons/espaces) ou français, et fiche détaillée complète au clic.
- **Audio & Effets sonores 100 % locaux** : voix chinoise via Android `TextToSpeech` (`zh-CN`) + effets sonores doux via `SoundPool` sur le canal média.
- **Mode clair & mode sombre** : sélecteur *Système / Clair / Sombre* avec palette anthracite reposante et barres système adaptatives.

---

## Stack technique

- **Langage :** Kotlin (100 % Kotlin, zéro Java)
- **Interface :** Jetpack Compose + Material 3 (Navigation Compose à 4 onglets)
- **Architecture :** MVVM unidirectionnelle (`StateFlow`), injection manuelle via `AppContainer`
- **Persistance locale :** Room (SQLite) + DataStore Preferences
- **Réseau :** Aucun (zéro dépendance réseau, aucune permission `INTERNET`)
- **SDK :** `minSdk = 26`, `targetSdk = 35`

---

## Installation depuis GitHub Releases

1. Rendez-vous sur la page des versions : [Releases · alexis-mnk/ChinoisFacile](https://github.com/alexis-mnk/ChinoisFacile/releases).
2. Téléchargez le fichier `.apk` de la dernière release (ex. `ChinoisFacile-v1.0.0.apk`) sur votre smartphone Android (Android 8.0 / API 26 minimum).
3. Ouvrez le fichier `.apk` et autorisez l'installation depuis cette source si Android vous le demande.
4. *(Recommandé pour l'audio)* Vérifiez dans **Paramètres Android > Synthèse vocale** (ou via l'onglet **Paramètres** de l'application) que la langue **Chinois (Chine / zh-CN)** est bien téléchargée pour l'écoute hors-ligne.

---

## Compilation locale

### Prérequis
- Android Studio Ladybug / Meerkat ou plus récent (avec **JDK 17+** et **Android SDK API 35**)

### Cloner et compiler en debug
```bash
git clone https://github.com/alexis-mnk/ChinoisFacile.git
cd ChinoisFacile

# Lancer les tests unitaires JVM
./gradlew testDebugUnitTest

# Compiler l'APK debug
./gradlew assembleDebug
```
*(Sous Windows PowerShell, utilisez `.\gradlew.bat`.)*

### Générer un APK Release signé
Créez un fichier `keystore.properties` à la racine du projet (exclu du suivi Git via `.gitignore`) :

```properties
storeFile=chemin/vers/votre-cle.jks
storePassword=votre_mot_de_passe_keystore
keyAlias=votre_alias
keyPassword=votre_mot_de_passe_cle
```

Puis lancez :
```bash
./gradlew assembleRelease
```
L'APK signé est généré dans `app/build/outputs/apk/release/`.

---

## Outillage catalogue (`tools/`)

Les fichiers JSON embarqués dans [app/src/main/assets/catalog/](app/src/main/assets/catalog/) sont déjà générés et prêts à l'emploi.  
Les scripts Python hors-application permettent de régénérer ou valider les catalogues sur PC :
- `tools/validate_catalog.py` : vérifie l'absence de doublons intra/inter-niveaux, le pinyin tonal Unicode et la segmentation des `tokens`.
- `tools/build_hsk_catalogs.py` : pipeline reproductible (HSK 2.0 + CFDICT + Tatoeba + `jieba` + `pypinyin`).
