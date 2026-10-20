package forge.gamemodes.roguelike;

import java.util.LinkedHashMap;
import java.util.Map;

/** An enemy or boss, loaded from {@code encounters/*.json}. Its gimmick lives on its Base. */
public class EncounterDef {
    public String id;
    public String name;
    public boolean boss;
    /** Earliest map row (0-based) this enemy can appear on. */
    public int minRow;
    public String commander;
    public Map<String, Integer> deck = new LinkedHashMap<>();
    /** File name in {@code bases/} of the enemy's Base, or null. */
    public String base;
    public int life = 20;
    public int hand = 7;
}
