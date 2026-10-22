package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;

/** A Base's rules text says each relic once: a scripted relic as its own line, only a relic with no script text in a list. */
public class BaseAssemblerTest extends AITest {

    private static int count(final String text, final String part) {
        return text.split(java.util.regex.Pattern.quote(part), -1).length - 1;
    }

    @Test
    public void aRelicsTextAppearsOnce() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        List<String> base = c.getBase("guardian_corps_armory.txt");
        RelicDef herb = c.getRelics().get("healing_herb");
        RelicDef heart = c.getRelics().get("iron_heart");

        String withHerb = String.join("\n", BaseAssembler.assemble(base, List.of(herb)));
        assertTrue(withHerb, !withHerb.contains("Text:"));
        assertEquals(withHerb, 1, count(withHerb, "Healing Herb: At the beginning of your end step"));

        // Iron Heart's script adds no text (it raises max life), so it is the one that gets listed
        String both = String.join("\n", BaseAssembler.assemble(base, List.of(herb, heart)));
        assertTrue(both, both.contains("Text:Relics:\\r\\nIron Heart: +5 run max life."));
        assertEquals(both, 1, count(both, "Healing Herb: At the beginning of your end step"));
    }
}
