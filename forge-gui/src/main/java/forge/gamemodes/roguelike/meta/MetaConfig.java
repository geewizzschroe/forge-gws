package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/**
 * Meta-progression numbers and the account levels, loaded from {@code meta.json}. Values marked
 * PLACEHOLDER stand in for open details of D9 (DESIGN.md).
 */
public class MetaConfig {
    // PLACEHOLDER: experience needed for level 2, and how much more each further level needs.
    public int levelXpBase = 200;
    public int levelXpGrowth = 200;
    // PLACEHOLDER: the account level uses the same experience, on its own curve.
    public int accountLevelXpBase = 500;
    public int accountLevelXpGrowth = 500;
    public List<LevelDef> accountLevels = new ArrayList<>();
}
