package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import forge.StaticData;
import forge.ai.AITest;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Golbez's Elite (2026-10-23): the Tower of Zot, Moment of Evil Triumph and the AI's cycling plan. */
public class GolbezTest extends AITest {

    private Game game;
    private Player ai;

    private void setUp(final boolean withBase) {
        game = initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        ai = game.getPlayers().get(1);
        fillLibrary(ai, 12);
        fillLibrary(game.getPlayers().get(0), 12);
        if (withBase) {
            ai.getZone(ZoneType.Command).add(Card.fromPaperCard(BaseCard.fromScript(c.getBase("tower_of_zot.txt")), ai));
        }
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, ai, 3);
        game.getAction().checkStateEffects(true);
    }

    private int clues() {
        int n = 0;
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            n += c.isToken() && c.getType().hasSubtype("Clue") ? 1 : 0;
        }
        return n;
    }

    @Test
    public void theModesOwnCardIsInTheCardDatabase() {
        initAndCreateGame();
        RoguelikeContent.load();
        assertNotNull(StaticData.instance().getCommonCards().getCard("Moment of Evil Triumph"));
    }

    @Test
    public void towerOfZotMakesCyclingCheapAndLeavesClues() {
        setUp(true);
        addCardToZone("Moment of Evil Triumph", ai, ZoneType.Library); // the plan: cycle, then reanimate
        addCardToZone("Ice Flan", ai, ZoneType.Hand); // cycling {2}, {1} with the Base
        addCard("Island", ai);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        assertTrue("the AI cycled Ice Flan for one mana", ai.getCardsIn(ZoneType.Graveyard).anyMatch(c -> c.getName().equals("Ice Flan")));
        assertEquals("and got a Clue", 1, clues());
    }

    @Test
    public void momentOfEvilTriumphRaisesTheWholeGraveyard() {
        setUp(false);
        addCardToZone("Ice Flan", ai, ZoneType.Graveyard);
        addCardToZone("Malboro", ai, ZoneType.Graveyard);
        addCardToZone("Hill Gigas", ai, ZoneType.Graveyard);
        addCardToZone("Moment of Evil Triumph", ai, ZoneType.Hand);
        addCards("Island", 5, ai);
        addCards("Swamp", 5, ai);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        for (String name : new String[] {"Ice Flan", "Malboro", "Hill Gigas"}) {
            assertTrue("the AI cast it and " + name + " came back",
                    ai.getCardsIn(ZoneType.Battlefield).anyMatch(c -> c.getName().equals(name)));
        }
    }
}
