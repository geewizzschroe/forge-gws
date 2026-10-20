package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;

public class RunTextTest extends AITest {

    @Test
    public void upgradesReadNaturally() {
        assertEquals("+1/+1", new RunUpgrade(1, 1, List.of()).describe());
        assertEquals("+0/+2", new RunUpgrade(0, 2, List.of()).describe());
        assertEquals("Ward 1", new RunUpgrade(0, 0, List.of("Ward:1")).describe());
        assertEquals("+1/+0, Haste", new RunUpgrade(1, 0, List.of("Haste")).describe());
    }

    @Test
    public void screensGetTheRunInWords() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Run run = Run.start(c, "jill_mono_blue", 5);
        run.getState().relics.add("whetstone");
        run.getState().upgrades.put("Wind Drake", new ArrayList<>(List.of(new RunUpgrade(1, 1, List.of()))));
        String status = RunText.status(run);
        assertTrue(status, status.startsWith("Life 20/20"));
        assertTrue(status, status.contains("Relics: Whetstone"));
        assertTrue(status, status.endsWith("Choose where to go next."));
        String deck = RunText.deckList(run);
        assertTrue(deck, deck.contains("3x Wind Drake  [+1/+1]"));
        assertTrue(deck, deck.contains("Whetstone - Creatures you control get +1/+0."));
        MapNode first = run.getAvailableNodes().get(0);
        assertTrue(RunText.node(run, first), RunText.node(run, first).startsWith("Step 1 - Enemy: "));

        OfferOption o = new OfferOption();
        o.cards.put("Air Elemental", 2);
        o.upgradeCard = "Air Elemental";
        o.upgrade = new RunUpgrade(0, 0, List.of("Ward:1"));
        o.price = 30;
        assertEquals("2x Air Elemental [upgraded: Ward 1]  (30 gold)", RunText.option(run, o));
    }
}
