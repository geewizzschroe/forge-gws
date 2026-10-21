package forge.gamemodes.roguelike.sim;

/** One simulated match. */
public class MatchRecord {
    public long runSeed;
    public String encounterId;
    public String encounterName;
    public boolean boss;
    public int floor;
    /** Map row, 0-based; -1 for a single-encounter test. */
    public int row;
    public int heroLifeBefore;
    public int heroLifeAfter;
    public boolean won;
    /** The match hit the time limit and was ended as a draw (counted as a loss by the run). */
    public boolean timedOut;
    public int turns;
    public long millis;

    static String csvHeader() {
        return "runSeed,encounterId,boss,floor,row,heroLifeBefore,heroLifeAfter,won,timedOut,turns,millis";
    }

    String toCsv() {
        return runSeed + "," + encounterId + "," + boss + "," + floor + "," + row + "," + heroLifeBefore + ","
                + heroLifeAfter + "," + won + "," + timedOut + "," + turns + "," + millis;
    }
}
