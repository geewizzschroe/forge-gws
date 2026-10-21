package forge.gamemodes.roguelike.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.gamemodes.roguelike.RunStatus;

/** One simulated run. */
public class RunRecord {
    public long seed;
    public String starterId;
    public RunStatus status;
    public final List<MatchRecord> matches = new ArrayList<>();
    /** Name of the enemy that ended the run, if it was lost. */
    public String endedBy;
    public int floor;
    public int finalDeckSize;
    public int finalLife;
    public int finalMaxLife;
    public int goldLeft;
    public final List<String> relics = new ArrayList<>();
    /** How many times each node type was entered. */
    public final Map<String, Integer> nodesEntered = new LinkedHashMap<>();
    /** Every choice taken, in the run log's wording. */
    public final List<String> choices = new ArrayList<>();
}
