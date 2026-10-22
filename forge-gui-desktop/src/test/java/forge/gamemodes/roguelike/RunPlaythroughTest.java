package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.testng.annotations.Test;

import com.google.common.eventbus.Subscribe;

import forge.ai.AITest;
import forge.ai.LobbyPlayerAi;
import forge.game.BasicLandEscalation;
import forge.game.Game;
import forge.game.GameEndReason;
import forge.game.ability.ApiType;
import forge.game.card.Card;
import forge.game.event.GameEventTurnPhase;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.gamemodes.roguelike.RunMatchFactory.RunMatch;
import forge.view.TimeLimitedCodeBlock;

/** Runs played start to finish with the AI on both sides. */
public class RunPlaythroughTest extends AITest {

    /** Lands seen on any player's battlefield at any phase, across all games played by this test instance. */
    private int mostLandsSeen;

    private Game play(final RunMatch m, final int landCap) {
        final Game game = m.match().createGame();
        game.setNoGUIUser();
        game.subscribeToEvents(new Object() {
            @Subscribe
            public void onPhase(GameEventTurnPhase ev) {
                for (Player p : game.getPlayers()) {
                    int lands = BasicLandEscalation.countLands(p);
                    mostLandsSeen = Math.max(mostLandsSeen, lands);
                    assertTrue(p + " has " + lands + " lands", lands <= landCap);
                }
            }
        });
        try {
            TimeLimitedCodeBlock.runWithTimeout(() -> m.match().startGame(game), 180, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            game.setGameOver(GameEndReason.Draw);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return game;
    }

    @Test(timeOut = 300000)
    public void everythingFromTheRunReachesTheMatch() throws Exception {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Run run = Run.start(c, "jill_mono_blue", 3);
        RunState s = run.getState();
        s.life = 14;
        s.relics.add("whetstone");
        s.relics.add("lucky_coin");
        s.deck.put("Cultivate", 2);
        s.upgrades.put("Faerie Seer", new ArrayList<>(List.of(new RunUpgrade(1, 1, List.of()))));
        run.enterNode(run.getAvailableNodes().get(0).id);
        RunMatch m = run.createMatch(new LobbyPlayerAi("Hero", null), new LobbyPlayerAi("Enemy", null));

        final Game game = m.match().createGame();
        game.setNoGUIUser();
        final List<String> problems = new ArrayList<>();
        TimeLimitedCodeBlock.runWithTimeout(() -> m.match().startGame(game, () -> {
            try {
                Player hero = null;
                for (Player p : game.getPlayers()) {
                    if (p.getRegisteredPlayer() == m.hero()) {
                        hero = p;
                    }
                }
                check(problems, hero.getLife() == 14, "hero life " + hero.getLife());
                check(problems, hero.getMaxLife() == 40, "hero max life " + hero.getMaxLife());
                check(problems, hero.getCommanders().size() == 1, "commander " + hero.getCommanders());
                Card base = null;
                for (Card card : hero.getCardsIn(ZoneType.Command)) {
                    if (card.getType().hasStringType("Base")) {
                        base = card;
                    }
                }
                check(problems, base != null && base.getName().equals("Wayfarer's Camp"), "base " + base);
                if (base != null) {
                    String text = base.getAbilityText(base.getCurrentState());
                    // each relic's text appears once, as its own line, not again in a list
                    check(problems, text.contains("Whetstone: Creatures you control get +1/+0.")
                            && text.contains("Lucky Coin") && !text.contains("Relics:")
                            && text.indexOf("Whetstone") == text.lastIndexOf("Whetstone"), "relic text " + text);
                }
                for (Card card : hero.getCardsIn(ZoneType.Library, ZoneType.Hand)) {
                    if (card.getName().equals("Faerie Seer")) {
                        check(problems, card.getNetPower() == 2 && card.getNetToughness() == 2,
                                "Faerie Seer " + card.getNetPower() + "/" + card.getNetToughness());
                    }
                    if (card.getName().equals("Cultivate")) {
                        SpellAbility sa = card.getFirstSpellAbility();
                        check(problems, sa.getApi() == ApiType.MakeCard, "Cultivate api " + sa.getApi());
                    }
                    check(problems, !card.isLand(), "land in deck: " + card);
                }
                // Whetstone pumps creatures on the battlefield
                Card seer = hero.getCardsIn(ZoneType.Library, "Faerie Seer").get(0);
                Card onField = game.getAction().moveToPlay(seer, null, null);
                game.getAction().checkStateEffects(true);
                check(problems, onField.getNetPower() == 3, "Faerie Seer with Whetstone " + onField.getNetPower());
            } catch (Throwable t) {
                problems.add(t.toString());
            }
            game.setGameOver(GameEndReason.Draw);
        }), 120, TimeUnit.SECONDS);
        assertEquals("[]", problems.toString());
    }

    /** Base supplies start each match as counters on the Base, and what a won match spends stays spent. */
    @Test(timeOut = 300000)
    public void baseSuppliesLastTheWholeRun() throws Exception {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Run run = Run.start(c, "jill_mono_blue", 3);
        run.getState().baseSupply = 3;
        for (int fight = 0; fight < 2; fight++) {
            final int expected = 3 - fight;
            if (run.getState().offer != null) {
                run.choose(0); // the card reward
            }
            MapNode next = run.getAvailableNodes().get(0);
            next.type = NodeType.ENEMY; // whatever the map rolled, make it a fight
            next.encounterId = "troop_of_dwarves";
            run.enterNode(next.id);
            RunMatch m = run.createMatch(new LobbyPlayerAi("Hero", null), new LobbyPlayerAi("Enemy", null));
            final Game game = m.match().createGame();
            game.setNoGUIUser();
            final List<String> problems = new ArrayList<>();
            TimeLimitedCodeBlock.runWithTimeout(() -> m.match().startGame(game, () -> {
                try {
                    Player hero = null;
                    Player enemy = null;
                    for (Player p : game.getPlayers()) {
                        if (p.getRegisteredPlayer() == m.hero()) {
                            hero = p;
                        } else {
                            enemy = p;
                        }
                    }
                    Card base = null;
                    for (Card card : hero.getCardsIn(ZoneType.Command)) {
                        if (card.getType().hasStringType("Base")) {
                            base = card;
                        }
                    }
                    int supply = base.getCounters(forge.game.card.CounterEnumType.SUPPLY);
                    check(problems, supply == expected, "supply at the start " + supply + ", expected " + expected);
                    String text = base.getAbilityText(base.getCurrentState());
                    check(problems, text.contains("Supplies: put a +1/+1 counter"), "Base text " + text);
                    base.subtractCounter(forge.game.card.CounterEnumType.SUPPLY, 1, hero); // as if the ability was used
                    enemy.concede();
                    game.getAction().checkGameOverCondition();
                } catch (Throwable t) {
                    problems.add(t.toString());
                }
            }), 120, TimeUnit.SECONDS);
            assertEquals("[]", problems.toString());
            assertTrue(run.finishMatch(m, game));
            assertEquals(expected - 1, run.getState().baseSupply);
        }
    }

    private static void check(List<String> problems, boolean ok, String what) {
        if (!ok) {
            problems.add(what);
        }
    }

    @Test(timeOut = 1800000)
    public void wholeRunsEndInAWinOrALoss() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        c.getConfig().mapLayout = List.of("FIGHTS", "STRETCH 2", "BOSS"); // keep the test short
        int runs = Integer.getInteger("roguelike.playthroughs", 2);
        for (long seed = 1; seed <= runs; seed++) {
            Run run = Run.start(c, "jill_mono_blue", seed);
            RunState s = run.getState();
            int matches = 0;
            for (int step = 0; step < 200 && s.status == RunStatus.IN_PROGRESS; step++) {
                if (s.offer != null) {
                    if (s.offer.kind == OfferKind.SHOP) {
                        int buy = -1;
                        for (int i = 0; i < s.offer.options.size(); i++) {
                            OfferOption o = s.offer.options.get(i);
                            if (!o.bought && o.price <= s.gold) {
                                buy = i;
                            }
                        }
                        if (buy >= 0) {
                            run.buy(buy);
                        } else {
                            run.skip();
                        }
                    } else if (s.offer.options.isEmpty()) {
                        run.skip();
                    } else {
                        run.choose(0);
                    }
                } else if (run.getActiveEncounter() != null) {
                    EncounterDef enc = run.getActiveEncounter();
                    RunMatch m = run.createMatch(new LobbyPlayerAi("Hero", null), new LobbyPlayerAi(enc.name, null));
                    run.finishMatch(m, play(m, c.getConfig().landCap));
                    matches++;
                } else {
                    List<MapNode> next = run.getAvailableNodes();
                    assertFalse("stuck: " + s.log, next.isEmpty());
                    run.enterNode(next.get(0).id);
                }
                assertTrue(s.life >= 1 && s.life <= s.maxLife);
                assertTrue(s.retriesLeft >= 0);
            }
            System.out.println("Run " + seed + ": " + s.status + " after " + matches + " matches\n  " + String.join("\n  ", s.log));
            assertTrue("run " + seed + " did not end: " + s.log, s.status != RunStatus.IN_PROGRESS);
            assertTrue(matches >= 1);
        }
        assertTrue("no game reached a land count of 5 or more", mostLandsSeen >= 5);
    }

    /** Phase 1 exit check: ramp decks under the landless rules, over several games. */
    @Test(timeOut = 1800000)
    public void rampDecksNeverPassTheCap() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        EncounterDef ramp = new EncounterDef();
        ramp.id = "ramp";
        ramp.name = "Ramp";
        for (String card : List.of("Rampant Growth", "Cultivate", "Kodama's Reach", "Explosive Vegetation",
                "Wood Elves", "Sakura-Tribe Elder", "Harrow", "Craw Wurm", "Hill Giant", "Grizzly Bears")) {
            ramp.deck.put(card, 4);
        }
        c.getEncounters().put(ramp.id, ramp);
        StarterDef starter = new StarterDef();
        starter.id = "ramp_starter";
        starter.name = "Ramp";
        starter.commander = "Omnath, Locus of Mana";
        starter.deck.putAll(ramp.deck);
        c.getStarters().put(starter.id, starter);

        int games = Integer.getInteger("roguelike.soakGames", 3);
        int treasureGames = 0;
        for (long seed = 1; seed <= games; seed++) {
            Run run = Run.start(c, starter.id, seed);
            run.getState().map.get(run.getAvailableNodes().get(0).id).encounterId = ramp.id;
            run.enterNode(run.getAvailableNodes().get(0).id);
            RunMatch m = run.createMatch(new LobbyPlayerAi("Hero", null), new LobbyPlayerAi("Enemy", null));
            Game g = play(m, c.getConfig().landCap);
            assertTrue(g.isGameOver());
            for (Player p : g.getRegisteredPlayers()) {
                for (Card card : p.getCardsIn(ZoneType.Battlefield)) {
                    if (card.isToken() && card.getType().hasSubtype("Treasure")) {
                        treasureGames++;
                        break;
                    }
                }
            }
        }
        System.out.println("Ramp soak: " + games + " games, most lands seen " + mostLandsSeen
                + ", players ending a game with a Treasure: " + treasureGames);
        assertEquals("a ramp game should reach the cap", 10, mostLandsSeen);
        assertNotNull(c);
    }
}
