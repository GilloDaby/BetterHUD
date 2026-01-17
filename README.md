# BetterHUD

Affiche l'état de l'équipement directement au-dessus de la hotbar :
- icônes textuelles pour chaque pièce d'armure
- pourcentage de durabilité avec code couleur (vert > 60 %, jaune > 30 %, rouge sinon)

## Build
```bash
cd BetterHUD
./gradlew jar
```
Le JAR généré inclura `plugin.json` et `manifest.json`. Placez-le dans le dossier des plugins du serveur Hytale.
