package forge.gamemodes.roguelike.meta;

import java.util.List;

import forge.gamemodes.roguelike.RoguelikeContent;

/** Plain-text descriptions of meta-progression for screens. */
public final class ProgressText {

    private ProgressText() {
    }

    public static String levelLine(final RoguelikeContent c, final CharacterDef ch, final CharacterProgress p) {
        Progression.Level l = Progression.characterLevel(c, p);
        return ch.name + " - level " + l.level() + " (" + l.xpIntoLevel() + "/" + l.xpForNext() + " experience)";
    }

    public static String accountLine(final RoguelikeContent c, final MetaProfile profile) {
        Progression.Level l = Progression.accountLevel(c, profile);
        return "Account level " + l.level() + " (" + l.xpIntoLevel() + "/" + l.xpForNext() + " experience)";
    }

    public static String progress(final RoguelikeContent c, final MetaProfile profile, final CharacterDef ch) {
        CharacterProgress p = profile.character(ch.id);
        StringBuilder sb = new StringBuilder(levelLine(c, ch, p)).append("\n\n");
        List<Progression.Unlock> earned = Progression.characterUnlocks(c, ch, p);
        sb.append("Unlocked:\n");
        if (earned.isEmpty()) {
            sb.append("  nothing yet\n");
        }
        for (Progression.Unlock u : earned) {
            sb.append("  ").append(u.source()).append(": ").append(Progression.describe(c, u.def())).append('\n');
        }
        List<Progression.Unlock> next = Progression.upcomingUnlocks(c, ch, p);
        if (!next.isEmpty()) {
            sb.append("\nComing up:\n");
            for (Progression.Unlock u : next.subList(0, Math.min(3, next.size()))) {
                sb.append("  ").append(u.source()).append(": ").append(Progression.describe(c, u.def())).append('\n');
            }
        }
        if (!ch.milestones.isEmpty()) {
            sb.append("\nMilestones:\n");
            for (MilestoneDef m : ch.milestones) {
                boolean done = p.completedMilestones.contains(m.id);
                sb.append("  ").append(m.description).append(": ")
                  .append(done ? "done" : Math.min(m.target, p.counters.getOrDefault(m.counter, 0)) + "/" + m.target).append('\n');
            }
        }
        sb.append('\n').append(accountLine(c, profile)).append('\n');
        for (Progression.Unlock u : Progression.accountUnlocks(c, profile)) {
            sb.append("  ").append(u.source()).append(": ").append(Progression.describe(c, u.def())).append('\n');
        }
        return sb.toString().trim();
    }
}
