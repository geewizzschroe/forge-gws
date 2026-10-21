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

import forge.ai.AITest;

/** The run's rules, one step at a time, on small hand-made maps. */
public class RunTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    /** A straight line of nodes of the given types, ending in the boss. */
    private Run runWithPath(RoguelikeContent c, NodeType... types) {
        Run run = Run.start(c, "jill_mono_blue", 7);
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
        assertEquals(6, offer.options.size()); // 3 relics + 3 cards
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
}
