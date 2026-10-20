package forge.game;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.model.FModel;

/** A per-player life cap: life gain stops at it, everything else about life is unchanged. */
public class MaxLifeTest extends AITest {

    @Test
    public void gainIsTrimmedToTheCap() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        p.setMaxLife(40);
        p.setLife(38, null);
        int gainedBefore = p.getLifeGainedThisTurn();
        assertTrue(p.gainLife(5, null, null));
        assertEquals(40, p.getLife());
        assertEquals("only life actually gained counts", 2, p.getLifeGainedThisTurn() - gainedBefore);
    }

    @Test
    public void nothingIsGainedAtTheCap() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        p.setMaxLife(40);
        p.setLife(40, null);
        int gainedBefore = p.getLifeGainedThisTurn();
        assertFalse(p.gainLife(3, null, null));
        assertEquals(40, p.getLife());
        assertEquals(gainedBefore, p.getLifeGainedThisTurn());
    }

    @Test
    public void noCapByDefault() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        assertEquals(0, p.getMaxLife());
        assertTrue(p.gainLife(100, null, null));
        assertEquals(120, p.getLife());
    }

    @Test
    public void lifeLossIsUnaffected() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        p.setMaxLife(40);
        p.loseLife(15, false, false, null);
        assertEquals(5, p.getLife());
        assertTrue(p.gainLife(50, null, null));
        assertEquals(40, p.getLife());
    }

    @Test
    public void doublersApplyBeforeTheCap() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCard("Boon Reflection", p);
        p.setMaxLife(40);
        p.loseLife(7, false, false, null);
        assertEquals(13, p.getLife());
        p.gainLife(3, null, null); // doubled to 6
        assertEquals(19, p.getLife());
        p.gainLife(10, null, null); // doubled to 20
        assertEquals(39, p.getLife());
        p.gainLife(3, null, null); // doubled to 6, then trimmed to 1
        assertEquals(40, p.getLife());
    }

    @Test
    public void registeredPlayerCapReachesTheGame() {
        initAndCreateGame(); // loads the card database
        Deck deck = new Deck("d");
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Island"), 40);
        List<RegisteredPlayer> players = new ArrayList<>();
        RegisteredPlayer hero = new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("Hero", null));
        hero.setStartingLife(17);
        hero.setMaxLife(40);
        players.add(hero);
        players.add(new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("Boss", null)));
        Game game = new Match(new GameRules(GameType.Constructed), players, "Test").createGame();

        for (Player p : game.getPlayers()) {
            if (p.getName().equals("Hero")) {
                assertEquals(17, p.getLife());
                assertEquals(40, p.getMaxLife());
            } else {
                assertEquals(0, p.getMaxLife());
            }
        }
    }
}
