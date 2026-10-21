package forge.game;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.ai.PlayerControllerAi;
import forge.card.ICardFace;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.event.GameEventTurnPhase;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.player.RegisteredPlayer;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.model.FModel;
import forge.view.TimeLimitedCodeBlock;

/** Landless play: the per-turn conjured basic land and the land cap. */
public class BasicLandEscalationTest extends AITest {

    private int countInZone(Player p, String name, ZoneType zone) {
        int n = 0;
        for (Card c : p.getCardsIn(zone)) {
            if (c.getName().equals(name)) {
                n++;
            }
        }
        return n;
    }

    @Test
    public void landCapStopsLandsEnteringBeyondIt() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        for (int i = 0; i < 14; i++) {
            Card c = addCardToZone("Island", p, ZoneType.Hand);
            game.getAction().moveToPlay(c, null, null);
        }
        assertEquals(10, BasicLandEscalation.countLands(p));
        assertEquals("lands that could not enter stay where they were", 4, countInZone(p, "Island", ZoneType.Hand));
    }

    @Test
    public void noLandCapByDefault() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        for (int i = 0; i < 14; i++) {
            Card c = addCardToZone("Island", p, ZoneType.Hand);
            game.getAction().moveToPlay(c, null, null);
        }
        assertEquals(14, BasicLandEscalation.countLands(p));
    }

    @Test
    public void aiConjuresTheColourItsHandNeeds() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        Card made = BasicLandEscalation.conjureChosenBasicLand(p);
        assertNotNull(made);
        assertEquals("Mountain", made.getName());
        assertTrue(made.isInPlay());
    }

    @Test
    public void aiSplashesOnceTheFirstColourIsCovered() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCardToZone("Savannah Lions", p, ZoneType.Hand);  // {W}
        addCardToZone("Savannah Lions", p, ZoneType.Hand);
        addCardToZone("Mulldrifter", p, ZoneType.Hand);     // {4}{U}
        addCardToZone("Mulldrifter", p, ZoneType.Hand);
        assertEquals("Plains", BasicLandEscalation.conjureChosenBasicLand(p).getName());
        assertEquals("Island", BasicLandEscalation.conjureChosenBasicLand(p).getName());
    }

    @Test
    public void aiWithNoCommanderAndNoColouredCardsStillGetsALand() {
        // late in a long game hand and library can hold no coloured cards; without a commander there is no
        // colour identity to fall back on either (this used to throw)
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        assertTrue(p.getCommanders().isEmpty());
        Card made = BasicLandEscalation.conjureChosenBasicLand(p);
        assertNotNull(made);
        assertTrue(made.isLand());
    }

    @Test
    public void onlyTheDecksColoursAreOffered() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Savannah Lions", p, ZoneType.Library); // W
        addCardToZone("Mulldrifter", p, ZoneType.Hand);       // U
        addCardToZone("Mountain", p, ZoneType.Library);       // lands don't count
        addCard("Swamp", p);
        assertEquals(List.of("Plains", "Island"), BasicLandEscalation.basicLandOptions(p));
    }

    @Test
    public void aDeckWithNoColourIsOfferedEveryBasic() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Ornithopter", p, ZoneType.Library);
        assertEquals(5, BasicLandEscalation.basicLandOptions(p).size());
    }

    @Test
    public void aOneColourDeckGetsItsLandWithoutBeingAsked() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCardToZone("Mulldrifter", p, ZoneType.Library);
        addCardToZone("Counterspell", p, ZoneType.Hand);
        final boolean[] asked = {false};
        p.runWithController(() -> {
            Card made = BasicLandEscalation.conjureChosenBasicLand(p);
            assertEquals("Island", made.getName());
        }, new PlayerControllerAi(game, p, p.getLobbyPlayer()) {
            @Override
            public String chooseBasicLandToConjure(List<String> basicLandNames) {
                asked[0] = true;
                return super.chooseBasicLandToConjure(basicLandNames);
            }
        });
        assertFalse("the player was asked", asked[0]);
    }

    @Test
    public void rampChoicesFollowTheDecksColours() {
        Game game = initAndCreateGame();
        game.getRules().setConjureBasicLandEachTurn(true);
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCardToZone("Mulldrifter", p, ZoneType.Library);    // U
        Card source = addCardToZone("Counterspell", p, ZoneType.Hand);
        final List<String> asked = new ArrayList<>();
        Runnable ramp = () -> {
            SpellAbility sa = AbilityFactory.getAbility(
                    "DB$ MakeCard | Choices$ Plains,Island,Swamp,Mountain,Forest | Zone$ Battlefield | Conjure$ True", source);
            sa.setActivatingPlayer(p);
            AbilityUtils.resolve(sa);
        };
        PlayerControllerAi recording = new PlayerControllerAi(game, p, p.getLobbyPlayer()) {
            @Override
            public String chooseCardName(SpellAbility sa, List<ICardFace> faces, String message) {
                List<String> names = new ArrayList<>();
                for (ICardFace f : faces) {
                    names.add(f.getName());
                }
                asked.add(names.toString());
                return super.chooseCardName(sa, faces, message);
            }
        };
        p.runWithController(ramp, recording);
        assertEquals("a one-colour deck isn't asked", 0, asked.size());
        assertEquals(1, countInZone(p, "Island", ZoneType.Battlefield));

        addCardToZone("Savannah Lions", p, ZoneType.Library); // W
        p.runWithController(ramp, recording);
        assertEquals(List.of("[Plains, Island]"), asked);
    }

    @Test
    public void nothingIsConjuredAtTheCap() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        for (int i = 0; i < 10; i++) {
            addCard("Island", p);
        }
        assertNull(BasicLandEscalation.conjureChosenBasicLand(p));
        assertEquals(10, BasicLandEscalation.countLands(p));
        assertEquals("the built-in land never turns into a Treasure", 0, treasures(p).size());
    }

    private List<Card> treasures(Player p) {
        List<Card> out = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getType().hasSubtype("Treasure")) {
                out.add(c);
            }
        }
        return out;
    }

    private void resolveRamp(Player p, String extra) {
        SpellAbility sa = AbilityFactory.getAbility(
                "DB$ MakeCard | Choices$ Plains,Island,Swamp,Mountain,Forest | Zone$ Battlefield | Conjure$ True" + extra,
                addCardToZone("Savannah Lions", p, ZoneType.Hand));
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);
    }

    @Test
    public void rampAtTheCapMakesAnUntappedTreasure() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCards("Island", 10, p);
        resolveRamp(p, "");
        assertEquals(10, BasicLandEscalation.countLands(p));
        assertEquals(1, treasures(p).size());
        assertFalse(treasures(p).get(0).isTapped());
    }

    @Test
    public void tappedRampAtTheCapMakesATappedTreasure() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCards("Island", 10, p);
        resolveRamp(p, " | Tapped$ True");
        assertEquals(1, treasures(p).size());
        assertTrue(treasures(p).get(0).isTapped());
    }

    @Test
    public void rampWithRoomForOneMakesOneLandAndOneTreasure() {
        Game game = initAndCreateGame();
        game.getRules().setMaxLandsPerPlayer(10);
        Player p = game.getPlayers().get(1);
        addCards("Island", 9, p);
        resolveRamp(p, " | Amount$ 2 | Tapped$ True");
        assertEquals(10, BasicLandEscalation.countLands(p));
        assertEquals(1, treasures(p).size());
        assertTrue(treasures(p).get(0).isTapped());
    }

    @Test
    public void rampWithoutACapMakesNoTreasure() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCards("Island", 10, p);
        resolveRamp(p, "");
        assertEquals(11, BasicLandEscalation.countLands(p));
        assertEquals(0, treasures(p).size());
    }

    @Test
    public void aiConjureSpellPicksTheBasicItNeeds() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        addCardToZone("Lightning Bolt", p, ZoneType.Hand);
        SpellAbility sa = AbilityFactory.getAbility(
                "DB$ MakeCard | Choices$ Plains,Island,Swamp,Mountain,Forest | Zone$ Battlefield | Conjure$ True",
                addCard("Savannah Lions", p));
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);
        assertEquals(1, countInZone(p, "Mountain", ZoneType.Battlefield));
        assertEquals(1, BasicLandEscalation.countLands(p));
    }

    @Test
    public void aiConjureChoiceIsUnchangedWhenNotAllBasics() {
        Game game = initAndCreateGame();
        Player p = game.getPlayers().get(1);
        addCardToZone("Mulldrifter", p, ZoneType.Hand);
        SpellAbility sa = AbilityFactory.getAbility(
                "DB$ MakeCard | Choices$ Shock,Island | Zone$ Hand | Conjure$ True", addCard("Savannah Lions", p));
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);
        assertEquals("non-land choices keep the old first-option pick", 1, countInZone(p, "Shock", ZoneType.Hand));
    }

    @Test
    public void strippingRemovesLandsButKeepsSpellsWithALandBack() {
        initAndCreateGame(); // loads the card database
        Deck deck = new Deck("d");
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Savannah Lions"), 4);
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Plains"), 10);
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Mutavault"), 2);
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Dryad Arbor"), 1);
        deck.getMain().add(FModel.getMagicDb().getCommonCards().getCard("Bala Ged Recovery"), 2); // spell // land
        deck.getOrCreate(DeckSection.Sideboard).add(FModel.getMagicDb().getCommonCards().getCard("Island"), 3);
        deck.getOrCreate(DeckSection.Sideboard).add(FModel.getMagicDb().getCommonCards().getCard("Shock"), 1);

        Deck stripped = BasicLandEscalation.withoutLands(deck);
        assertEquals(6, stripped.getMain().countAll());
        assertEquals(4, stripped.getMain().count(FModel.getMagicDb().getCommonCards().getCard("Savannah Lions")));
        assertEquals(1, stripped.get(DeckSection.Sideboard).countAll());
        assertEquals("the original deck is untouched", 19, deck.getMain().countAll());
    }

    private Deck landlessDeck(String name, String... cards) {
        Deck d = new Deck(name);
        for (String c : cards) {
            d.getMain().add(FModel.getMagicDb().getCommonCards().getCard(c), 10);
        }
        return d;
    }

    /** A whole AI-vs-AI game with landless decks: one land per own turn, up to 10, in the colours each deck needs. */
    @Test(timeOut = 300000)
    public void fullGameGivesOneLandPerTurnUpToTheCap() throws Exception {
        initAndCreateGame(); // loads the card database

        List<RegisteredPlayer> players = new ArrayList<>();
        players.add(new RegisteredPlayer(landlessDeck("Hero", "Savannah Lions", "Wall of Omens", "Man-o'-War", "Mulldrifter"))
                .setPlayer(new LobbyPlayerAi("Hero", null)));
        players.add(new RegisteredPlayer(landlessDeck("Boss", "Lightning Bolt", "Goblin Piker", "Hill Giant", "Shock"))
                .setPlayer(new LobbyPlayerAi("Boss", null)));
        GameRules rules = new GameRules(GameType.Constructed);
        rules.setConjureBasicLandEachTurn(true);
        rules.setMaxLandsPerPlayer(10);
        Match match = new Match(rules, players, "Test");
        final Game game = match.createGame();
        game.setNoGUIUser();

        final Map<String, List<Integer>> landsAtOwnCombat = new LinkedHashMap<>();
        landsAtOwnCombat.put("Hero", new ArrayList<>());
        landsAtOwnCombat.put("Boss", new ArrayList<>());
        game.subscribeToEvents(new Object() {
            @Subscribe
            public void onPhase(GameEventTurnPhase ev) {
                if (ev.phase() != PhaseType.COMBAT_BEGIN) {
                    return;
                }
                Player active = game.getPhaseHandler().getPlayerTurn();
                landsAtOwnCombat.get(active.getName()).add(BasicLandEscalation.countLands(active));
            }
        });

        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> match.startGame(game), 240, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            game.setGameOver(GameEndReason.Draw);
        }

        for (Map.Entry<String, List<Integer>> e : landsAtOwnCombat.entrySet()) {
            List<Integer> seen = e.getValue();
            assertTrue(e.getKey() + " played too few turns to judge: " + seen, seen.size() >= 4);
            for (int turn = 0; turn < seen.size(); turn++) {
                assertEquals(e.getKey() + " lands on own turn " + (turn + 1) + " in " + seen,
                        Math.min(turn + 1, 10), seen.get(turn).intValue());
            }
        }

        Player boss = null;
        for (Player p : game.getRegisteredPlayers()) {
            assertEquals("no card of any kind is added to the command zone", 0, p.getCardsIn(ZoneType.Command).size());
            if (p.getName().equals("Boss")) {
                boss = p;
            }
        }
        assertNotNull(boss);
        for (Card c : boss.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand()) {
                assertEquals("a mono-red deck should only ever pick Mountains", "Mountain", c.getName());
            }
        }
    }
}
