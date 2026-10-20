package forge.game;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtil;
import forge.ai.ComputerUtilAbility;
import forge.ai.LobbyPlayerAi;
import forge.deck.Deck;
import forge.game.card.Card;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.model.FModel;
import forge.view.TimeLimitedCodeBlock;

/** Bases: a per-player command-zone card built from card-script text. */
public class BaseCardTest extends AITest {

    private static final List<String> MOUNTAIN_PASS = List.of(
            "Name:Mountain Pass",
            "ManaCost:no cost",
            "Types:Base",
            "T:Mode$ ChangesZone | Origin$ Any | Destination$ Battlefield | ValidCard$ Creature.Dwarf+YouCtrl"
                    + " | TriggerZones$ Command | Execute$ TrigTreasure"
                    + " | TriggerDescription$ Whenever a Dwarf you control enters, create a Treasure token.",
            "SVar:TrigTreasure:DB$ Token | TokenScript$ c_a_treasure_sac",
            "A:AB$ GainLife | Cost$ 1 | ActivationZone$ Command | ActivationLimit$ 1 | LifeAmount$ 2"
                    + " | SpellDescription$ You gain 2 life. Activate only once each turn.",
            "Oracle:Whenever a Dwarf you control enters, create a Treasure token.\\n{1}: You gain 2 life. Activate only once each turn.");

    private Card putBase(Player p) {
        Card base = Card.fromPaperCard(BaseCard.fromScript(MOUNTAIN_PASS), p);
        p.getZone(ZoneType.Command).add(base);
        return base;
    }

    private int treasures(Player p) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getType().hasSubtype("Treasure")) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void baseIsBuiltFromScript() {
        Card base = putBase(initAndCreateGame().getPlayers().get(1));
        assertEquals("Mountain Pass", base.getName());
        assertEquals("Base", base.getType().toString());
        assertTrue(base.getType().hasStringType("Base"));
        assertFalse(base.isPermanent());
    }

    @Test
    public void baseTriggerWorksFromTheCommandZone() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        putBase(p);
        game.getAction().checkStateEffects(true); // registers the Base's trigger, as any real game has by then
        Card dwarf = addCardToZone("Dwarven Trader", p, ZoneType.Hand);
        game.getAction().moveToPlay(dwarf, null, null);
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
        assertEquals(1, treasures(p));

        addCard("Savannah Lions", p); // not a Dwarf
        game.getStack().addAllTriggeredAbilitiesToStack();
        assertTrue(game.getStack().isEmpty());
    }

    @Test
    public void baseActivatedAbilityWorksOnceEachTurn() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, p);
        Card base = putBase(p);
        addCards("Mountain", 2, p);

        List<SpellAbility> abilities = ComputerUtilAbility.getSpellAbilities(ComputerUtilAbility.getAvailableCards(game, p), p);
        SpellAbility power = findSAWithPrefix(abilities, "{1}: You gain 2 life");
        assertNotNull("abilities offered: " + abilities, power);
        assertEquals(base, power.getHostCard());
        power.setActivatingPlayer(p);
        assertTrue(power.canPlay());

        assertTrue(ComputerUtil.handlePlayingSpellAbility(p, power, null));
        playUntilStackClear(game);
        assertEquals(22, p.getLife());
        assertFalse("only once each turn", power.canPlay());
    }

    @Test
    public void registeredBaseStartsInTheCommandZone() throws Exception {
        initAndCreateGame(); // loads the card database
        Deck deck = new Deck("d");
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Mountain"), 40);
        RegisteredPlayer hero = new RegisteredPlayer(deck);
        hero.setBase(BaseCard.fromScript(MOUNTAIN_PASS));
        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(hero.setPlayer(new LobbyPlayerAi("Hero", null)));
        players.add(new RegisteredPlayer(deck).setPlayer(new LobbyPlayerAi("Boss", null)));
        Match match = new Match(new GameRules(GameType.Constructed), players, "Test");
        final Game game = match.createGame();
        game.setNoGUIUser();
        final List<String> heroCommand = new ArrayList<>();
        final List<String> bossCommand = new ArrayList<>();
        TimeLimitedCodeBlock.runWithTimeout(() -> match.startGame(game, () -> {
            for (Player p : game.getPlayers()) {
                for (Card c : p.getCardsIn(ZoneType.Command)) {
                    (p.getName().equals("Hero") ? heroCommand : bossCommand).add(c.getName());
                }
            }
            game.setGameOver(GameEndReason.Draw);
        }), 120, TimeUnit.SECONDS);
        assertEquals(List.of("Mountain Pass"), heroCommand);
        assertTrue(bossCommand.isEmpty());
    }
}
