package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;
import java.util.Random;
import java.util.Set;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Planar interlopers (2026-10-24): an event's fight rolled from an encounter list, and their Bases. */
public class InterloperTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    /** A run whose first node is an Event that can only be the interloper event. */
    private Run atTheTear(final RoguelikeContent c) {
        EventDef tear = c.getEvents().get("planar_interloper");
        c.getEvents().clear();
        c.getEvents().put(tear.id, tear);
        Run run = Run.start(c, "lightning_rw", 9);
        MapNode n = run.getState().map.get(run.getState().map.startIds.get(0));
        n.type = NodeType.EVENT;
        n.encounterId = null;
        run.enterNode(n.id);
        return run;
    }

    @Test
    public void theTearOffersAFightFromTheListAndTheEventBecomesAMatch() {
        RoguelikeContent c = content();
        Run run = atTheTear(c);
        RunState s = run.getState();
        assertEquals(OfferKind.EVENT, s.offer.kind);
        assertTrue("it can be refused", s.offer.canSkip);
        OfferOption fight = s.offer.options.get(0);
        assertTrue(fight.fightEncounter, c.getConfig().encounterLists.get("interlopers").contains(fight.fightEncounter));
        s.life = 17;
        run.choose(0);
        assertNull(s.offer);
        EncounterDef enc = run.getActiveEncounter();
        assertEquals(fight.fightEncounter, enc.id);
        assertTrue(enc.interloper);
        assertEquals(17, s.lifeBeforeMatch);

        // winning gives cards from the interloper's own deck
        run.applyMatchResult(enc, true, 12);
        assertEquals(OfferKind.CARD_REWARD, s.offer.kind);
        assertEquals(c.getConfig().cardRewardChoices, s.offer.options.size());
        for (OfferOption o : s.offer.options) {
            String card = o.cards.keySet().iterator().next();
            assertTrue(card, enc.deck.containsKey(card));
            assertFalse(card.equals(enc.commander));
        }
        run.choose(0);
        assertNull(s.offer);
        assertTrue(s.eventEncountersMet.contains(enc.id));
    }

    @Test
    public void eachInterloperIsMetOnceARun() {
        RoguelikeContent c = content();
        Run run = atTheTear(c);
        RunState s = run.getState();
        s.eventEncountersMet.addAll(c.getConfig().encounterLists.get("interlopers"));
        MapNode next = s.map.get(s.map.startIds.get(1));
        next.type = NodeType.EVENT;
        run.skip();
        // with every interloper met, the tear doesn't open again: nothing else qualifies here, so nothing happens
        s.currentNodeId = null;
        s.visited.clear();
        run.enterNode(next.id);
        assertNull(s.offer);
    }

    @Test
    public void interlopersNeverAppearOnTheMap() {
        RoguelikeContent c = content();
        for (int seed = 0; seed < 100; seed++) {
            RunMap m = RunMap.generate(c.getConfig(), c.getConfig().mapLayout, c, new Random(seed), Set.of());
            for (MapNode n : m.nodes) {
                if (n.encounterId != null) {
                    assertFalse(n.encounterId, c.getEncounter(n.encounterId).interloper);
                }
            }
        }
    }

    @Test
    public void gallifreyFallsNoMoreFeedsTheWarDoctor() {
        Game game = initAndCreateGame();
        Player me = game.getPlayers().get(1);
        fillLibrary(me, 10);
        fillLibrary(game.getPlayers().get(0), 10);
        me.getZone(ZoneType.Command).add(Card.fromPaperCard(
                BaseCard.fromScript(RoguelikeContent.load().getBase("gallifrey_falls_no_more.txt")), me));
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, me, 3);
        Card doctor = addCard("The War Doctor", me);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals("a card exiled at the end step", 1, me.getCardsIn(ZoneType.Exile).size());
        assertEquals("and the War Doctor counted it", 1, doctor.getCounters(CounterEnumType.TIME));
    }

    @Test
    public void theJasmineDragonSurveilsEachEndStep() {
        Game game = initAndCreateGame();
        Player me = game.getPlayers().get(1);
        fillLibrary(me, 10);
        fillLibrary(game.getPlayers().get(0), 10);
        List<String> base = RoguelikeContent.load().getBase("the_jasmine_dragon.txt");
        me.getZone(ZoneType.Command).add(Card.fromPaperCard(BaseCard.fromScript(base), me));
        game.getPhaseHandler().devModeSet(PhaseType.MAIN2, me, 3);
        game.getAction().checkStateEffects(true);
        playUntilPhase(game, PhaseType.CLEANUP);
        assertEquals(1, me.getSurveilThisTurn());
    }
}
