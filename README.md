# Super StopWatch

**Chronomètre Wear OS (Galaxy Watch 7 et autres montres Wear OS 3+)** — version **v11**.
Conçu par **Raphaël GARIVIER avec Claude (Anthropic), octobre 2026**.

> *English summary:* Super StopWatch is an open-source stopwatch app for Wear OS 3+ watches (built and tested for the Samsung Galaxy Watch 7).
> It adds laps on the physical bottom button, a tile, a complication, history with QR-code export, an exercise/rest cycle,
> a touch lock, 3 languages (FR/EN/ZH), 10 logos and a very customizable layout. Install it with `adb` (see below).
> The code was generated step by step with Claude and compiled by GitHub Actions; it has **not** been tested on every watch.

Cette application n'est **pas** affiliée à Samsung. Le code a été écrit avec Claude à partir d'une conversation, puis compilé par GitHub Actions ;
il n'a pas pu être testé sur toutes les montres (voir « Limites »).

---

## Fonctionnalités

**Chronomètre**
- Start / Stop / Tour / Reset / Annuler le dernier tour (cercles à l'écran).
- **Bouton physique du bas (Retour)** : *tour* quand le chrono tourne, *start* sinon. Si l'écran est éteint ou vient d'être rallumé, le bouton ne fait que le réveiller (pas de tour fantôme).
- Reset et effacement de l'historique **protégés par un appui long** (durée réglable, snake de progression + vibration).
- Liste des tours : n°, temps total, durée du tour, allure (option), écart avec le tour précédent ; triangles ▲ / ▼ **optionnels** (plus rapide / plus lent que le tour précédent, ou que le tour de même rang de la série précédente en suivi avancé) ; couleurs en dégradé (couleur de référence → teinte intermédiaire → teinte opposée) ; moyenne en option.
- Défilement de la liste avec la **bague rotative / tactile Samsung** (native, 1 ligne par cran).
- Écart chrono → cercles réglable de −10 % à 100 % (0 % = les chiffres touchent les cercles).
- Précision d'affichage : jamais plus que les **dixièmes** ; mode « temps en secondes » (`101.2`).

**Écran et énergie**
- Bague extérieure avec un « snake » (1 tour = 1 minute au départ, puis = meilleur tour ; en suivi avancé = segment d'exercice/repos en cours).
- **Mode éco** : tout en niveaux de gris, mise à jour 1 fois par seconde, bague et always-on display désactivés, luminosité réglable (0 % au départ), vibrations réduites.
- **Affichage permanent** optionnel (avec avertissement) : l'écran reste allumé tant que le chrono tourne, malgré la veille du système, puis passe en veille douce (noir, chiffres gris, luminosité minimale) après un délai réglable ; un toucher ou le bouton le réveille. **Mode clair** optionnel (avec avertissement).
- Aucun réveil quand l'écran est éteint ; enregistrement des tours « en ajout seul » en mode éco.

**Sécurité**
- **Verrou tactile** : tant que le chrono tourne, seul le maintien d'une icône agit ; déverrouillage par maintien du cadenas ou automatiquement après N minutes.
- **Appli fermée ou « Forcer l'arrêt »** : le chrono est arrêté et remis à zéro dès que l'appli (ou le tile / la complication) se relance, et la séance est enregistrée. Deux détections indépendantes : une *sentinelle* (alarme lointaine qui disparaît à l'arrêt forcé) et le journal de sortie du système. Quitter simplement l'appli ne coupe pas le chrono ; un redémarrage de la montre non plus.
- Bouton « **Stop + Reset** » sur la notification de la pastille du cadran.
- **Arrêt automatique** d'un chrono oublié après N minutes.

**Suivi avancé** (option)
- Distance par tour (précision réglable de 0,1 m à 1 000 m) → allure `3'32/km`.
- Cycle d'exercice : *temps d'exercice* × *répétitions*, puis *repos*, puis on recommence ; vibration (comme pour un tour) à chaque frontière, même écran éteint, désactivable. Temps d'exercice = 0 → pas d'objectif.

**Historique**
- Chaque séance est enregistrée au Reset (nommée automatiquement par sa date et son heure). **Glisser vers la droite** : ouvre ; maintien : supprime.
- Page de séance : statistiques minimalistes (meilleur, pire, moyenne, médiane, écart-type, régularité, barres), liste des tours.
- **Codes QR** : liste Python des durées de tours en secondes au centième, par ex. `[73.12, 44.23, 20.00, 33.03]`. Version (5 → 25) et niveau de correction réglables ; découpage en blocs si nécessaire, défilement gauche/droite.

**Personnalisation** (interrupteur dédié dans les réglages)
- Langues : **Français, English, 中文 (mandarin simplifié)**.
- **10 logos** (icône de l'appli ; pictogramme du tile teinté par la couleur de référence ; complication ; notification).
- Couleur de référence RVB ; toutes les couleurs non grises s'en déduisent.
- Tailles (chrono, cercles, tours), espacements en **% de la hauteur de l'écran** (0 % = les objets se touchent), colonnes au choix, largeur et proportion haut/bas, mode gaucher, fondu des tours, etc.
- **Chaque curseur** a un sous-menu **min / max** (appui long) : 100 pas entre vos bornes → on peut affiner autant qu'on veut.
- **Aide complète** (rubriques) et, sur **chaque réglage, un appui long affiche son explication** (langue choisie) ; pour les curseurs, l'explication est au-dessus du sous-menu min / max. Durées affichées en s / min / h. Retour au même endroit de la liste après un sous-menu.
- Tile et complication.

---

## Compatibilité

| | |
|---|---|
| ✅ Wear OS 3 / 4 / 5 (Android 11+) | Galaxy Watch 4 → 7 / Ultra, Pixel Watch, TicWatch, Fossil Gen 6… |
| ❌ Tizen (Galaxy Watch 3 et avant), Apple Watch, Garmin, Fitbit | non compatibles |

Le bouton physique « Retour » n'existe pas sur toutes les montres (pas sur Pixel Watch). La bague tactile est propre à Samsung.

---

## Installation (Windows, sans Android Studio)

### 1. Récupérer l'APK
Depuis l'onglet **Releases** du dépôt GitHub : télécharger `SuperStopWatch-v11-buildN.apk`
(ou onglet **Actions → dernière exécution → Artifacts**).

### 2. Installer *platform-tools* (adb)
1. Télécharger *SDK Platform-Tools for Windows* : <https://developer.android.com/tools/releases/platform-tools>
2. Dézipper (par ex. dans `C:\platform-tools`) et y copier l'APK.
3. Ouvrir une invite de commandes dans ce dossier (barre d'adresse de l'Explorateur → taper `cmd` → Entrée).

### 3. Activer le débogage sur la montre
1. *Paramètres → À propos de la montre → Infos logiciel* → toucher 5 fois « Version du logiciel ».
2. *Options pour les développeurs* → activer **Débogage ADB** puis **Débogage via Wi-Fi**.
3. Montre et PC sur le **même réseau Wi-Fi**.

### 4. Connexion et installation
```bat
adb pair ADRESSE_IP:PORT_APPAIRAGE        :: saisir le code à 6 chiffres affiché sur la montre
adb connect ADRESSE_IP:PORT               :: le port affiché sur l'écran principal du débogage Wi-Fi (différent du précédent)
adb devices                               :: la montre doit apparaître
adb install SuperStopWatch-v11-buildN.apk
```

### 5. Mettre à jour
Chaque compilation GitHub est signée avec une **clé de debug différente** : une nouvelle version ne peut donc pas écraser l'ancienne.
```bat
adb uninstall com.example.chrono
adb install SuperStopWatch-v11-buildN.apk
```
⚠ Les réglages et l'historique sont perdus à la désinstallation. (Une signature stable est une évolution possible, voir `PROJET_POUR_IA.txt`.)

### 6. Ajouter le tile et la complication
- **Tile** : depuis le cadran, balayer vers la gauche → *Ajouter un tile* → « Super StopWatch ».
- **Complication** : appui long sur le cadran → *Modifier* → choisir « Super StopWatch » (texte court ou icône).

### Au premier lancement
Accepter la permission de **notification** : elle sert à la pastille « chrono en cours » sur le cadran.

---

## Compiler soi-même (sans Android Studio)

Le dépôt contient un workflow **GitHub Actions** (`.github/workflows/build.yml`) :
1. Pousser le code sur la branche `main`.
2. Le workflow compile (`gradle assembleRelease`, minification R8), renomme l'APK et publie une **Release** (`v11-buildN`).
3. Si la publication échoue : *Settings → Actions → General → Workflow permissions → Read and write permissions*.

Avec Android Studio : ouvrir le dossier, `Build > Build Bundle(s) / APK(s)`.

Pile technique : Kotlin 1.9, Jetpack Compose + Wear Compose, Tiles (ProtoLayout), Complications, Ongoing Activity, ZXing (QR). `minSdk 30`, `targetSdk 34`.

---

## Gestes à connaître

| Geste | Effet |
|---|---|
| Bouton du bas | tour (chrono en marche) / start |
| Maintenir **Reset** | remise à zéro (+ enregistrement dans l'historique) |
| Maintenir un **réglage** (interrupteur, case, curseur) | explication (curseur : puis sous-menu min / max) |
| Glisser une séance vers la **droite** | l'ouvrir (historique) |
| Maintenir une séance | la supprimer |
| Maintenir **le cadenas** | déverrouiller le tactile |
| Maintenir l'icône **réglages** (en activité) | ouvrir les réglages |
| Bague rotative / tactile | faire défiler les tours |
| Bouton du bas dans un menu | retour |

---

## Limites connues

- L'application n'a **pas été testée** sur toutes les montres ; le code a été compilé uniquement par GitHub Actions.
- Le bouton du haut (Accueil) est réservé au système : une appli ne peut pas le lire.
- Un appui long sur le bouton du bas est géré par Samsung (Wallet) : il n'est donc pas utilisé.
- L'icône de l'appli est figée à l'installation : les 10 logos sont des alias de lancement (couleurs fixes) ; seul le tile suit la couleur de référence.
- La détection de la fermeture dépend du système : « Forcer l'arrêt » est détecté par la sentinelle ; la fermeture depuis les applis récentes ne l'est que par le journal de sortie du système (selon le fabricant). Aucun des deux n'a été vérifié sur toutes les montres.
- L'affichage permanent garde l'écran allumé (aucune permission requise) : il consomme plus de batterie et peut marquer un écran AMOLED.
- Un glissement vers la droite peut, selon la montre, être concurrencé par le geste « retour » du système.
- Les codes QR trop denses (versions élevées) peuvent être illisibles sur une petite montre.

---

## Structure du dépôt

```
app/src/main/java/com/example/chrono/   code Kotlin (voir PROJET_POUR_IA.txt)
app/src/main/res/                       logos vectoriels, chaînes (FR/EN/ZH)
apercu_logos.png / .svg                 aperçu des 10 logos
.github/workflows/build.yml             compilation + release automatique
README.md                               ce fichier
PROJET_POUR_IA.txt                      description complète pour reprendre le projet avec une IA
```

## Crédits

Idée, cahier des charges et tests : **Raphaël GARIVIER**. Génération du code et de la documentation : **Claude (Anthropic)**, octobre 2026.
Bibliothèques : AndroidX / Jetpack Compose, Wear Compose, ZXing (Apache 2.0).
