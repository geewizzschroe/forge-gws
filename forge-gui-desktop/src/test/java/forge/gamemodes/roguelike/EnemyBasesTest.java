package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.ai.ComputerUtilMana;
import forge.game.BaseCard;
import forge.game.Game;
import forge.game.card.Card;
import forge.game.card.CounterEnumType;
import forge.game.phase.PhaseType;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;

/** Each enemy and boss Base drafted on 2026-10-22 does what its text says, in a real game. */
public class EnemyBasesTest extends AITest {

    private Game game;
    private Player me;
    private Player opp;

    /** A game on my precombat main phase with the given Base in my command zone. */
    private void setUp(final String baseFile) {
        game = initAndCreateGame();
        me = game.getPlayers().get(1);
        opp = game.getPlayers().get(0);
        fillLibrary(me, 10);
        fillLibrary(opp, 10);
        game.getPhaseHandler().devModeSet(PhaseType.MAIN1, me);
        Card base = Card.fromPaperCard(BaseCard.fromScript(RoguelikeContent.load().getBase(baseFile)), me);
        me.getZone(ZoneType.Command).add(base);
        game.getAction().checkStateEffects(true);
    }

    private void resolveTriggers() {
        game.getAction().checkStateEffects(true);
        game.getStack().addAllTriggeredAbilitiesToStack();
        playUntilStackClear(game);
    }

    private int tokens(final Player p, final String type) {
        int n = 0;
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isToken() && c.getType().hasCreatureType(type)) {
                n++;
            }
        }
        return n;
    }

    private Card ready(final String name, final Player p) {
        Card c = addCard(name, p);
        c.setSickness(false);
        return c;
    }

    @Test
    public void chocoboRanchMakesABirdOnlyWhenThereIsNone() {
        setUp("chocobo_ranch.txt");
        playUntilPhase(game, PhaseType.END_OF_TURN);
        resolveTriggers();
        assertEquals(1, tokens(me, "Bird"));
        playUntilNextTurn(game); // opponent's turn: not my end step
        playUntilNextTurn(game);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        resolveTriggers();
        assertEquals("a Bird is already there", 1, tokens(me, "Bird"));
    }

    @Test
    public void mognetCentralGainsLifeForMoogles() {
        setUp("mognet_central.txt");
        game.getAction().moveToPlay(addCardToZone("Delivery Moogle", me, ZoneType.Hand), null, null);
        resolveTriggers();
        assertEquals(21, me.getLife());
    }

    @Test
    public void crimsonCourtFeedsAVampireAfterLifegain() {
        setUp("crimson_court.txt");
        Card vampire = addCard("Bishop's Soldier", me);
        me.gainLife(2, null, null);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        resolveTriggers();
        assertEquals(1, vampire.getCounters(CounterEnumType.P1P1));
    }

    @Test
    public void chamberOfTheFaythRewardsASummonsEnd() {
        setUp("chamber_of_the_fayth.txt");
        Card summon = addCard("Summon: Choco/Mog", me);
        game.getAction().destroy(summon, null, true, null);
        resolveTriggers();
        assertEquals(22, me.getLife());
    }

    @Test
    public void malboroBogRotsTheOpponent() {
        setUp("malboro_bog.txt");
        Card theirs = addCard("Savannah Lions", opp);
        Card mine = addCard("Savannah Lions", me);
        game.getAction().destroy(theirs, null, true, null);
        resolveTriggers();
        assertEquals(19, opp.getLife());
        game.getAction().destroy(mine, null, true, null);
        resolveTriggers();
        assertEquals("my own creature dying doesn't cost me", 20, me.getLife());
    }

    @Test
    public void cactuarIslandNeedlesWhenAPlantAttacks() {
        setUp("cactuar_island.txt");
        ready("Cactuar", me); // 3/3 trample, against an empty board
        playUntilPhase(game, PhaseType.COMBAT_END);
        resolveTriggers();
        assertEquals("3 combat damage and 1 from the needles", 16, opp.getLife());
    }

    @Test
    public void wyrmRoostMakesDragonsCheaper() {
        setUp("wyrm_roost.txt");
        addCards("Mountain", 5, me);
        Card shivan = addCardToZone("Shivan Dragon", me, ZoneType.Hand); // {4}{R}{R}
        SpellAbility cast = shivan.getFirstSpellAbility();
        cast.setActivatingPlayer(me);
        assertTrue("six-mana Dragon with five lands", ComputerUtilMana.canPayManaCost(cast, me, 0, false));
    }

    @Test
    public void ancientGroveGrowsOnceThereAreFiveElves() {
        setUp("ancient_grove.txt");
        addCards("Llanowar Elves", 5, me);
        playUntilNextTurn(game);
        playUntilNextTurn(game); // to my next upkeep and beyond
        playUntilPhase(game, PhaseType.MAIN1);
        resolveTriggers();
        assertEquals(1, tokens(me, "Elf"));
    }

    @Test
    public void northernCraterAddsAJenovaCellEachCombat() {
        setUp("northern_crater.txt");
        playUntilPhase(game, PhaseType.COMBAT_END);
        resolveTriggers();
        assertEquals(1, tokens(me, "Horror"));
    }

    @Test
    public void dragoonBarracksChargesAttackingKnights() {
        setUp("dragoon_barracks.txt");
        ready("Knight of Grace", me); // 2/2 first strike, against an empty board
        playUntilPhase(game, PhaseType.COMBAT_END);
        resolveTriggers();
        assertEquals(17, opp.getLife());
    }

    @Test
    public void theFarplaneGrowsSagaCreatures() {
        setUp("the_farplane.txt");
        Card summon = addCard("Summon: Choco/Mog", me);
        game.getAction().checkStateEffects(true);
        assertEquals(4, summon.getNetPower());
        assertEquals(4, summon.getNetToughness());
        Card lions = addCard("Savannah Lions", me);
        game.getAction().checkStateEffects(true);
        assertEquals(2, lions.getNetPower());
    }

    @Test
    public void kefkasTowerMakesEveryoneLoot() {
        setUp("kefkas_tower.txt");
        addCardToZone("Savannah Lions", me, ZoneType.Hand);
        addCardToZone("Savannah Lions", opp, ZoneType.Hand);
        playUntilPhase(game, PhaseType.END_OF_TURN);
        resolveTriggers();
        assertEquals(1, me.getCardsIn(ZoneType.Graveyard).size());
        assertEquals(1, opp.getCardsIn(ZoneType.Graveyard).size());
        assertEquals(1, opp.getCardsIn(ZoneType.Hand).size());
    }

    @Test
    public void bigBridgeGivesEquippedCreaturesFirstStrike() {
        setUp("big_bridge.txt");
        Card lions = addCard("Savannah Lions", me);
        game.getAction().checkStateEffects(true);
        assertFalse(lions.hasFirstStrike());
        // an Equipment entering is attached by the Base (the AI rarely pays to equip on its own)
        Card pick = game.getAction().moveToPlay(addCardToZone("Goldvein Pick", me, ZoneType.Hand), null, null);
        resolveTriggers();
        assertTrue("attached on entering", pick.isAttachedToEntity());
        assertTrue(lions.hasFirstStrike());
    }
}
