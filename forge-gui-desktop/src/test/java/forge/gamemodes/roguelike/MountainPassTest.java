package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** A Troop of Dwarves' Base makes a Treasure whenever one of its Equipment enters, and not for anything else. */
public class MountainPassTest extends AITest {

    private static int treasures(final Player p) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getType().hasSubtype("Treasure")) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void equipmentMakesTreasure() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        RoguelikeContent c = RoguelikeContent.load();
        assertTrue(c.getEncounter("troop_of_dwarves").deck.containsKey("Goldvein Pick"));
        Card base = Card.fromPaperCard(BaseCard.fromScript(c.getBase("mountain_pass.txt")), p);
        p.getZone(ZoneType.Command).add(base);
        game.getAction().checkStateEffects(true);

        game.getAction().moveToPlay(addCardToZone("Goldvein Pick", p, ZoneType.Hand), null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertEquals("an Equipment entered", 1, treasures(p));

        game.getAction().moveToPlay(addCardToZone("Dwarven Castle Guard", p, ZoneType.Hand), null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertTrue("a Dwarf is no longer enough", game.getStack().isEmpty());
    }
}
