package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import org.testng.annotations.Test;

import forge.ai.AITest;

/** Two floors: the boss's rewards, the commander's rare upgrade, a full heal and a harder second floor. */
public class FloorsTest extends AITest {

    private RoguelikeContent content() {
        initAndCreateGame(); // loads the card database
        return RoguelikeContent.load();
    }

    /** Beat the boss of the current floor without playing it. */
    private static void beatTheBoss(Run run) {
        RunState s = run.getState();
        s.activeNodeId = s.map.bossId;
        s.lifeBeforeMatch = s.life;
        run.applyMatchResult(run.getActiveEncounter(), true, 7);
    }

    @Test
    public void beatingTheFirstBossLeadsToFloorTwo() {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 5);
        RunState s = run.getState();
        String firstBoss = s.map.get(s.map.bossId).encounterId;
        beatTheBoss(run);
        assertEquals(OfferKind.BOSS_RELIC, s.offer.kind);
        run.choose(0);

        Offer upgrades = s.offer;
        assertEquals(OfferKind.COMMANDER_UPGRADE, upgrades.kind);
        assertFalse("one has to be taken", upgrades.canSkip);
        assertEquals(3, upgrades.options.size());
        for (OfferOption o : upgrades.options) {
            assertEquals(s.commander, o.upgradeCard);
            assertTrue(c.getRareUpgrades().contains(o.upgrade));
        }
        RunUpgrade taken = upgrades.options.get(0).upgrade;
        run.choose(0);

        assertEquals(2, s.floor);
        assertEquals("healed to full", s.maxLife, s.life);
        assertTrue(s.upgrades.get(s.commander).contains(taken));
        assertEquals(RunStatus.IN_PROGRESS, s.status);
        assertFalse("no boss twice in a run", firstBoss.equals(s.map.get(s.map.bossId).encounterId));
        for (int start : s.map.startIds) {
            MapNode n = s.map.get(start);
            if (n.type == NodeType.ENEMY) {
                assertEquals("floor 2 starts at tier 2", 2, c.getEncounter(n.encounterId).tier);
            }
        }
    }

    @Test
    public void beatingTheLastBossWinsTheRun() {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 6);
        RunState s = run.getState();
        beatTheBoss(run);
        run.choose(0); // relic
        run.choose(0); // rare upgrade
        beatTheBoss(run);
        assertEquals(OfferKind.BOSS_RELIC, s.offer.kind);
        run.choose(0);
        assertEquals(RunStatus.WON, s.status);
    }

    @Test
    public void floorTwoEnemiesAreTougher() {
        RoguelikeContent c = content();
        Run run = Run.start(c, "jill_mono_blue", 7);
        EncounterDef dwarves = c.getEncounter("troop_of_dwarves");
        assertEquals(20, run.encounterLife(dwarves));
        assertTrue(run.encounterUpgrades(dwarves).isEmpty());

        run.getState().floor = 2;
        assertEquals(25, run.encounterLife(dwarves));
        assertEquals(2, run.encounterUpgrades(dwarves).size());
        assertEquals("the same every time", run.encounterUpgrades(dwarves).keySet(), run.encounterUpgrades(dwarves).keySet());
        String base = String.join("\n", run.getEncounterBase(dwarves).getRules().getMainPart().getTriggers());
        assertTrue(base, base.contains("GameActivationLimit$ 1"));
        assertNotNull(c.getConfig().floorRules.get(1).enemyBaseText);
    }
}
