package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A board-style map: {@code rows} rows of nodes joined by paths that only go upward, then one Boss
 * node that every node in the last row leads to. Paths start at random columns of the first row and
 * step to the same or a neighbouring column each row, as in Slay the Spire.
 */
public class RunMap {
    public List<MapNode> nodes = new ArrayList<>();
    public List<Integer> startIds = new ArrayList<>();
    public int bossId;

    public MapNode get(final int id) {
        return nodes.get(id);
    }

    public static RunMap generate(final RunConfig cfg, final RoguelikeContent content, final Random rnd) {
        final int rows = Math.max(1, cfg.mapRows);
        final int cols = Math.max(1, cfg.mapColumns);
        RunMap map = new RunMap();
        MapNode[][] grid = new MapNode[rows][cols];

        for (int p = 0; p < Math.max(1, cfg.mapPathsFromStart); p++) {
            int col = rnd.nextInt(cols);
            MapNode prev = null;
            for (int row = 0; row < rows; row++) {
                if (row > 0) {
                    col = Math.max(0, Math.min(cols - 1, col + rnd.nextInt(3) - 1));
                }
                MapNode node = grid[row][col];
                if (node == null) {
                    node = new MapNode();
                    node.id = map.nodes.size();
                    node.row = row;
                    node.column = col;
                    node.type = row == 0 ? NodeType.ENEMY : pickType(cfg, rnd);
                    map.nodes.add(node);
                    grid[row][col] = node;
                    if (row == 0) {
                        map.startIds.add(node.id);
                    }
                }
                if (prev != null && !prev.next.contains(node.id)) {
                    prev.next.add(node.id);
                }
                prev = node;
            }
        }

        MapNode boss = new MapNode();
        boss.id = map.nodes.size();
        boss.row = rows;
        boss.column = cols / 2;
        boss.type = NodeType.BOSS;
        map.nodes.add(boss);
        map.bossId = boss.id;
        for (MapNode n : map.nodes) {
            if (n.row == rows - 1) {
                n.next.add(boss.id);
            }
        }

        for (MapNode n : map.nodes) {
            if (n.type.isMatch()) {
                n.encounterId = pickEncounter(content, n, rnd);
            }
        }
        return map;
    }

    private static NodeType pickType(final RunConfig cfg, final Random rnd) {
        double[] w = {cfg.weightEnemy, cfg.weightShop, cfg.weightRest, cfg.weightWorkshop, cfg.weightEvent};
        NodeType[] t = {NodeType.ENEMY, NodeType.SHOP, NodeType.REST, NodeType.WORKSHOP, NodeType.EVENT};
        double total = 0;
        for (double x : w) {
            total += x;
        }
        double roll = rnd.nextDouble() * total;
        for (int i = 0; i < w.length; i++) {
            roll -= w[i];
            if (roll < 0) {
                return t[i];
            }
        }
        return NodeType.ENEMY;
    }

    private static String pickEncounter(final RoguelikeContent content, final MapNode node, final Random rnd) {
        List<String> options = new ArrayList<>();
        for (EncounterDef e : content.getEncounters().values()) {
            if (e.boss == (node.type == NodeType.BOSS) && (e.boss || e.minRow <= node.row)) {
                options.add(e.id);
            }
        }
        if (options.isEmpty()) {
            throw new IllegalStateException("No " + (node.type == NodeType.BOSS ? "boss" : "enemy")
                    + " encounter fits row " + node.row);
        }
        return options.get(rnd.nextInt(options.size()));
    }
}
