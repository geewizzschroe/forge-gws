# Roguelike run mode content

Read by `forge.gamemodes.roguelike.RoguelikeContent`. See `design/roguelike-run-mode/DESIGN.md`.

This folder ships inside the app (it is a classpath resource of `forge-gui`), so it reaches Android
even though the app's `res` folder is replaced by downloaded assets. **`index.txt` must list every
file** (bundled files can't be listed at runtime); a test checks it. To edit content on a device,
copy this folder to `<Forge user data>/roguelike/content/`; when that folder exists it is used instead.

Everything here except `overrides/` is **sample content**, there to make the run playable and
testable. Replace it freely.

- `config.json`: run numbers (`RunConfig`). Fields marked PLACEHOLDER in `RunConfig.java` stand in for open design questions.
- `upgrades.json`: the pool card upgrades are drawn from (power/toughness and keywords).
- `starters/`: characters (commander, landless starter deck, Base, reward pool).
- `encounters/`: enemies and bosses (deck, optional commander, Base, life, hand size, earliest map row).
- `relics/`: relics. `script` lines are added to the player's Base each match; prefix SVar names with the relic id.
- `pools/`: card lists. `"bucket": true` makes a themed bucket that can be offered as a reward.
- `bases/`: Base card scripts (type line `Types:Base`). Abilities used from the command zone need
  `ActivationZone$ Command`, triggers `TriggerZones$ Command`, statics `EffectZone$ Command`.
- `overrides/`: landless versions of ramp cards, used instead of the printed card in every match of the mode.
