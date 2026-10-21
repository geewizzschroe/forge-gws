#!/bin/bash
# usage: boot.sh "<plane setting>" tag
# Starts the real desktop dev build from the OVERLAY assets dir (repo untouched) under Xvfb :99,
# selects the plane, then Adventure Mode -> New Game -> Start, and leaves the game running.
SC=${SC:-/path/to/scratch}   # scratch dir holding overlay/, testhome/ and tools/ (session-specific)
H=$SC/testhome; PLANE="$1"; TAG="$2"; export DISPLAY=:99
JAR=/home/user/forge-gws/forge-gui-mobile-dev/target/forge-gui-mobile-dev-2.0.16-SNAPSHOT-jar-with-dependencies.jar
pkill -f "[f]orge-gui-mobile-dev-2.0.16"; sleep 2
pgrep Xvfb >/dev/null || (Xvfb :99 -screen 0 1280x720x24 >$SC/xvfb.log 2>&1 &); sleep 1
rm -rf $H/.forge/adventure/user_* $H/.forge/adventure/RoL_* $H/.forge/adventure/Realm* $H/.forge/adventure/Shandalar 2>/dev/null   # old saves
printf '{"plane":"%s","width":1280,"height":720,"videomode":"720p"}' "$PLANE" > $H/.forge/adventure/settings.json
cd $SC/overlay
(nohup java -Duser.home=$H -Xmx3g -jar $JAR width=1280 height=720 landscape > $SC/game_$TAG.log 2>&1 &)
ready=0
for i in $(seq 1 90); do
  import -window root $SC/probe_$TAG.png 2>/dev/null
  rgb=$(convert $SC/probe_$TAG.png -crop 630x70+330+515 -resize 1x1! -depth 8 txt:- 2>/dev/null | tail -1 | grep -o 'srgb([0-9]*,[0-9]*,[0-9]*)' | tr -d 'srgb()')
  r=$(echo "$rgb" | cut -d, -f1); b=$(echo "$rgb" | cut -d, -f3)
  if [ -n "$b" ] && [ $((b - r)) -gt 70 ]; then ready=1; break; fi
  sleep 3
done
echo "menu ready: $ready"; [ $ready = 1 ] || exit 1
sleep 2
java -cp $SC/tools Click click 640 657 2>&1 | grep -v JAVA_TOOL
for i in $(seq 1 30); do grep -q "ui/start_menu.json" $SC/game_$TAG.log && break; sleep 2; done; sleep 6
java -cp $SC/tools Click click 533 87 2>&1 | grep -v JAVA_TOOL; sleep 8
import -window root $SC/${TAG}_create.png
java -cp $SC/tools Click click 1115 446 2>&1 | grep -v JAVA_TOOL
sleep 30; import -window root $SC/${TAG}_start.png
echo "== $TAG exceptions so far (excluding the sandbox's blocked update-check): $(grep -v JAVA_TOOL $SC/game_$TAG.log | grep -v 'releases.atom' | grep -c 'Exception')"
