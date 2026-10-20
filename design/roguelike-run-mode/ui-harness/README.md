# UI harness (headless real-UI testing)

How the real desktop dev build was driven with no display during the design sessions.

- `Click.java`: a tiny `java.awt.Robot` helper. `click x y`, `key NAME`, `type "text"`, `drag x1 y1 x2 y2`.
- `boot.sh`: starts the dev jar under `Xvfb :99`, waits for the main menu, then clicks Adventure Mode, New Game and Start. **The paths in it are session-specific** (scratch directories). Treat it as a template.

Typical flow: start `Xvfb :99 -screen 0 1280x720x24`, run the dev jar (`forge-gui-mobile-dev/target/*-jar-with-dependencies.jar`) from a directory that contains `res/`, set `DISPLAY=:99`, run `java -cp <dir with Click.class> Click ...`, and capture screenshots with `import -window root shot.png`. In Adventure, F9 opens the console. The menu-ready check polls the average colour of the "Classic Mode" button area, because the loading screen's blue progress bar fools a single-pixel check.

Known gotchas: pass `-Duser.home=<scratch>` (Java ignores `$HOME`); do not `pkill -f` a pattern that also appears in your own command line.
