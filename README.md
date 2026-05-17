# Notéa

Application Android de notation de TP. Elle permet de préparer un TP, importer une liste d'élèves, importer une grille XLSX, saisir les niveaux par critère, gérer des binômes et exporter une grille remplie.

## Fonctionnalités

- Création et reprise de plusieurs TP locaux.
- Import d'élèves depuis un fichier CSV.
- Import d'une grille de notation XLSX EP ou ETLV.
- Suggestions des listes et grilles déjà importées, sans rouvrir l'explorateur de fichiers.
- Saisie rapide des niveaux `NE`, `0`, `1`, `2`, `3`.
- Calcul automatique de la note sur 20 et de la moyenne.
- Mode binôme avec notes partagées.
- Consultation des descripteurs de compétences quand ils sont présents dans la grille.
- Ajustement des pondérations par critère.
- Thème clair/sombre.
- Export XLSX de la grille complétée pour un élève ou un binôme.
- Export groupé de toutes les grilles déjà notées dans un dossier `exports`.

## Organisation du code

- `MainActivity.kt` : écrans et interactions Android.
- `Models.kt` : modèles métier (`TpProject`, `Student`, `Criterion`, etc.).
- `GradingLogic.kt` : calculs de notes, groupes, binômes et regroupement des critères.
- `CsvStudentParser.kt` : lecture et nettoyage des listes d'élèves CSV.
- `XlsxGridParser.kt` : détection et lecture des grilles XLSX.
- `XlsxGridExporter.kt` : remplissage et export des grilles XLSX.
- `ProjectStore.kt` : sauvegarde locale des TP.
- `ExportPrefs.kt` : état temporaire nécessaire aux imports/exports Android.
- `UiTheme.kt` : palette et préférence de thème.
- `TextParsing.kt` et `StreamExtensions.kt` : petits utilitaires partagés.

## Développement

Pré-requis :

- Android Studio récent ou JDK compatible Gradle.
- SDK Android 36 installé.

Compiler l'application en debug :

```powershell
.\gradlew.bat assembleDebug
```

L'APK debug est généré dans :

```text
app/build/outputs/apk/debug/
```

## Données locales

Les TP sont stockés localement dans les préférences Android de l'application. Aucun compte ni serveur distant n'est utilisé.

## Notes de maintenance

La logique métier a été séparée de l'interface pour limiter les risques lors des futures évolutions. Pour modifier un calcul de note ou de binôme, commencer par `GradingLogic.kt`. Pour modifier l'apparence ou les écrans, commencer par `MainActivity.kt`.
