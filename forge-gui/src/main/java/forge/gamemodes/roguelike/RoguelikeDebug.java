package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.gamemodes.roguelike.meta.CharacterDef;
import forge.gamemodes.roguelike.meta.CharacterProgress;
import forge.gamemodes.roguelike.meta.LevelDef;
import forge.gamemodes.roguelike.meta.MetaProfile;
import forge.gamemodes.roguelike.meta.MilestoneDef;
import forge.gamemodes.roguelike.meta.Progression;
import forge.gamemodes.roguelike.meta.RunProgress;

/** Testing shortcuts for the prototype's debug panel. Each returns a message saying what it did. */
public final class RoguelikeDebug {

    private RoguelikeDebug() {
    }

    /** Give a character experience as if earned (account too); returns the level-up messages. */
    public static List<String> grantXp(final RoguelikeContent c, final MetaProfile profile, final String characterId, final long xp) {
        List<String> out = new ArrayList<>();
        out.add("+" + xp + " experience (debug).");
        out.addAll(Progression.record(c, profile, characterId, xp, java.util.Map.of()));
        return out;
    }

    /** Raise a character to the given level (never lowers it). */
    public static List<String> raiseToLevel(final RoguelikeContent c, final MetaProfile profile, final String characterId, final int level) {
        long needed = 0;
        long step = c.getMeta().levelXpBase;
        for (int l = 1; l < level; l++) {
            needed += step;
            step += c.getMeta().levelXpGrowth;
        }
        long missing = needed - profile.character(characterId).xp;
        return missing > 0 ? grantXp(c, profile, characterId, missing) : List.of("Already level " + level + " or higher.");
    }

    /** The highest level that unlocks anything for the character. */
    public static int maxLevel(final CharacterDef ch) {
        int max = 1;
        for (LevelDef l : ch.levels) {
            max = Math.max(max, l.level);
        }
        return max;
    }

    /** Complete every milestone of the character. */
    public static List<String> completeMilestones(final RoguelikeContent c, final MetaProfile profile, final CharacterDef ch) {
        CharacterProgress p = profile.character(ch.id);
        java.util.Map<String, Integer> counters = new java.util.LinkedHashMap<>();
        for (MilestoneDef m : ch.milestones) {
            int have = p.counters.getOrDefault(m.counter, 0);
            if (have < m.target) {
                counters.merge(m.counter, m.target - have, Integer::sum);
            }
        }
        List<String> out = Progression.record(c, profile, ch.id, 0, counters);
        return out.isEmpty() ? List.of("No milestones left to complete.") : out;
    }

    /** Raise the account to its next level. */
    public static List<String> nextAccountLevel(final RoguelikeContent c, final MetaProfile profile) {
        Progression.Level l = Progression.accountLevel(c, profile);
        long missing = l.xpForNext() - l.xpIntoLevel();
        profile.accountXp += missing;
        List<String> out = new ArrayList<>();
        out.add("Account level " + Progression.accountLevel(c, profile).level() + " (debug).");
        return out;
    }

    public static String addGold(final Run run, final int gold) {
        run.getState().gold += gold;
        return "+" + gold + " gold (debug).";
    }

    public static String heal(final Run run) {
        run.getState().life = run.getState().maxLife;
        return "Healed to " + run.getState().life + " (debug).";
    }

    /** Add one relic the run doesn't have yet (locked ones included). */
    public static String addRelic(final Run run) {
        for (RelicDef r : run.getContent().getRelics().values()) {
            if (!run.getState().relics.contains(r.id)) {
                run.getState().relics.add(r.id);
                if (r.maxLifeBonus != 0) {
                    run.getState().maxLife += r.maxLifeBonus;
                    run.getState().life += Math.max(0, r.maxLifeBonus);
                }
                return "Relic added: " + r.name + " (debug).";
            }
        }
        return "You already have every relic.";
    }

    /** Win the current fight without playing it, keeping current life; returns progress messages. */
    public static List<String> winFight(final Run run, final MetaProfile profile) {
        EncounterDef enc = run.getActiveEncounter();
        if (enc == null) {
            return List.of("No fight to win right now.");
        }
        long xpBefore = run.getState().xpEarned;
        run.applyMatchResult(enc, true, run.getState().life);
        List<String> out = new ArrayList<>();
        out.add("Won against " + enc.name + " (debug).");
        out.addAll(RunProgress.afterMatch(run.getContent(), profile, run, enc, true, xpBefore, null));
        return out;
    }
}
