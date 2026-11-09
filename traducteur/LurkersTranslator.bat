@echo off
rem Lance Lurkers Translator avec le Java fourni avec le jeu.
cd /d "%~dp0"
start "" "jre\bin\javaw.exe" -Dfile.encoding=UTF-8 -jar "traducteur\lurkers-translator.jar"
