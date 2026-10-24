# tv-launcher

Un écran d'accueil minimaliste pour Android TV / Google TV, sans pubs ni recommandations :
heure, météo, indicateur VPN et une grille d'apps à la télécommande.

Kotlin + Jetpack Compose for TV. Testé sur une TV Philips sous Android TV 12.

Tuto complet : [Mon propre écran d'accueil Android TV, sans pubs](https://olivierthevet.fr/articles/launcher-android-tv-sans-pubs)

## Fonctions

- Heure et date, météo du jour via [Open-Meteo](https://open-meteo.com) (sans clé d'API)
- Indicateur « VPN connecté / déconnecté », qui ouvre l'app VPN
- Grille d'apps : « + » pour ajouter, appui long pour déplacer ou retirer
- Tuile Paramètres (seul accès aux réglages une fois l'accueil Google désactivé)

## Réglages

Dans `local.properties` (non versionné) :

```properties
weather.city=Troyes
weather.latitude=48.2973
weather.longitude=4.0744
```

Sans ces lignes, la météo est celle de Paris.

- App VPN ouverte par l'indicateur : `SURFSHARK` dans `Apps.kt`
- Apps affichées au premier lancement : `DEFAULT_APPS` dans `Apps.kt`

## Installation

Prérequis : Android Studio (pour le SDK, `adb` et le JDK), mode développeur et débogage
activés sur la TV.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"  # macOS
adb connect <ip-de-la-tv>:5555
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell cmd package compile -m speed -f fr.olivierthevet.tvlauncher
```

Remplacer l'accueil d'origine (vérifie d'abord son nom) :

```bash
adb shell cmd package resolve-activity --brief \
  -a android.intent.action.MAIN -c android.intent.category.HOME
adb shell pm disable-user --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity fr.olivierthevet.tvlauncher/.MainActivity
```

Revenir en arrière :

```bash
adb shell pm enable com.google.android.tvlauncher
```
