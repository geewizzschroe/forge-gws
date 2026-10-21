package forge.gamemodes.roguelike;

import java.util.LinkedHashMap;
import java.util.Map;

/** A character and starter deck, loaded from {@code starters/*.json}. Decks hold no lands. */
public class StarterDef {
    public String id;
    public String name;
    public String commander;
    public Map<String, Integer> deck = new LinkedHashMap<>();
    /** File name in {@code bases/} of the player's Base, or null. */
    public String base;
    /** Id of the card pool rewards are drawn from. */
    public String rewardPool;
    /** True for a starter that shares no cards with its character's core (see {@code CharacterDef.core}). */
    public boolean independentOfCore;
}
