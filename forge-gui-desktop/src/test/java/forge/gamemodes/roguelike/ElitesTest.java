package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.testng.annotations.Test;

import forge.ai.AITest;

/** Elites (2026-10-23): where they appear, what they give, and bosses giving rare relics. */
public class ElitesTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    @Test
    public void elitesReplaceSomeFightsAfterTheFirstRow() {
        RoguelikeContent c = content();
        RunConfig cfg = c.getConfig();
        int total = 0;
        for (int floor = 1; floor <= 2; floor++) {
            List<String> layout = Run.floorRule(cfg, floor).layout != null ? Run.floorRule(cfg, floor).layout : cfg.mapLayout;
            for (int seed = 0; seed < 100; seed++) {
                RunMap m = RunMap.generate(cfg, layout, c, new Random(seed), Set.of());
                int firstRow = m.get(m.startIds.get(0)).row;
                Set<String> seen = new HashSet<>();
                int elites = 0;
                for (MapNode n : m.nodes) {
                    if (n.type == NodeType.ELITE) {
                        elites++;
                        assertTrue("not in the first row", n.row != firstRow);
                        assertTrue(n.encounterId, c.getEncounter(n.encounterId).elite);
                        assertTrue("each Elite once a floor", seen.add(n.encounterId));
                    } else if (n.type == NodeType.ENEMY) {
                        assertFalse("a normal fight never draws an Elite", c.getEncounter(n.encounterId).elite);
                    }
                }
                assertTrue(elites + " Elites", elites <= cfg.eliteMaxPerFloor);
                total += elites;
            }
        }
        assertTrue("Elites do appear: " + total, total > 0);

        // at a 100% chance, every floor has the most it can
        cfg.eliteChance = 1;
        RunMap m = RunMap.generate(cfg, c, new Random(1));
        long elites = m.nodes.stream().filter(n -> n.type == NodeType.ELITE).count();
        assertEquals(Math.min(cfg.eliteMaxPerFloor, c.getEncounters().values().stream().filter(e -> e.elite).count()), elites);
    }

    @Test
    public void anEliteGivesUpgradedCardsThenARelic() {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 4);
        RunState s = run.getState();
        MapNode n = s.map.get(s.map.startIds.get(0));
        n.type = NodeType.ELITE;
        n.encounterId = "elite_tatyova";
        s.activeNodeId = n.id;
        s.currentNodeId = null;
        s.lifeBeforeMatch = s.life;
        int gold = s.gold;
        run.applyMatchResult(c.getEncounter("elite_tatyova"), true, s.life);
        assertEquals(gold + c.getConfig().goldPerEnemy, s.gold);

        assertEquals(OfferKind.CARD_REWARD, s.offer.kind);
        for (OfferOption o : s.offer.options) {
            assertEquals("single cards, no buckets", 1, o.cards.size());
            String card = o.cards.keySet().iterator().next();
            if (!Run.isCreature(card) || run.usefulUpgrades(card).isEmpty()) {
                continue;
            }
            assertEquals("every creature comes upgraded: " + card, card, o.upgradeCard);
        }
        run.choose(0);

        assertEquals(OfferKind.RELIC_REWARD, s.offer.kind);
        assertEquals(c.getConfig().eliteRelicChoices, s.offer.options.size());
        int relics = s.relics.size();
        run.choose(0);
        assertEquals(relics + 1, s.relics.size());
        assertNull(s.offer);
        assertEquals(Integer.valueOf(n.id), s.currentNodeId);
    }

    @Test
    public void bossesGiveRareRelics() {
        RoguelikeContent c = content();
        long rare = c.getRelics().values().stream().filter(r -> r.rare).count();
        assertEquals("four rare relics", 4, rare);
        Run run = Run.start(c, "jill_mono_blue", 6);
        RunState s = run.getState();
        s.activeNodeId = s.map.bossId;
        s.lifeBeforeMatch = s.life;
        run.applyMatchResult(run.getActiveEncounter(), true, 7);
        assertEquals(OfferKind.BOSS_RELIC, s.offer.kind);
        assertEquals(c.getConfig().bossRelicChoices, s.offer.options.size());
        for (OfferOption o : s.offer.options) {
            assertTrue(o.relicId, c.getRelic(o.relicId).rare);
        }
    }

    @Test
    public void elitesHaveCommandersAnd25Life() {
        RoguelikeContent c = content();
        int elites = 0;
        for (EncounterDef e : c.getEncounters().values()) {
            if (e.elite) {
                elites++;
                assertTrue(e.id, e.commander != null);
                assertEquals(e.id, 25, e.life);
            }
        }
        assertEquals(4, elites);
    }
}
