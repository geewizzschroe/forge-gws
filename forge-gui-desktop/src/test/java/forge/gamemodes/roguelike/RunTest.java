package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.File;
import java.util.List;

import org.testng.annotations.Test;

import com.google.gson.Gson;

import forge.StaticData;
import forge.ai.AITest;
import forge.card.MagicColor;

/** The run's rules, one step at a time, on small hand-made maps. */
public class RunTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    /** A straight line of nodes of the given types, ending in the boss. */
    private Run runWithPath(RoguelikeContent c, NodeType... types) {
        return runWithPath(c, "jill_mono_blue", types);
    }

    private Run runWithPath(RoguelikeContent c, String starter, NodeType... types) {
        Run run = Run.start(c, starter, 7);
        RunMap map = new RunMap();
        for (int i = 0; i <= types.length; i++) {
            MapNode n = new MapNode();
            n.id = i;
            n.row = i;
            n.type = i < types.length ? types[i] : NodeType.BOSS;
            if (n.type == NodeType.ENEMY) {
                n.encounterId = "goblin_raiders";
            } else if (n.type == NodeType.BOSS) {
                n.encounterId = "boss_krenko";
            }
            if (i < types.length) {
                n.next.add(i + 1);
            }
            map.nodes.add(n);
        }
        map.startIds.add(0);
        map.bossId = types.length;
        run.getState().map = map;
        return run;
    }

    @Test
    public void startingStateFollowsTheStarterAndConfig() {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 1);
        RunState s = run.getState();
        assertEquals("Jill, Shiva's Dominant", s.commander);
        assertEquals(c.getStarter("jill_mono_blue").deck, s.deck);
        assertEquals(20, s.life);
        assertEquals(20, s.maxLife);
        assertEquals(1, s.retriesLeft);
        assertEquals(RunStatus.IN_PROGRESS, s.status);
        assertFalse(run.getAvailableNodes().isEmpty());
    }

    @Test
    public void restHealsUpToMaxAndMovesOn() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.REST);
        run.getState().life = 10;
        run.enterNode(0);
        assertEquals(16, run.getState().life); // 30% of 20
        assertEquals(Integer.valueOf(0), run.getState().currentNodeId);
        assertEquals(List.of(0), run.getState().visited);
        assertTrue(run.getState().log.toString(), run.getState().log.contains("Entered Step 1 - Rest"));
        assertEquals(1, run.getAvailableNodes().size());
        run.getState().life = 19;
        run.getState().currentNodeId = null;
        run.enterNode(0);
        assertEquals(20, run.getState().life);
    }

    @Test
    public void cutRemovesEveryCopyOfTheChosenName() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.CUT);
        run.enterNode(0);
        Offer offer = run.getState().offer;
        assertEquals(OfferKind.CUT, offer.kind);
        assertEquals(run.getState().deck.size(), offer.options.size());
        String name = offer.options.get(0).cutCard;
        int before = run.getState().deckSize();
        int copies = run.getState().deck.get(name);
        run.choose(0);
        assertFalse(run.getState().deck.containsKey(name));
        assertEquals(before - copies, run.getState().deckSize());
        assertNull(run.getState().offer);
    }

    @Test
    public void upgradeNodeUpgradesACreatureName() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.UPGRADE);
        run.enterNode(0);
        Offer offer = run.getState().offer;
        assertEquals(OfferKind.UPGRADE, offer.kind);
        assertEquals(3, offer.options.size());
        OfferOption o = offer.options.get(0);
        run.choose(0);
        assertEquals(List.of(o.upgrade), run.getState().upgrades.get(o.upgradeCard));
    }

    @Test
    public void shopSellsForGoldUntilYouLeave() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.SHOP);
        run.getState().gold = 70;
        run.enterNode(0);
        Offer offer = run.getState().offer;
        assertEquals(OfferKind.SHOP, offer.kind);
        assertEquals(7, offer.options.size()); // 3 relics + 3 cards + card removal
        int relic = -1;
        for (int i = 0; i < offer.options.size(); i++) {
            if (offer.options.get(i).relicId != null) {
                relic = i;
            }
        }
        run.buy(relic);
        assertEquals(10, run.getState().gold);
        assertEquals(1, run.getState().relics.size());
        try {
            run.buy(relic);
            throw new AssertionError("bought the same thing twice");
        } catch (IllegalStateException expected) {
            // fine
        }
        run.skip();
        assertNull(run.getState().offer);
        assertEquals(Integer.valueOf(0), run.getState().currentNodeId);
    }

    @Test
    public void maxLifeRelicRaisesMaxAndHeals() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.SHOP);
        run.getState().life = 12;
        run.getState().gold = 1000;
        run.enterNode(0);
        Offer offer = run.getState().offer;
        offer.options.get(0).relicId = "iron_heart";
        offer.options.get(0).bought = false;
        run.buy(0);
        assertEquals(25, run.getState().maxLife);
        assertEquals(17, run.getState().life);
    }

    @Test
    public void nodesMustBeReachable() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.REST, NodeType.REST);
        try {
            run.enterNode(1);
            throw new AssertionError("entered a node that isn't reachable");
        } catch (IllegalStateException expected) {
            // fine
        }
        run.enterNode(0);
        run.enterNode(1);
        assertEquals(1, run.getAvailableNodes().size());
        assertEquals(NodeType.BOSS, run.getAvailableNodes().get(0).type);
    }

    @Test
    public void saveAndLoadKeepTheWholeState() throws Exception {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 99);
        run.getState().relics.add("whetstone");
        run.getState().upgrades.put("Wind Drake", new java.util.ArrayList<>(List.of(new RunUpgrade(1, 1, List.of("Haste")))));
        run.enterNode(run.getAvailableNodes().get(0).id);
        File f = File.createTempFile("run", ".json");
        f.deleteOnExit();
        run.save(f);
        Run loaded = Run.load(c, f);
        Gson gson = new Gson();
        assertEquals(gson.toJson(run.getState()), gson.toJson(loaded.getState()));
        assertNotNull(loaded.getActiveEncounter());
        assertTrue(loaded.getAvailableNodes().isEmpty());
    }

    /** The reward offered after winning the first fight, with every reward a bucket offer. */
    private Offer bucketReward(RoguelikeContent c, String starter, double offColourChance) {
        c.getConfig().bucketRewardChance = 1;
        c.getConfig().offColourBucketChance = offColourChance;
        Run run = runWithPath(c, starter, NodeType.ENEMY);
        run.enterNode(0);
        run.applyMatchResult(run.getActiveEncounter(), true, 20);
        return run.getState().offer;
    }

    @Test
    public void bucketRewardsStayInTheRunsColours() {
        RoguelikeContent c = content();
        Offer offer = bucketReward(c, "jill_mono_blue", 0);
        assertFalse(offer.options.isEmpty());
        for (OfferOption o : offer.options) {
            assertFalse(o.label, o.label.contains("(splash)"));
            for (String card : o.cards.keySet()) {
                byte identity = StaticData.instance().getCommonCards().getCard(card).getRules().getColorIdentity().getColor();
                assertEquals(card + " is outside blue", 0, identity & ~MagicColor.BLUE);
            }
        }
    }

    @Test
    public void lightningIsOfferedRedAndWhiteBuckets() {
        RoguelikeContent c = content();
        Offer offer = bucketReward(c, "lightning_rw", 0);
        assertEquals(3, offer.options.size());
        for (OfferOption o : offer.options) {
            assertFalse(o.label, o.label.contains("(splash)"));
            for (String card : o.cards.keySet()) {
                byte identity = StaticData.instance().getCommonCards().getCard(card).getRules().getColorIdentity().getColor();
                assertEquals(card + " is outside red-white", 0, identity & ~(MagicColor.RED | MagicColor.WHITE));
            }
        }
    }

    @Test
    public void withNoBucketInTheRunsColoursTheRewardIsSingleCards() {
        RoguelikeContent c = content();
        // keep only the blue and green buckets
        c.getPools().values().removeIf(p -> p.bucket && !List.of("bucket_fliers", "bucket_bounce", "bucket_ramp").contains(p.id));
        Offer offer = bucketReward(c, "lightning_rw", 0);
        assertFalse(offer.options.isEmpty());
        for (OfferOption o : offer.options) {
            assertNull(o.label);
            assertEquals(1, o.cards.size());
        }
    }

    @Test
    public void anOffColourBucketIsOfferedAsASplash() {
        RoguelikeContent c = content();
        Offer offer = bucketReward(c, "lightning_rw", 1);
        assertEquals(3, offer.options.size());
        int splashes = 0;
        for (OfferOption o : offer.options) {
            if (o.label.endsWith("(splash)")) {
                splashes++;
            }
        }
        assertEquals("one of the three is a splash", 1, splashes);
    }

    @Test
    public void cardRewardsCantBeSkipped() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.ENEMY);
        run.enterNode(0);
        run.applyMatchResult(run.getActiveEncounter(), true, 20);
        assertFalse(run.getState().offer.canSkip);
        try {
            run.skip();
            throw new AssertionError("skipped a card reward");
        } catch (IllegalStateException expected) {
            // fine
        }
    }

    @Test
    public void singleCardRewardsMixCuratedAndFullPoolSlots() {
        RoguelikeContent c = content();
        c.getConfig().bucketRewardChance = 0;
        List<String> curatedPool = c.getPool("blue_core").cards;
        for (int seed = 1; seed <= 20; seed++) {
            Run line = runWithPath(c, NodeType.ENEMY);
            line.getState().seed = seed;
            line.enterNode(0);
            line.applyMatchResult(line.getActiveEncounter(), true, 20);
            int curated = 0;
            int full = 0;
            for (OfferOption o : line.getState().offer.options) {
                String card = o.cards.keySet().iterator().next();
                if ("curated".equals(o.source)) {
                    curated++;
                    assertTrue(card + " is not in the curated pool", curatedPool.contains(card));
                } else {
                    assertEquals("full pool", o.source);
                    full++;
                    var rules = StaticData.instance().getCommonCards().getCard(card).getRules();
                    assertFalse(card + " is a land", rules.getMainPart().getType().isLand());
                    assertEquals(card + " is outside blue", 0, rules.getColorIdentity().getColor() & ~MagicColor.BLUE);
                }
            }
            assertEquals(3, curated + full);
            assertTrue("at least one curated slot", curated >= 1);
            assertTrue("at least one Full Pool slot", full >= 1);
        }
    }

    @Test
    public void theThroughTheAgesBucketTakesOnColourCardsOfItsSet() {
        RoguelikeContent c = content();
        c.getConfig().bucketRewardChance = 1;
        c.getConfig().offColourBucketChance = 0;
        c.getPools().values().removeIf(p -> p.bucket && !p.id.equals("bucket_fca"));
        Run run = runWithPath(c, NodeType.ENEMY);
        assertTrue(run.getState().unlockedPools.isEmpty());
        run.getState().unlockedPools.add("bucket_fca");
        run.enterNode(0);
        run.applyMatchResult(run.getActiveEncounter(), true, 20);
        Offer offer = run.getState().offer;
        assertEquals(1, offer.options.size());
        assertEquals("Through the Ages", offer.options.get(0).label);
        List<String> fca = FullPool.setCards("FCA");
        for (String card : offer.options.get(0).cards.keySet()) {
            assertTrue(card + " is not from Through the Ages", fca.contains(card));
            byte identity = StaticData.instance().getCommonCards().getCard(card).getRules().getColorIdentity().getColor();
            assertEquals(card + " is outside blue", 0, identity & ~MagicColor.BLUE);
        }
    }

    @Test
    public void theShopRemovesEveryCopyOfACardOncePerVisitForARisingFee() {
        RoguelikeContent c = content();
        Run run = runWithPath(c, NodeType.SHOP, NodeType.SHOP);
        run.getState().gold = 300;
        run.enterNode(0);
        int removal = removalOption(run.getState().offer);
        assertEquals(75, run.getState().offer.options.get(removal).price);

        run.buy(removal); // opens the choice; backing out is free
        assertEquals(OfferKind.CUT, run.getState().offer.kind);
        run.skip();
        assertEquals(OfferKind.SHOP, run.getState().offer.kind);
        assertEquals(300, run.getState().gold);

        run.buy(removal);
        String cut = run.getState().offer.options.get(0).cutCard;
        run.choose(0);
        assertFalse(run.getState().deck.containsKey(cut));
        assertEquals(225, run.getState().gold);
        assertEquals(OfferKind.SHOP, run.getState().offer.kind);
        assertTrue(run.getState().offer.options.get(removal).bought);
        try {
            run.buy(removal);
            throw new AssertionError("removed twice in one shop");
        } catch (IllegalStateException expected) {
            // fine
        }
        run.skip(); // leave

        run.enterNode(1);
        assertEquals(150, run.getState().offer.options.get(removalOption(run.getState().offer)).price);
    }

    private static int removalOption(Offer shop) {
        for (int i = 0; i < shop.options.size(); i++) {
            if (shop.options.get(i).removal) {
                return i;
            }
        }
        throw new AssertionError("no removal option");
    }
}
