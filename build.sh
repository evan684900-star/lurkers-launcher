#!/bin/bash
set -e
cd "$(dirname "$0")"
F='warning: \[options\]|^Picked up|^1 warning'
J="javac --release 8 -nowarn -encoding UTF-8"
# Dépendance de compilation : le .jar du jeu (libs/game.jar) s'il est là,
# sinon libGDX seul (libs/gdx-1.9.10.jar, téléchargé depuis Maven Central).
if [ -f libs/game.jar ]; then GDX=libs/game.jar; else
  GDX=libs/gdx-1.9.10.jar
  [ -f "$GDX" ] || { mkdir -p libs && curl -sSfL -o "$GDX" https://repo1.maven.org/maven2/com/badlogicgames/gdx/gdx/1.9.10/gdx-1.9.10.jar; }
fi
rm -rf build dist/modloader/lib dist/modloader/mods dist/launcher/*.jar dist/traducteur && mkdir -p build/{boot,core,hud,launcher,traducteur} dist/modloader/{lib,mods,config} dist/launcher/fr
# 1. agent (chargeur système) : aucune dépendance à libGDX
$J -d build/boot $(find loader/src -name '*.java') 2>&1 | grep -Ev "$F" || true
printf 'Manifest-Version: 1.0\nPremain-Class: fr.evan.lurkers.loader.Agent\nImplementation-Version: 0.1.1\n' > build/boot.mf
jar cfm dist/modloader/lurkers-modloader.jar build/boot.mf -C build/boot . 2>&1 | grep -v Picked || true
# 2. cœur + API (chargés dans le ClassLoader du jeu)
$J -cp "$GDX" -d build/core $(find core -name '*.java') 2>&1 | grep -Ev "$F" || true
jar cf dist/modloader/lib/modloader-core.jar -C build/core . 2>&1 | grep -v Picked || true
# 3. mod HUD
$J -cp "$GDX":build/core -d build/hud $(find hud/src -name '*.java') 2>&1 | grep -Ev "$F" || true
mkdir -p build/hud/META-INF/services && echo fr.evan.lurkers.hud.HudMod > build/hud/META-INF/services/fr.evan.lurkers.api.LurkersMod
jar cf dist/modloader/mods/HUD.jar -C build/hud . 2>&1 | grep -v Picked || true
cp loader/traduction.properties dist/modloader/config/
# 4. launcher
$J -d build/launcher $(find launcher/src -name '*.java') 2>&1 | grep -Ev "$F" || true
printf 'Manifest-Version: 1.0\nMain-Class: fr.evan.lurkers.launcher.Main\n' > build/launcher.mf
jar cfm dist/launcher/lurkers-launcher.jar build/launcher.mf -C build/launcher . 2>&1 | grep -v Picked || true
cp traduction/language_fr.properties dist/launcher/fr/
cp LurkersLauncher.json dist/
# 5. traducteur (Lurkers Translator) : outil indépendant, ne touche pas au jeu
mkdir -p dist/traducteur
$J -d build/traducteur $(find traducteur/src -name '*.java') 2>&1 | grep -Ev "$F" || true
printf 'Manifest-Version: 1.0\nMain-Class: fr.evan.lurkers.traducteur.Main\nImplementation-Version: 1.0.0\n' > build/traducteur.mf
jar cfm dist/traducteur/lurkers-translator.jar build/traducteur.mf -C build/traducteur . 2>&1 | grep -v Picked || true
cp traducteur/LurkersTranslator.json traducteur/LurkersTranslator.bat dist/
echo build OK
