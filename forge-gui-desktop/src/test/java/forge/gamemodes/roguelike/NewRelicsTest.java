package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/** Magpie's Eye and Hostile Makeover (2026-10-23), in a real game, and where rare relics are offered. */
public class NewRelicsTest extends AITest {

    private Game game;
    private Player me;
    private Player opp;

    /** A game with the relic on my Base; the AI plays me, so it makes the relic's choices. */
    private void setUp(final String relic, final PhaseType phase, final boolean myTurn) {
        game = initAndCreateGame();
        me = game.getPlayers().get(1);
        opp = game.getPlayers().get(0);
        fillLibrary(me, 10);
        fillLibrary(opp, 10);
        RoguelikeContent c = RoguelikeContent.load();
        List<String> script = BaseAssembler.assemble(null, List.of(c.getRelic(relic)));
        me.getZone(ZoneType.Command).add(Card.fromPaperCard(BaseCard.fromScript(script), me));
        game.getPhaseHandler().devModeSet(phase, myTurn ? me : opp, 3); // not turn 1, which skips the draw
        game.getAction().checkStateEffects(true);
    }

    private void resolveTriggers() {
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
    }

    @Test
    public void magpiesEyeScriesBeforeTheDrawStepDraw() {
        setUp("magpies_eye", PhaseType.UPKEEP, true);
        int hand = me.getCardsIn(ZoneType.Hand).size();
        playUntilPhase(game, PhaseType.MAIN1);
        assertEquals("still one card, not more", hand + 1, me.getCardsIn(ZoneType.Hand).size());
        assertTrue("scried", me.getScryThisTurn() > 0);
    }

    @Test
    public void magpiesEyeWorksOnAnOpponentsTurnButNotOnMyMainPhase() {
        setUp("magpies_eye", PhaseType.MAIN1, false);
        int hand = me.getCardsIn(ZoneType.Hand).size();
        me.drawCard();
        assertEquals(hand + 1, me.getCardsIn(ZoneType.Hand).size());
        assertTrue("scried on their turn", me.getScryThisTurn() > 0);

        setUp("magpies_eye", PhaseType.MAIN1, true);
        me.drawCard();
        assertEquals("no scry on my own main phase", 0, me.getScryThisTurn());
    }

    @Test
    public void hostileMakeoverHitsTheFirstCreatureEachTurnOnly() {
        setUp("hostile_makeover", PhaseType.MAIN1, false);
        Card first = game.getAction().moveToPlay(addCardToZone("Giant Spider", opp, ZoneType.Hand), null, null); // 2/4
        resolveTriggers();
        boolean hit = first.isTapped() && first.getCounters(CounterEnumType.STUN) == 1
                || first.getCounters(CounterEnumType.M1M1) == 1
                || first.getNetPower() == 4 && first.getNetToughness() == 2;
        assertTrue("one of the three modes happened: " + first.getNetPower() + "/" + first.getNetToughness(), hit);
        Card second = game.getAction().moveToPlay(addCardToZone("Grizzly Bears", opp, ZoneType.Hand), null, null);
        resolveTriggers();
        assertFalse("once per turn", second.isTapped());
        assertEquals(0, second.getCounters(CounterEnumType.M1M1));
        assertEquals(0, second.getCounters(CounterEnumType.STUN));
    }

    @Test
    public void rareRelicsComeFromBossesAndOnlyRarelyFromShops() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        assertTrue(c.getRelic("hostile_makeover").rare);
        assertFalse(c.getRelic("magpies_eye").rare);

        c.getConfig().rareRelicChance = 0;
        for (long seed = 1; seed <= 20; seed++) {
            for (OfferOption o : shop(c, seed).options) {
                assertFalse("a rare relic in a shop at 0%", "hostile_makeover".equals(o.relicId));
            }
        }
        c.getConfig().rareRelicChance = 1;
        int rare = 0;
        for (OfferOption o : shop(c, 1).options) {
            rare += o.relicId != null && c.getRelic(o.relicId).rare ? 1 : 0;
        }
        assertEquals("every relic slot is rare at 100%", c.getConfig().shopRelicChoices, rare);

        // a boss offers rare relics first, then normal ones once the rare ones run out
        c.getConfig().bossRelicChoices = 50;
        Run run = Run.start(c, "jill_mono_blue", 3);
        RunState s = run.getState();
        s.activeNodeId = s.map.bossId;
        s.lifeBeforeMatch = s.life;
        run.applyMatchResult(run.getActiveEncounter(), true, 7);
        assertEquals(OfferKind.BOSS_RELIC, s.offer.kind);
        assertTrue(s.offer.options.stream().anyMatch(o -> "hostile_makeover".equals(o.relicId)));
        assertTrue(s.offer.options.stream().anyMatch(o -> "magpies_eye".equals(o.relicId)));
    }

    private static Offer shop(RoguelikeContent c, long seed) {
        Run run = Run.start(c, "jill_mono_blue", seed);
        RunState s = run.getState();
        MapNode n = s.map.get(s.map.startIds.get(0));
        n.type = NodeType.SHOP;
        n.encounterId = null;
        s.gold = 0;
        run.enterNode(n.id);
        return s.offer;
    }
}
