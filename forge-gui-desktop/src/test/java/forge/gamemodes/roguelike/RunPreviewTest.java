package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.card.CardView;

public class RunPreviewTest extends AITest {

    @Test
    public void previewsShowTheRunsUpgrades() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Run run = Run.start(c, "jill_mono_blue", 1);
        CardView plain = RunPreview.cardView(run, "Man-o'-War", null);
        assertEquals(2, plain.getCurrentState().getPower());

        run.getState().upgrades.put("Man-o'-War", new ArrayList<>(List.of(new RunUpgrade(1, 1, List.of()))));
        CardView upgraded = RunPreview.cardView(run, "Man-o'-War", null);
        assertEquals(3, upgraded.getCurrentState().getPower());
        assertEquals(3, upgraded.getCurrentState().getToughness());

        CardView offered = RunPreview.cardView(run, "Man-o'-War", new RunUpgrade(0, 0, List.of("Haste")));
        assertEquals(3, offered.getCurrentState().getPower());
        assertTrue(offered.getCurrentState().hasHaste());
        // The text the card is drawn with shows the upgrade, not just the printed text
        assertTrue(offered.getText(offered.getCurrentState(), null).contains("Haste"));
        assertEquals("", offered.getCurrentState().getDisplayId()); // a preview has no game id to show

        // the shared UI copy of the card is not changed
        assertEquals(2, RunPreview.cardView(Run.start(c, "jill_mono_blue", 2), "Man-o'-War", null).getCurrentState().getPower());
    }
}
