package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** The Restless Dead's Base makes a Zombie when a nontoken creature dies, and not when a token does. */
public class OpenGravesTest extends AITest {

    private static int zombies(final Player p) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getType().hasSubtype("Zombie")) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void onlyNontokenCreaturesMakeZombies() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        RoguelikeContent c = RoguelikeContent.load();
        Card base = Card.fromPaperCard(BaseCard.fromScript(c.getBase("open_graves.txt")), p);
        p.getZone(ZoneType.Command).add(base);
        game.getAction().checkStateEffects(true);

        Card lions = addCard("Savannah Lions", p);
        game.getAction().destroy(lions, null, true, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertEquals("a nontoken creature died", 1, zombies(p));

        Card zombie = null;
        for (Card t : p.getCardsIn(ZoneType.Battlefield)) {
            if (t.isToken()) {
                zombie = t;
            }
        }
        game.getAction().destroy(zombie, null, true, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertEquals("a token died: no new Zombie", 0, zombies(p));
    }
}
