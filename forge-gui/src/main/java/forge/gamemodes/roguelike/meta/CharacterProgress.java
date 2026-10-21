package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One character's saved progress. */
public class CharacterProgress {
    public long xp;
    public Map<String, Integer> counters = new LinkedHashMap<>();
    public List<String> completedMilestones = new ArrayList<>();
    /** The loadout used last time, offered again by default. */
    public Loadout loadout;
}
