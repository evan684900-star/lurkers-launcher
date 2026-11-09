# Lurkers — traduction FR, launcher et modloader : récapitulatif

État au 9 novembre 2026. À donner à Claude Code pour reprendre le projet.

## Contexte

- Jeu : **Lurkers** (lurkers.io), version **Steam Demo**, build **223.7**.
  Dossier : `C:\Program Files (x86)\Steam\steamapps\common\Lurkers Demo`
- Le jeu est en Java 8 + libGDX (backend **LWJGL3**), lancé par `lurkers.exe` (lanceur **packr**, fork NimblyGames) qui lit `lurkers.json`.
- Le code du jeu (`io/lurkers/...`) est **obfusqué** (noms comme `ay.class`), et les noms changent à chaque mise à jour. libGDX (`com/badlogic/...`) n'est pas obfusqué.
- Le dev du jeu a envoyé le fichier anglais `language.properties` pour qu'Evan fasse une traduction française officielle.

## Traduction

- `language_fr.properties` : traduction complète (424 textes, même structure que le fichier du dev), prête à envoyer au dev.
  - Apostrophes normales `'` (Evan ne veut pas les changer).
  - « fusil de précision » remplacé par « sniper » (lignes 216 et 358).
  - Les lignes vides (`HammerControlsText=`, etc.) sont des séparateurs : à laisser vides.
- À signaler au dev : la police des titres **Born2bSportyFS-16** n'a aucune lettre accentuée (« Nouveautés » s'affiche « Nouveauts »). La police principale (quanpixel) a tous les accents.
- Doute non vérifié : sur clavier AZERTY, l'aide affiche peut-être WASD alors qu'on joue en ZQSD.
- **Test validé** : le jeu lit `i18n/language_fr.properties` posé dans le dossier du jeu **avant** celui du .jar, et choisit le français automatiquement si Windows est en français. Plus besoin de modifier le .jar pour les textes.
- Textes écrits dans le code (pas dans le fichier de langue) : « PLAY », « Disconnect », « Games », « Map Editor », « players online », etc. Traduits par le modloader (`modloader/config/traduction.properties`).
  - **Ne jamais traduire « Friends »** : le jeu s'en sert comme identifiant, ça a cassé la liste d'amis (« invalid input parameter »).
- Pas traduisibles côté client : descriptions des modes de jeu et noms d'objets (viennent du serveur / scripts Lua ; dans `items.json` les noms servent d'identifiants).
- Notes de version (`misc/release_notes.json`) : traduites dans les anciennes versions modifiées du .jar.

## Fichiers dans le dossier du jeu

| Fichier | Rôle |
|---|---|
| `desktop-1.0-obf.jar` | .jar du jeu **modifié par Evan** (FR + boutons traduits + emojis dans `textures/output/texture.png`) |
| `desktop-1.0-obf-original.jar` | .jar d'origine build 223.7 (sauvegarde) |
| `desktop-1.0-obf-avant-emoji.jar` | version FR avant la modif emojis d'Evan |
| `i18n/language_fr.properties` | traduction lue par le jeu |
| `LurkersLauncher.exe` + `.json` | copie de `lurkers.exe` (packr lit `<nom de l'exe>.json`) qui lance le launcher avec le JRE du jeu |
| `launcher/lurkers-launcher.jar` | le launcher (Swing, Java 8) |
| `launcher/fr/language_fr.properties` | copie source de la traduction |
| `launcher/launcher.properties` | réglages du launcher |
| `launcher/lurkers.json.original` | sauvegarde de `lurkers.json` faite au 1er changement |
| `launcher/jeu.log` | sortie du jeu quand il est lancé par le launcher |
| `modloader/lurkers-modloader.jar` | agent Java (`-javaagent`) |
| `modloader/lib/modloader-core.jar` | cœur + API, chargé dans le ClassLoader du jeu |
| `modloader/mods/HUD.jar` | mod HUD : encadré bleu, FPS + heure |
| `modloader/config/traduction.properties` | textes du code à traduire |
| `modloader/config/hud.properties` | réglages du HUD (créé au 1er lancement) |
| `modloader/modloader.log` | journal du modloader |

`lurkers.json` contient actuellement `-javaagent:modloader/lurkers-modloader.jar` dans `vmArgs` (modloader activé).

## Architecture du modloader (v0.1.1)

1. **Agent** (`fr.evan.lurkers.loader`, chargeur système, aucune dépendance libGDX) :
   - `StringPatcher` : réécrit seulement les constantes UTF-8 du constant pool des classes `io/lurkers/game/rendering/ui/*` (correspondance exacte, aucune instruction modifiée).
   - `GameLoaderSpy` : repère le ClassLoader qui charge `com/badlogic/gdx/*` (packr charge le jeu dans un `URLClassLoader` à part, pas dans le classpath système), puis charge `modloader-core.jar` avec ce ClassLoader comme parent.
2. **Cœur** (`fr.evan.lurkers.core` + API `fr.evan.lurkers.api`) :
   - `ModManager` : charge les .jar de `modloader/mods` via `ServiceLoader` (`META-INF/services/fr.evan.lurkers.api.LurkersMod`) ; un mod qui plante est désactivé, le jeu continue.
   - `RenderHook` : attend `Gdx.app`, puis par réflexion remplace `Lwjgl3Window.listener` (champ final) par une enveloppe qui appelle le rendu du jeu puis les mods (SpriteBatch en coordonnées écran ; la zone d'affichage GL est sauvegardée et restaurée).
3. **API d'un mod** (`LurkersMod`) : `getName`, `onLoad(ModContext)`, `onGameReady()`, `onRender(SpriteBatch, w, h)`, `onDispose()`. `ModContext` : `getConfigDir`, `loadConfig(name, defaults)`, `log`.

## Launcher (v0.1.1)

- Swing, style bleu et blanc (dégradé #1347D1 → #4F8BFF, cartes blanches, interrupteurs), police Segoe UI.
- Options : traduction FR (copie/supprime `i18n/language_fr.properties`), modloader (ajoute/retire `-javaagent` dans `lurkers.json`), liste des mods (renommage `.jar` ↔ `.jar.disabled`), fermer au lancement, bouton JOUER (lance `lurkers.exe`, sortie redirigée vers `launcher/jeu.log`).
- Au démarrage, réapplique ce qui était activé si une mise à jour Steam a tout effacé.
- Le modloader et la traduction marchent aussi en lançant depuis Steam (tout est dans les fichiers).

## Où on en est

- **v0.1.0** : le jeu plantait au lancement via le launcher. Le journal montrait que le HUD ne trouvait pas libGDX (`NoClassDefFoundError: com/badlogic/gdx/graphics/g2d/Batch`) à cause du ClassLoader de packr. Autre cause probable : le launcher ne vidait pas la sortie du jeu (blocage quand le tampon est plein).
- **v0.1.1** (installée, **pas encore testée en jeu**) : modloader découpé en agent + cœur, sortie du jeu redirigée vers un fichier. Simulé avec un faux ClassLoader « packr » : la traduction et le chargement du HUD marchent. Le branchement du rendu n'a pas pu être testé sans le vrai jeu.

## Prochaines étapes

1. Lancer via le launcher, puis lire `modloader/modloader.log` et `launcher/jeu.log`. Vérifier que l'encadré bleu s'affiche en haut à droite.
2. Tester aussi le lancement depuis Steam. Si le bouton JOUER pose problème, le faire passer par Steam (`steam://rungameid/<appid>`, l'appid se trouve dans `steamapps/appmanifest_*.acf`).
3. Idées : resource packs (textures/sons posés dans le dossier du jeu, à tester comme pour `i18n`), d'autres infos de HUD, une icône pour le launcher.
4. Envoyer `language_fr.properties` au dev, avec la remarque sur la police des titres.

## Règles

- Pas de triche : aucun mod qui donne un avantage (radar, voir à travers les murs, visée auto, vol, vitesse…), aucun contournement du premium. Demander au dev avant d'utiliser des mods en ligne ou de partager un .jar modifié.
- Ne jamais modifier les .jar du jeu à la main sans sauvegarde ; préférer les fichiers posés à côté du jeu et le modloader.
- Une mise à jour Steam remplace `desktop-1.0-obf.jar` (les modifs d'Evan dedans, comme les emojis, sont alors perdues).

## Compiler

Sources dans `lurkers-launcher-source.zip`. Il faut un JDK (le JRE 8 du jeu n'a pas `javac`). `build.sh` compile en `--release 8` avec le .jar du jeu dans `libs/game.jar` comme dépendance ; le résultat va dans `dist/`.
