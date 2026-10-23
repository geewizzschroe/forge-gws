package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * What changes on one floor of a run (config.json {@code floorRules}, one entry per floor; floors past the
 * list use its last entry). Decided 2026-10-23: later floors are harder through any of these knobs.
 */
public class FloorRule {
    /** The map layout for this floor (see RunMap); null uses {@code mapLayout}. */
    public List<String> layout;
    /** Extra starting life for every enemy and boss on this floor. */
    public int enemyLifeBonus;
    /** How many of each enemy's creature names get a random upgrade from the upgrade pool. */
    public int enemyUpgrades;
    /** Card-script lines added to every enemy's Base on this floor (a floor-wide buff). */
    public List<String> enemyBaseScript = new ArrayList<>();
    /** The buff in words, for the map screen. */
    public String enemyBaseText;
}
