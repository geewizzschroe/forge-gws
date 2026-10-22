package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import org.testng.annotations.Test;

import com.google.gson.Gson;

import forge.ai.AITest;

public class RunMapTest extends AITest {

    @Test
    public void mapsAreWellFormedOverManySeeds() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        for (long seed = 0; seed < 200; seed++) {
            RunMap map = RunMap.generate(c.getConfig(), c, new Random(seed));
            assertFalse(map.startIds.isEmpty());
            MapNode boss = map.get(map.bossId);
            assertEquals(NodeType.BOSS, boss.type);
            assertTrue(boss.next.isEmpty());
            assertTrue(c.getEncounter(boss.encounterId).boss);

            for (MapNode n : map.nodes) {
                assertEquals(n, map.get(n.id));
                if (n.row == 0) {
                    // fights, or Events where no unused tier-1 enemy was left
                    assertTrue("first row is fights or Events: " + n.type, n.type == NodeType.ENEMY || n.type == NodeType.EVENT);
                }
                if (n.type.isMatch()) {
                    assertNotNull(n.encounterId);
                    EncounterDef e = c.getEncounter(n.encounterId);
                    assertEquals(n.type == NodeType.BOSS, e.boss);
                    if (!e.boss) { // the default layout's ladder: T1 at the start, T1-2 then T2-3 in the stretches
                        int lo = n.row >= 5 ? 2 : 1;
                        int hi = n.row == 0 ? 1 : n.row >= 5 ? 3 : 2;
                        assertTrue(e.id + " (tier " + e.tier + ") on row " + n.row, e.tier >= lo && e.tier <= hi);
                    }
                } else {
                    assertEquals(null, n.encounterId);
                }
                for (int next : n.next) {
                    assertEquals("paths only go up one row", n.row + 1, map.get(next).row);
                }
            }

            // every node is reachable from the start, and every node leads to the boss
            Set<Integer> seen = new HashSet<>();
            Deque<Integer> todo = new ArrayDeque<>(map.startIds);
            while (!todo.isEmpty()) {
                int id = todo.pop();
                if (seen.add(id)) {
                    MapNode n = map.get(id);
                    assertTrue("dead end at " + n + " (seed " + seed + ")", n.type == NodeType.BOSS || !n.next.isEmpty());
                    todo.addAll(n.next);
                }
            }
            assertEquals(map.nodes.size(), seen.size());
        }
    }

    @Test
    public void everyPathGoesThroughTheTouchstonesAndStretchesBranch() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        assertEquals(List.of("FIGHTS T1", "UPGRADE", "STRETCH 2 T1-2", "SHOP", "STRETCH 3 T2-3", "REST", "BOSS"), c.getConfig().mapLayout);
        Random walk = new Random(1);
        int choices = 0;
        int walks = 0;
        for (long seed = 0; seed < 200; seed++) {
            RunMap map = RunMap.generate(c.getConfig(), c, new Random(seed));
            Map<Integer, List<MapNode>> rows = new TreeMap<>();
            for (MapNode n : map.nodes) {
                rows.computeIfAbsent(n.row, k -> new ArrayList<>()).add(n);
            }
            assertEquals(10, rows.size());
            assertEquals(3, map.startIds.size());
            assertEquals(List.of(NodeType.UPGRADE), types(rows.get(1)));
            assertEquals(List.of(NodeType.SHOP), types(rows.get(4)));
            assertEquals(List.of(NodeType.REST), types(rows.get(8)));
            assertEquals(List.of(NodeType.BOSS), types(rows.get(9)));
            for (int r : List.of(2, 3, 5, 6, 7)) {
                assertEquals("stretch row " + r, 3, rows.get(r).size());
                for (MapNode n : rows.get(r)) {
                    assertTrue(n.type + " in a stretch", List.of(NodeType.ENEMY, NodeType.SHOP, NodeType.REST, NodeType.WORKSHOP, NodeType.EVENT).contains(n.type));
                }
            }
            // a random walk counts the steps with a real choice
            MapNode at = map.get(map.startIds.get(walk.nextInt(3)));
            choices++; // which start
            while (at.type != NodeType.BOSS) {
                List<Integer> next = new ArrayList<>(at.next);
                if (next.size() > 1) {
                    choices++;
                }
                at = map.get(next.get(walk.nextInt(next.size())));
            }
            walks++;
        }
        double perRun = (double) choices / walks;
        assertTrue("real choices per run " + perRun, perRun >= 4.5);
    }

    /** No enemy twice on a floor: with the real content the extra fights become Events. */
    @Test
    public void noEnemyTwiceOnAFloor() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        for (long seed = 0; seed < 200; seed++) {
            RunMap map = RunMap.generate(c.getConfig(), c, new Random(seed));
            List<String> met = new ArrayList<>();
            int fightsAtStart = 0;
            for (MapNode n : map.nodes) {
                if (n.type == NodeType.ENEMY) {
                    assertFalse(n.encounterId + " twice on a floor (seed " + seed + ")", met.contains(n.encounterId));
                    met.add(n.encounterId);
                    fightsAtStart += n.row == 0 ? 1 : 0;
                }
            }
            long tierOne = c.getEncounters().values().stream().filter(e -> !e.boss && e.tier == 1).count();
            assertEquals("starts that keep their fight: one per unused tier-1 enemy", Math.min(3, tierOne), fightsAtStart);
        }
    }

    /** With enough enemies in every tier, every fight keeps its fight, and still no enemy repeats. */
    @Test
    public void enoughEnemiesFillEveryFight() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Gson gson = new Gson();
        EncounterDef template = c.getEncounter("troop_of_dwarves");
        for (int tier = 1; tier <= 3; tier++) {
            for (int i = 0; i < 6; i++) {
                EncounterDef copy = gson.fromJson(gson.toJson(template), EncounterDef.class);
                copy.id = "copy_" + tier + "_" + i;
                copy.tier = tier;
                c.getEncounters().put(copy.id, copy);
            }
        }
        for (long seed = 0; seed < 100; seed++) {
            RunMap map = RunMap.generate(c.getConfig(), c, new Random(seed));
            List<String> met = new ArrayList<>();
            for (MapNode n : map.nodes) {
                if (n.type == NodeType.ENEMY) {
                    assertFalse(n.encounterId + " twice", met.contains(n.encounterId));
                    met.add(n.encounterId);
                }
            }
            for (int start : map.startIds) {
                assertEquals("the start row stays all fights", NodeType.ENEMY, map.get(start).type);
            }
        }
    }

    @Test
    public void aCustomLayoutWorks() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        c.getConfig().mapLayout = List.of("EVENT", "STRETCH 1", "BOSS");
        RunMap map = RunMap.generate(c.getConfig(), c, new Random(3));
        assertEquals(List.of(0), map.startIds);
        assertEquals(NodeType.EVENT, map.get(0).type);
        assertEquals(3, map.get(0).next.size());
        assertEquals(NodeType.BOSS, map.get(map.bossId).type);
    }

    private static List<NodeType> types(List<MapNode> row) {
        List<NodeType> out = new ArrayList<>();
        for (MapNode n : row) {
            out.add(n.type);
        }
        return out;
    }

    @Test
    public void sameSeedSameMap() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Gson gson = new Gson();
        assertEquals(gson.toJson(RunMap.generate(c.getConfig(), c, new Random(42))),
                gson.toJson(RunMap.generate(c.getConfig(), c, new Random(42))));
    }
}
