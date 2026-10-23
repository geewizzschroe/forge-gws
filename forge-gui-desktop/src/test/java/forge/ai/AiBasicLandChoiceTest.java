package forge.ai;

import static org.testng.AssertJUnit.assertEquals;

import java.util.List;

import org.testng.annotations.Test;

import forge.game.Game;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** The landless land picker gets a commander still to be cast its colours early (2026-10-23). */
public class AiBasicLandChoiceTest extends AITest {

    @Test
    public void aCommandersMissingColourComesSecond() {
        Game game = initAndCreateGame();
        Player ai = game.getPlayers().get(1);
        for (int i = 0; i < 20; i++) {
            addCardToZone("Grizzly Bears", ai, ZoneType.Library); // a green library pulls toward Forests
        }
        Card halana = addCardToZone("Halana and Alena, Partners", ai, ZoneType.Command); // {2}{R}{G}
        ai.addCommander(halana);
        List<String> options = List.of("Mountain", "Forest");
        assertEquals("the first land follows the deck", "Forest", AiBasicLandChoice.choose(ai, options));
        addCard("Forest", ai);
        assertEquals("then the commander's missing colour", "Mountain", AiBasicLandChoice.choose(ai, options));
        addCard("Mountain", ai);
        assertEquals("then back to the deck", "Forest", AiBasicLandChoice.choose(ai, options));
    }
}
