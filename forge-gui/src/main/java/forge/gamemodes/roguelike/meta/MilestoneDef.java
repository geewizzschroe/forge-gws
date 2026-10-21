package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/** An unusual goal: when a counter (see {@link MatchStatsTracker} and {@link Progression}) reaches the target, its unlocks open. */
public class MilestoneDef {
    public String id;
    public String description;
    public String counter;
    public int target;
    public List<UnlockDef> unlocks = new ArrayList<>();
}
