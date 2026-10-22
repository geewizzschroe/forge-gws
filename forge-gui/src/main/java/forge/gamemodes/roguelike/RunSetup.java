package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Starting conditions brought into a run from meta-progression (character unlocks and account unlocks). */
public class RunSetup {
    /** Character the run's experience goes to; null for none. */
    public String characterId;
    public int maxLifeBonus;
    public int goldBonus;
    public int retryBonus;
    public Map<String, List<RunUpgrade>> upgrades = new LinkedHashMap<>();
    /** Base file to use instead of the starter's, or null. */
    public String base;
    /** Relic ids that can be offered; null means every relic. */
    public List<String> relicPool;
    /** Cards added to the reward pool on top of the starter's own. */
    public List<String> extraRewardCards = new ArrayList<>();
    /** Abilities added to the player's Base by unlocks. */
    public List<BaseAbility> baseAbilities = new ArrayList<>();
    /** Locked card pools that have been unlocked (buckets among them can be offered). */
    public List<String> unlockedPools = new ArrayList<>();
    /** Sets added to the Full Pool by unlocks. */
    public List<String> fullPoolSets = new ArrayList<>();
    /** Full Pool sets switched off for this run. */
    public List<String> disabledSets = new ArrayList<>();
}
