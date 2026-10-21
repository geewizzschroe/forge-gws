package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import forge.gamemodes.roguelike.EncounterDef;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunConfig;
import forge.gamemodes.roguelike.RunStatus;

/** Feeds what a run earns into the player's progress. Used by the app and by the simulator. */
public final class RunProgress {
    /** Run-level counters milestones can refer to. */
    public static final String MATCHES_WON = "matchesWon";
    public static final String BOSSES_BEATEN = "bossesBeaten";
    public static final String RUNS_WON = "runsWon";

    private RunProgress() {
    }

    /**
     * After {@link Run#finishMatch}: add the match's experience (the change in the run's experience since
     * {@code xpBefore}) and counters to the run's character. Returns messages for the player.
     */
    public static List<String> afterMatch(final RoguelikeContent c, final MetaProfile profile, final Run run,
                                          final EncounterDef encounter, final boolean won, final long xpBefore,
                                          final Map<String, Integer> matchCounters) {
        String characterId = run.getState().characterId;
        List<String> messages = new ArrayList<>();
        if (characterId == null) {
            return messages;
        }
        Map<String, Integer> counters = new LinkedHashMap<>();
        if (matchCounters != null) {
            counters.putAll(matchCounters);
        }
        if (won) {
            counters.merge(MATCHES_WON, 1, Integer::sum);
            if (encounter.boss) {
                counters.merge(BOSSES_BEATEN, 1, Integer::sum);
            }
        }
        long xp = run.getState().xpEarned - xpBefore;
        List<String> earned = Progression.record(c, profile, characterId, xp, counters);
        if (xp > 0) {
            CharacterDef ch = c.getCharacters().get(characterId);
            Progression.Level lvl = Progression.characterLevel(c, profile.character(characterId));
            messages.add("+" + xp + " experience." + (ch == null ? "" : " " + ch.name + ": level " + lvl.level()
                    + " (" + lvl.xpIntoLevel() + "/" + lvl.xpForNext() + ")"));
        }
        messages.addAll(earned);
        return messages;
    }

    /**
     * After any run step: when the run has just been won, add the run-won experience once (it is earned when
     * the last boss's reward is chosen, after the match itself was recorded). Returns messages for the player.
     */
    public static List<String> afterStep(final RoguelikeContent c, final MetaProfile profile, final Run run) {
        List<String> messages = new ArrayList<>();
        if (run.getState().characterId == null || run.getState().status != RunStatus.WON || run.getState().xpRecordedForWin) {
            return messages;
        }
        RunConfig cfg = c.getConfig();
        long xp = Math.round(cfg.xpRunWon * cfg.xpMultiplier);
        run.getState().xpRecordedForWin = true;
        messages.add("Run won! +" + xp + " experience.");
        messages.addAll(Progression.record(c, profile, run.getState().characterId, xp, Map.of(RUNS_WON, 1)));
        return messages;
    }
}
