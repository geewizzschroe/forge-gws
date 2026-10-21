package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.testng.annotations.Test;

import forge.StaticData;
import forge.ai.AITest;
import forge.item.PaperCard;

/**
 * The card scope check (DESIGN.md 2.3): which content cards have no printing in the scope sets.
 *
 * This does not fail on out-of-scope cards; it lists them in target/card-scope.md so the content
 * can be reviewed, and each card either swapped or added to the exceptions.
 */
public class CardScopeTest extends AITest {

    @Test
    public void scopeCheckFindsCardsOutsideTheScopeSets() throws Exception {
        initAndCreateGame(); // loads the card database
        RoguelikeContent c = RoguelikeContent.load();

        assertTrue(c.isInScope("Jill, Shiva's Dominant")); // Final Fantasy
        assertTrue(c.isInScope("Lightning, Army of One")); // Final Fantasy
        assertFalse(c.isInScope("Vendilion Clique"));
        c.getConfig().cardScopeExceptions = List.of("Vendilion Clique");
        assertTrue(c.isInScope("Vendilion Clique"));
        c.getConfig().cardScopeExceptions = List.of();

        writeReport(c, new File("target/card-scope.md"));
    }

    private static void writeReport(final RoguelikeContent c, final File file) throws Exception {
        Map<String, List<String>> all = c.cardNamesBySource();
        Map<String, List<String>> out = c.findOutOfScopeCards();
        List<String> lines = new ArrayList<>();
        lines.add("# Card scope check");
        lines.add("");
        lines.add("Scope sets: " + c.getConfig().cardScopeSets + ". Exceptions: " + c.getConfig().cardScopeExceptions + ".");
        lines.add("");
        int total = 0;
        int outside = 0;
        for (Map.Entry<String, List<String>> e : all.entrySet()) {
            List<String> names = e.getValue();
            List<String> outNames = out.getOrDefault(e.getKey(), List.of());
            total += names.size();
            outside += outNames.size();
            lines.add("## " + e.getKey() + " (" + outNames.size() + " of " + names.size() + " outside)");
            lines.add("");
            for (String n : names) {
                lines.add("- " + (outNames.contains(n) ? "OUT " : "in  ") + n + " (" + printings(n) + ")");
            }
            lines.add("");
        }
        lines.add(2, "Content cards outside the scope: " + outside + " of " + total + " (counted once per file).");
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    private static String printings(final String name) {
        List<String> sets = new ArrayList<>();
        for (PaperCard pc : StaticData.instance().getCommonCards().getAllCards(name)) {
            if (!sets.contains(pc.getEdition())) {
                sets.add(pc.getEdition());
            }
        }
        return sets.size() <= 6 ? String.join(", ", sets) : String.join(", ", sets.subList(0, 6)) + ", ... " + sets.size() + " sets";
    }
}
