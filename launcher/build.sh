#!/bin/sh
set -e
cd "$(dirname "$0")"
rm -rf build && mkdir -p build/classes
javac --release 8 -Xlint:-options -encoding UTF-8 -d build/classes src/launcher/*.java
cp -r web build/classes/web
cp -r templates build/classes/templates
printf 'Main-Class: launcher.LauncherServer\n' > build/manifest.txt
jar cfm snets-launcher.jar build/manifest.txt -C build/classes .
echo "OK: snets-launcher.jar"
