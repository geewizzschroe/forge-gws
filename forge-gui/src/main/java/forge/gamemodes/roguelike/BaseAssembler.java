package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the script of a player's Base for one match: the Base's own lines plus the script lines of
 * every relic the player holds and every unlocked Base ability, with rules text listing the relics.
 */
public final class BaseAssembler {

    /** Used when the player holds relics but their starter has no Base of its own. */
    static final List<String> DEFAULT_BASE = List.of(
            "Name:Camp",
            "ManaCost:no cost",
            "Types:Base",
            "Oracle:");

    private BaseAssembler() {
    }

    /** The assembled script, or null when there is neither a Base nor any relic. */
    public static List<String> assemble(final List<String> base, final List<RelicDef> relics) {
        return assemble(base, relics, List.of());
    }

    /** The assembled script with unlocked Base abilities too, or null when there is nothing to put on a Base. */
    public static List<String> assemble(final List<String> base, final List<RelicDef> relics, final List<BaseAbility> abilities) {
        if (base == null && relics.isEmpty() && abilities.isEmpty()) {
            return null;
        }
        List<String> out = new ArrayList<>();
        String oracle = "";
        for (String line : base == null ? DEFAULT_BASE : base) {
            if (line.startsWith("Oracle:")) {
                oracle = line.substring("Oracle:".length());
            } else if (!line.startsWith("Text:")) {
                out.add(line);
            }
        }

        Set<String> svars = new HashSet<>();
        for (String line : out) {
            addSVarName(line, svars, "the Base");
        }
        List<String> relicText = new ArrayList<>();
        for (RelicDef r : relics) {
            for (String line : r.script) {
                addSVarName(line, svars, "relic " + r.id);
                out.add(line);
            }
            relicText.add(r.name + ": " + r.description);
        }

        // An unlocked ability shows only as its own line of rules text (its script's description); only relics are listed
        for (BaseAbility a : abilities) {
            for (String line : a.script) {
                addSVarName(line, svars, "Base ability " + a.name);
                out.add(line);
            }
        }

        if (!relicText.isEmpty()) {
            out.add("Text:Relics:\\r\\n" + String.join("\\r\\n", relicText));
            String list = String.join("\\n", relicText);
            oracle = oracle.isEmpty() ? list : oracle + "\\n" + list;
        }
        out.add("Oracle:" + oracle);
        return out;
    }

    private static void addSVarName(final String line, final Set<String> seen, final String owner) {
        if (!line.startsWith("SVar:")) {
            return;
        }
        int end = line.indexOf(':', 5);
        String name = end < 0 ? line.substring(5) : line.substring(5, end);
        if (!seen.add(name)) {
            throw new IllegalStateException("SVar " + name + " in " + owner
                    + " is already used on this Base; prefix relic SVars with the relic id");
        }
    }
}
