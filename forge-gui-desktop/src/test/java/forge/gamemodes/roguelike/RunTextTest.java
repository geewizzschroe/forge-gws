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
        run.getState().upgrades.put("Man-o'-War", new ArrayList<>(List.of(new RunUpgrade(1, 1, List.of()))));
        String status = RunText.status(run);
        assertTrue(status, status.startsWith("Life 20/20"));
        assertTrue(status, status.contains("Relics: Whetstone"));
        assertTrue(status, status.endsWith("Choose where to go next."));
        String deck = RunText.deckList(run);
        assertTrue(deck, deck.contains("2x Man-o'-War  [+1/+1]"));
        assertTrue(deck, deck.contains("Whetstone - Creatures you control get +1/+0."));
        MapNode first = run.getAvailableNodes().get(0);
        assertTrue(RunText.node(run, first), RunText.node(run, first).startsWith("Step 1 - Enemy: "));

        assertTrue(RunText.nodeDetail(run, first), RunText.nodeDetail(run, first).startsWith("Enemy: "));
        MapNode boss = run.getState().map.get(run.getState().map.bossId);
        String bossText = RunText.nodeDetail(run, boss);
        assertTrue(bossText, bossText.contains("Boss: Krenko's Warband"));
        assertTrue(bossText, bossText.contains("commander Krenko, Mob Boss"));
        assertTrue(bossText, bossText.contains("Base: Goblin Warren - Goblins you control get +1/+0. At the beginning"));

        run.getState().status = RunStatus.LOST;
        run.getState().matchesWon = 2;
        run.getState().matchesLost = 2;
        run.getState().beaten.add("Goblin Raiders");
        String summary = RunText.summary(run);
        assertTrue(summary, summary.startsWith("Run over."));
        assertTrue(summary, summary.contains("Matches won: 2   lost: 2"));
        assertTrue(summary, summary.contains("Beaten: Goblin Raiders"));
        assertTrue(summary, summary.contains("Relics: Whetstone"));

        OfferOption o = new OfferOption();
        o.cards.put("Air Elemental", 2);
        o.upgradeCard = "Air Elemental";
        o.upgrade = new RunUpgrade(0, 0, List.of("Ward:1"));
        o.price = 30;
        assertEquals("2x Air Elemental [upgraded: Ward 1]  (30 gold)", RunText.option(run, o));
    }
}
