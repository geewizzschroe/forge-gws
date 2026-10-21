package forge.game;

import static org.testng.AssertJUnit.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.model.FModel;
import forge.view.TimeLimitedCodeBlock;

/** A commander that dies in a roguelike-run match can go back to the command zone (rule 903.9a). */
public class RoguelikeCommanderTest extends AITest {

    /** Where the commander ends up after it dies, in a game of the given type. */
    private ZoneType zoneAfterDeath(GameType type) throws Exception {
        initAndCreateGame(); // loads the card database
        Deck deck = new Deck("d");
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Island"), 40);
        deck.getOrCreate(DeckSection.Commander).add(FModel.getMagicDb().getCommonCards().getCard("Jill, Shiva's Dominant"));
        RegisteredPlayer hero = new RegisteredPlayer(deck);
        hero.assignCommander();
        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(hero.setPlayer(new LobbyPlayerAi("Hero", null)));
        players.add(new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("Enemy", null)));
        Match match = new Match(new GameRules(type), players, "Test");
        final Game game = match.createGame();
        game.setNoGUIUser();
        final ZoneType[] where = new ZoneType[1];
        TimeLimitedCodeBlock.runWithTimeout(() -> match.startGame(game, () -> {
            for (Player p : game.getPlayers()) {
                if (p.getName().equals("Hero")) {
                    Card jill = p.getCommanders().get(0);
                    Card onField = game.getAction().moveToPlay(jill, null, null);
                    Card dead = game.getAction().moveToGraveyard(onField, null);
                    game.getAction().checkStateEffects(true);
                    where[0] = dead.getZone().getZoneType();
                }
            }
            game.setGameOver(GameEndReason.Draw);
        }), 120, TimeUnit.SECONDS);
        return where[0];
    }

    @Test
    public void deadCommanderReturnsToTheCommandZone() throws Exception {
        assertEquals(ZoneType.Command, zoneAfterDeath(GameType.Roguelike));
    }

    @Test
    public void constructedHasNoCommanderRulesSoItStaysDead() throws Exception {
        assertEquals(ZoneType.Graveyard, zoneAfterDeath(GameType.Constructed));
    }
}
