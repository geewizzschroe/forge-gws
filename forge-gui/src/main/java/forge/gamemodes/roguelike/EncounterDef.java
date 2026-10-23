package forge.gamemodes.roguelike;

import java.util.LinkedHashMap;
import java.util.Map;

/** An enemy or boss, loaded from {@code encounters/*.json}. Its gimmick lives on its Base. */
public class EncounterDef {
    public String id;
    public String name;
    public boolean boss;
    /** An Elite (decided 2026-10-23): has a commander, takes the place of some fights, and gives a relic. Ignores tiers. */
    public boolean elite;
    /** Difficulty tier, 1 (early) to 3 (late), for the map's ladder (proposal 1.3). Bosses ignore it. */
    public int tier = 1;
    public String commander;
    public Map<String, Integer> deck = new LinkedHashMap<>();
    /** File name in {@code bases/} of the enemy's Base, or null. */
    public String base;
    public int life = 20;
    public int hand = 7;
}
