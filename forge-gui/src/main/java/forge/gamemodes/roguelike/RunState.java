package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Everything a run needs to be saved and resumed. Plain fields so it serialises to JSON as is. */
public class RunState {
    public int version = 1;
    public long seed;
    /** Count of random sequences drawn so far; each draw uses a fresh generator from (seed, step). */
    public int rngStep;
    public RunStatus status = RunStatus.IN_PROGRESS;

    public String starterId;
    public String commander;
    public Map<String, Integer> deck = new LinkedHashMap<>();
    public Map<String, List<RunUpgrade>> upgrades = new LinkedHashMap<>();
    public List<String> relics = new ArrayList<>();

    public int life;
    public int maxLife;
    public int retriesLeft;
    public int gold;
    public int floor = 1;

    public RunMap map;
    /** Last node finished; null before the first. */
    public Integer currentNodeId;
    /** Node entered but not finished yet (a match to play, or an open choice); null otherwise. */
    public Integer activeNodeId;
    /** Life when the active match was entered, restored by a retry. */
    public int lifeBeforeMatch;
    public Offer offer;

    public List<String> log = new ArrayList<>();

    /** A new random generator for the next step of the run. Deterministic for a given seed and history. */
    public Random nextRandom() {
        rngStep++;
        return new Random(seed * 1_000_003L + rngStep);
    }

    public int deckSize() {
        int n = 0;
        for (int c : deck.values()) {
            n += c;
        }
        return n;
    }
}
