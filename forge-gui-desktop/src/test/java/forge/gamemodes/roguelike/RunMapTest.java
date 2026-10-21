package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertNotNull;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

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
                    assertEquals("first row is enemies", NodeType.ENEMY, n.type);
                }
                if (n.type.isMatch()) {
                    assertNotNull(n.encounterId);
                    EncounterDef e = c.getEncounter(n.encounterId);
                    assertEquals(n.type == NodeType.BOSS, e.boss);
                    assertTrue(e.boss || e.minRow <= n.row);
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
    public void sameSeedSameMap() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Gson gson = new Gson();
        assertEquals(gson.toJson(RunMap.generate(c.getConfig(), c, new Random(42))),
                gson.toJson(RunMap.generate(c.getConfig(), c, new Random(42))));
    }
}
