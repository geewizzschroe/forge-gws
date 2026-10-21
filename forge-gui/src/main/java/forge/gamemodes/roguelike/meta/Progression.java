package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.card.ColorSet;
import forge.gamemodes.roguelike.BaseAbility;
import forge.gamemodes.roguelike.CardPoolDef;
import forge.gamemodes.roguelike.RelicDef;
import forge.gamemodes.roguelike.RoguelikeContent;
import forge.gamemodes.roguelike.RunSetup;
import forge.gamemodes.roguelike.StarterDef;
import forge.item.PaperCard;

/**
 * The rules of meta-progression (DESIGN.md 2.6): levels from experience, unlocks at fixed levels and from
 * milestones, a loadout turned into a run's starting conditions, and recording what a match earned.
 */
public final class Progression {

    private Progression() {
    }

    /** A level, how far into it, and how much the next one needs. */
    public record Level(int level, long xpIntoLevel, long xpForNext) {
    }

    /** An earned or upcoming unlock, with where it comes from ("Level 3", "Milestone: ..."). */
    public record Unlock(String key, String source, UnlockDef def) {
    }

    public static Level level(final long xp, final int base, final int growth) {
        return level(xp, List.of(), base, growth);
    }

    /**
     * The level for an amount of experience. The listed steps are what levels 2, 3, ... each need in turn;
     * after them each level needs {@code growth} more than the one before. With no steps, level 2 needs
     * {@code base}.
     */
    public static Level level(final long xp, final List<Integer> steps, final int base, final int growth) {
        int level = 1;
        long left = xp;
        long need = steps.isEmpty() ? base : steps.get(0);
        while (need > 0 && left >= need) {
            left -= need;
            level++;
            need = level - 1 < steps.size() ? steps.get(level - 1) : need + growth;
        }
        return new Level(level, left, need);
    }

    /** Total experience a character needs to reach a level. */
    public static long characterXpForLevel(final RoguelikeContent c, final int level) {
        MetaConfig m = c.getMeta();
        long total = 0;
        long need = m.levelXpSteps.isEmpty() ? m.levelXpBase : m.levelXpSteps.get(0);
        for (int l = 2; l <= level; l++) {
            total += need;
            need = l - 1 < m.levelXpSteps.size() ? m.levelXpSteps.get(l - 1) : need + m.levelXpGrowth;
        }
        return total;
    }

    public static Level characterLevel(final RoguelikeContent c, final CharacterProgress p) {
        return level(p.xp, c.getMeta().levelXpSteps, c.getMeta().levelXpBase, c.getMeta().levelXpGrowth);
    }

    public static Level accountLevel(final RoguelikeContent c, final MetaProfile profile) {
        return level(profile.accountXp, c.getMeta().accountLevelXpBase, c.getMeta().accountLevelXpGrowth);
    }

    static String key(final String source, final int index) {
        return source + "#" + index;
    }

    /** Unlocks the character has earned, in level order, then milestones. */
    public static List<Unlock> characterUnlocks(final RoguelikeContent c, final CharacterDef ch, final CharacterProgress p) {
        int level = characterLevel(c, p).level();
        List<Unlock> out = new ArrayList<>();
        for (LevelDef l : ch.levels) {
            if (l.level <= level) {
                for (int i = 0; i < l.unlocks.size(); i++) {
                    out.add(new Unlock(key("L" + l.level, i), "Level " + l.level, l.unlocks.get(i)));
                }
            }
        }
        for (MilestoneDef m : ch.milestones) {
            if (p.completedMilestones.contains(m.id)) {
                for (int i = 0; i < m.unlocks.size(); i++) {
                    out.add(new Unlock(key("M" + m.id, i), "Milestone: " + m.description, m.unlocks.get(i)));
                }
            }
        }
        return out;
    }

    /** Level unlocks the character hasn't reached yet. */
    public static List<Unlock> upcomingUnlocks(final RoguelikeContent c, final CharacterDef ch, final CharacterProgress p) {
        int level = characterLevel(c, p).level();
        List<Unlock> out = new ArrayList<>();
        for (LevelDef l : ch.levels) {
            if (l.level > level) {
                for (int i = 0; i < l.unlocks.size(); i++) {
                    out.add(new Unlock(key("L" + l.level, i), "Level " + l.level, l.unlocks.get(i)));
                }
            }
        }
        return out;
    }

    public static List<Unlock> accountUnlocks(final RoguelikeContent c, final MetaProfile profile) {
        int level = accountLevel(c, profile).level();
        List<Unlock> out = new ArrayList<>();
        for (LevelDef l : c.getMeta().accountLevels) {
            if (l.level <= level) {
                for (int i = 0; i < l.unlocks.size(); i++) {
                    out.add(new Unlock(key("A" + l.level, i), "Account level " + l.level, l.unlocks.get(i)));
                }
            }
        }
        return out;
    }

    public static List<String> availableStarters(final RoguelikeContent c, final CharacterDef ch, final CharacterProgress p) {
        Set<String> out = new LinkedHashSet<>(ch.starters);
        for (Unlock u : characterUnlocks(c, ch, p)) {
            if ("starter".equals(u.def().kind)) {
                out.add(u.def().id);
            }
        }
        return new ArrayList<>(out);
    }

    /** The character that a starter deck belongs to (by commander), or null. */
    public static CharacterDef characterForStarter(final RoguelikeContent c, final String starterId) {
        String commander = c.getStarter(starterId).commander;
        for (CharacterDef ch : c.getCharacters().values()) {
            if (ch.commander.equals(commander)) {
                return ch;
            }
        }
        return null;
    }

    /** The run's starting conditions from a character's earned unlocks, minus those switched off, plus account unlocks. */
    public static RunSetup setup(final RoguelikeContent c, final MetaProfile profile, final CharacterDef ch, final Loadout loadout) {
        CharacterProgress p = profile.character(ch.id);
        RunSetup s = new RunSetup();
        s.characterId = ch.id;
        for (Unlock u : characterUnlocks(c, ch, p)) {
            if (loadout != null && loadout.disabled.contains(u.key())) {
                continue;
            }
            UnlockDef d = u.def();
            switch (d.kind) {
                case "maxLife" -> s.maxLifeBonus += d.amount;
                case "gold" -> s.goldBonus += d.amount;
                case "retry" -> s.retryBonus += d.amount;
                case "upgrade" -> s.upgrades.computeIfAbsent(d.card, k -> new ArrayList<>()).add(d.upgrade);
                case "baseAbility" -> s.baseAbilities.add(new BaseAbility(d.name, d.description, d.script));
                default -> { } // starters and Bases are chosen in the loadout
            }
        }
        if (loadout != null && loadout.base != null) {
            s.base = loadout.base;
        }

        List<String> relics = new ArrayList<>();
        for (RelicDef r : c.getRelics().values()) {
            if (!r.locked) {
                relics.add(r.id);
            }
        }
        StarterDef starter = loadout != null && loadout.starterId != null ? c.getStarter(loadout.starterId) : null;
        for (Unlock u : accountUnlocks(c, profile)) {
            UnlockDef d = u.def();
            switch (d.kind) {
                case "relic" -> relics.add(d.id);
                case "pool" -> {
                    CardPoolDef pool = c.getPool(d.id);
                    s.unlockedPools.add(pool.id);
                    if (!pool.bucket) {
                        s.extraRewardCards.addAll(pool.cards);
                    }
                }
                case "set" -> s.extraRewardCards.addAll(setCards(d, starter));
                default -> { }
            }
        }
        s.relicPool = relics;
        return s;
    }

    /**
     * The cards of an unlocked set that join the reward pool: nonland cards not excluded, which share a colour
     * with the starter's commander or are colourless. PLACEHOLDER: the colour filter is not decided.
     */
    static List<String> setCards(final UnlockDef d, final StarterDef starter) {
        CardEdition edition = StaticData.instance().getEditions().get(d.set);
        if (edition == null) {
            throw new IllegalArgumentException("No set with code " + d.set);
        }
        ColorSet identity = null;
        if (starter != null) {
            PaperCard cmd = StaticData.instance().getCommonCards().getCard(starter.commander);
            identity = cmd == null ? null : cmd.getRules().getColorIdentity();
        }
        Set<String> out = new LinkedHashSet<>();
        for (CardEdition.EditionEntry e : edition.getAllCardsInSet()) {
            if (d.exclude.contains(e.name())) {
                continue;
            }
            PaperCard pc = StaticData.instance().getCommonCards().getCard(e.name());
            if (pc == null) {
                continue;
            }
            CardRules rules = pc.getRules();
            if (rules.getMainPart().getType().isLand()) {
                continue;
            }
            ColorSet colors = rules.getColorIdentity();
            if (identity == null || colors.isColorless() || colors.sharesColorWith(identity)) {
                out.add(e.name());
            }
        }
        return new ArrayList<>(out);
    }

    public static String describe(final RoguelikeContent c, final UnlockDef d) {
        if (d.description != null && !"baseAbility".equals(d.kind)) {
            return d.description;
        }
        return switch (d.kind) {
            case "starter" -> "Starter deck: " + c.getStarter(d.id).name;
            case "maxLife" -> "+" + d.amount + " run max life";
            case "gold" -> "+" + d.amount + " starting gold";
            case "retry" -> "+" + d.amount + (d.amount == 1 ? " retry" : " retries");
            case "upgrade" -> "Starting upgrade: " + d.card + " " + d.upgrade.describe();
            case "base" -> "Base: " + baseName(c, d.base);
            case "baseAbility" -> d.name + ": " + d.description;
            case "relic" -> "Relic in the pool: " + c.getRelic(d.id).name;
            case "pool" -> "Cards in the pool: " + c.getPool(d.id).name;
            case "set" -> "Set in the reward pool: " + d.set;
            default -> d.kind;
        };
    }

    static String baseName(final RoguelikeContent c, final String file) {
        for (String line : c.getBase(file)) {
            if (line.startsWith("Name:")) {
                return line.substring(5);
            }
        }
        return file;
    }

    /**
     * Add a match's (or run's) experience and counters to the profile. Returns messages about levels reached,
     * milestones completed and what they unlocked, for the player to see.
     */
    public static List<String> record(final RoguelikeContent c, final MetaProfile profile, final String characterId,
                                      final long xp, final Map<String, Integer> counters) {
        List<String> messages = new ArrayList<>();
        CharacterDef ch = c.getCharacters().get(characterId);
        if (ch == null) {
            return messages;
        }
        CharacterProgress p = profile.character(ch.id);
        int before = characterLevel(c, p).level();
        int accountBefore = accountLevel(c, profile).level();
        p.xp += xp;
        profile.accountXp += xp;
        int after = characterLevel(c, p).level();
        for (LevelDef l : ch.levels) {
            if (l.level > before && l.level <= after) {
                messages.add(ch.name + " reached level " + l.level + "! " + unlockList(c, l.unlocks));
            }
        }
        if (after > before && ch.levels.stream().noneMatch(l -> l.level == after)) {
            messages.add(ch.name + " reached level " + after + ".");
        }
        int accountAfter = accountLevel(c, profile).level();
        for (LevelDef l : c.getMeta().accountLevels) {
            if (l.level > accountBefore && l.level <= accountAfter) {
                messages.add("Account level " + l.level + "! " + unlockList(c, l.unlocks));
            }
        }
        counters.forEach((k, v) -> p.counters.merge(k, v, Integer::sum));
        for (MilestoneDef m : ch.milestones) {
            if (!p.completedMilestones.contains(m.id) && p.counters.getOrDefault(m.counter, 0) >= m.target) {
                p.completedMilestones.add(m.id);
                messages.add("Milestone complete: " + m.description + "! " + unlockList(c, m.unlocks));
            }
        }
        return messages;
    }

    private static String unlockList(final RoguelikeContent c, final List<UnlockDef> unlocks) {
        List<String> parts = new ArrayList<>();
        for (UnlockDef u : unlocks) {
            parts.add(describe(c, u));
        }
        return parts.isEmpty() ? "" : "Unlocked: " + String.join("; ", parts) + ".";
    }
}
