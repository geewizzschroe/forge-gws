package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/**
 * Meta-progression numbers and the account levels, loaded from {@code meta.json}. Values marked
 * PLACEHOLDER stand in for open details of D9 (DESIGN.md).
 */
public class MetaConfig {
    // Decided (DESIGN.md 2.6): a character's level 2, 3 and 4 need these amounts in turn, the experience
    // for a fight, a boss and a won run. PLACEHOLDER: after them, each level needs levelXpGrowth more.
    public List<Integer> levelXpSteps = new ArrayList<>();
    public int levelXpBase = 200; // level 2's need when there are no steps
    public int levelXpGrowth = 200;
    // PLACEHOLDER: the account level uses the same experience, on its own curve.
    public int accountLevelXpBase = 500;
    public int accountLevelXpGrowth = 500;
    public List<LevelDef> accountLevels = new ArrayList<>();
}
