package forge.ai.simulation;

import static org.testng.AssertJUnit.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.testng.annotations.Test;

import com.google.common.collect.Lists;

import forge.ai.AIOption;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.Game;
import forge.game.GameRules;
import forge.game.GameStage;
import forge.game.GameType;
import forge.game.Match;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.zone.ZoneType;
import forge.localinstance.properties.ForgePreferences.FPref;
import forge.model.FModel;

/** The simulation AI plays its cards under the run mode's landless rules (2026-10-28). */
public class LandlessSimulationTest extends SimulationTest {

    private Game game(final boolean landless, final boolean hybrid) {
        List<RegisteredPlayer> players = Lists.newArrayList();
        Deck d = new Deck();
        players.add(new RegisteredPlayer(d).setPlayer(new LobbyPlayerAi("p2", null)));
        Set<AIOption> options = new HashSet<>();
        options.add(hybrid ? AIOption.USE_HYBRID_SIMULATION : AIOption.USE_FULL_SIMULATION);
        players.add(new RegisteredPlayer(d).setPlayer(new LobbyPlayerAi("p1", options)));
        GameRules rules = new GameRules(landless ? GameType.Roguelike : GameType.Constructed);
        if (landless) {
            rules.setConjureBasicLandEachTurn(true);
            rules.setMaxLandsPerPlayer(10);
        }
        Match match = new Match(rules, players, "Test");
        Game game = new Game(players, rules, match);
        game.setAge(GameStage.Play);
        game.EXPERIMENTAL_RESTORE_SNAPSHOT = false;
        game.AI_TIMEOUT = FModel.getPreferences().getPrefInt(FPref.MATCH_AI_TIMEOUT);
        return game;
    }

    private void castsATwoDrop(final boolean landless, final boolean hybrid) {
        super.initAndCreateGame(); // loads the card database
        Game game = game(landless, hybrid);
        Player p = game.getPlayers().get(1);
        Player opp = game.getPlayers().get(0);
        fillLibrary(p, 10);
        fillLibrary(opp, 10);
        addCards("Island", 2, p);
        addCardToZone("Burrog Befuddler", p, ZoneType.Hand); // {1}{U}
        addCard("Grizzly Bears", opp); // something for its enter trigger to target
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p, 3);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        boolean cast = false;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            cast |= c.getName().equals("Burrog Befuddler");
        }
        assertTrue((landless ? "landless" : "normal") + (hybrid ? " hybrid" : " full") + ": cast the two-drop", cast);
    }

    @Test
    public void hybridSimulationCastsUnderNormalRules() {
        castsATwoDrop(false, true);
    }

    @Test
    public void hybridSimulationCastsUnderLandlessRules() {
        castsATwoDrop(true, true);
    }
}
