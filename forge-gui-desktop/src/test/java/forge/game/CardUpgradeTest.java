package forge.game;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.model.FModel;
import forge.view.TimeLimitedCodeBlock;

/** Per-card-name upgrades applied when the game starts. */
public class CardUpgradeTest extends AITest {

    private Deck deck(String commander) {
        Deck d = new Deck("d");
        d.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Savannah Lions"), 10);
        d.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Plains"), 30);
        if (commander != null) {
            d.getOrCreate(DeckSection.Commander).add(FModel.getMagicDb().getCommonCards().getCard(commander));
        }
        return d;
    }

    private Player byName(Game game, String name) {
        for (Player p : game.getPlayers()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        throw new IllegalStateException(name);
    }

    /** Start a real game, run the check once the opening hands are dealt, then stop the game. */
    private void atGameStart(RegisteredPlayer hero, Consumer<Game> check) throws Exception {
        initAndCreateGame(); // loads the card database
        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(hero.setPlayer(new LobbyPlayerAi("Hero", null)));
        players.add(new RegisteredPlayer(deck(null)).setPlayer(new LobbyPlayerAi("Boss", null)));
        Match match = new Match(new GameRules(GameType.Constructed), players, "Test");
        final Game game = match.createGame();
        game.setNoGUIUser();
        final Throwable[] failure = new Throwable[1];
        TimeLimitedCodeBlock.runWithTimeout(() -> match.startGame(game, () -> {
            try {
                check.accept(game);
            } catch (Throwable t) {
                failure[0] = t;
            }
            game.setGameOver(GameEndReason.Draw);
        }), 120, TimeUnit.SECONDS);
        if (failure[0] instanceof Error) {
            throw (Error) failure[0];
        } else if (failure[0] != null) {
            throw new RuntimeException(failure[0]);
        }
    }

    @Test
    public void upgradesAddUpOnEveryCopy() throws Exception {
        RegisteredPlayer hero = new RegisteredPlayer(deck(null));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.pt(1, 1));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.pt(1, 0));
        atGameStart(hero, game -> {
            int seen = 0;
            for (Card c : byName(game, "Hero").getCardsIn(ZoneType.Library, ZoneType.Hand)) {
                if (c.getName().equals("Savannah Lions")) {
                    assertEquals(4, c.getNetPower());     // 2/1 base
                    assertEquals(2, c.getNetToughness());
                    seen++;
                }
            }
            assertEquals(10, seen);
        });
    }

    @Test
    public void opponentsCopiesAreUntouched() throws Exception {
        RegisteredPlayer hero = new RegisteredPlayer(deck(null));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.pt(1, 1));
        atGameStart(hero, game -> {
            for (Card c : byName(game, "Boss").getCardsIn(ZoneType.Library, ZoneType.Hand)) {
                if (c.getName().equals("Savannah Lions")) {
                    assertEquals(2, c.getNetPower());
                    assertFalse(c.hasPerpetual());
                }
            }
        });
    }

    @Test
    public void upgradesStayThroughZoneChanges() throws Exception {
        RegisteredPlayer hero = new RegisteredPlayer(deck(null));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.pt(1, 1));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.keywords("Vigilance"));
        atGameStart(hero, game -> {
            Card lions = byName(game, "Hero").getCardsIn(ZoneType.Library, "Savannah Lions").get(0);
            Card onField = game.getAction().moveToPlay(lions, null, null);
            assertEquals(3, onField.getNetPower());
            assertTrue(onField.hasKeyword(Keyword.VIGILANCE));
            Card inGrave = game.getAction().moveToGraveyard(onField, null);
            Card back = game.getAction().moveToPlay(inGrave, null, null);
            assertEquals(3, back.getNetPower());
            assertTrue(back.hasKeyword(Keyword.VIGILANCE));
        });
    }

    @Test
    public void commanderIsUpgradedInTheCommandZone() throws Exception {
        RegisteredPlayer hero = new RegisteredPlayer(deck("Isamaru, Hound of Konda"));
        hero.assignCommander();
        hero.addCardUpgrade("Isamaru, Hound of Konda", CardUpgrade.pt(2, 2));
        atGameStart(hero, game -> {
            List<Card> commanders = byName(game, "Hero").getCommanders();
            assertEquals(1, commanders.size());
            assertEquals(4, commanders.get(0).getNetPower()); // 2/2 base
            assertTrue(commanders.get(0).isInZone(ZoneType.Command));
        });
    }

    @Test
    public void repeatedKeywordsAreKeptAsSeparateInstances() throws Exception {
        // Haste twice is still just haste; Flanking twice is two instances, which the rules make trigger twice.
        RegisteredPlayer hero = new RegisteredPlayer(deck(null));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.keywords("Haste"));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.keywords("Haste"));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.keywords("Flanking"));
        hero.addCardUpgrade("Savannah Lions", CardUpgrade.keywords("Flanking"));
        atGameStart(hero, game -> {
            Card lions = byName(game, "Hero").getCardsIn(ZoneType.Library, "Savannah Lions").get(0);
            Card onField = game.getAction().moveToPlay(lions, null, null);
            assertTrue(onField.hasKeyword(Keyword.HASTE));
            assertEquals(2, onField.getKeywords(Keyword.FLANKING).size());
        });
    }
}
