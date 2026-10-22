package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * A board-style map built from the {@code mapLayout} setting, bottom to top (proposal 1.1). Each entry is:
 * <ul>
 * <li>{@code FIGHTS}: a row of {@code mapLanes} Enemy nodes (the start, when first);</li>
 * <li>{@code STRETCH n}: n rows of {@code mapLanes} nodes of rolled types (by the node weights), where every node
 * leads straight up and to one diagonal neighbour, so every step inside a stretch is a choice;</li>
 * <li>a node type ({@code UPGRADE}, {@code SHOP}, {@code REST}, ...): a single touchstone node every path goes
 * through;</li>
 * <li>{@code BOSS}: the boss, last.</li>
 * </ul>
 * A row of lanes leads to every node of a single-node row, and a single node leads to every lane above it.
 * <p>
 * An entry may end with a tier range for its fights, such as {@code T1} or {@code T2-3} (proposal 1.3): fights
 * there draw enemies of those tiers (the nearest tiers if none fit). Without one, any tier fits. On any one path
 * an enemy appears only once while another fitting enemy is left.
 */
public class RunMap {
    public List<MapNode> nodes = new ArrayList<>();
    public List<Integer> startIds = new ArrayList<>();
    public int bossId;

    public MapNode get(final int id) {
        return nodes.get(id);
    }

    public static RunMap generate(final RunConfig cfg, final RoguelikeContent content, final Random rnd) {
        final int lanes = Math.max(1, cfg.mapLanes);
        RunMap map = new RunMap();
        List<MapNode> below = null;
        int row = 0;
        Map<Integer, int[]> tiers = new HashMap<>();
        for (String entry : cfg.mapLayout) {
            List<String> words = new ArrayList<>(List.of(entry.trim().toUpperCase(Locale.ROOT).split("\\s+")));
            int[] tier = {1, 3};
            String last = words.get(words.size() - 1);
            if (words.size() > 1 && last.matches("T\\d(-\\d)?")) {
                words.remove(words.size() - 1);
                tier[0] = last.charAt(1) - '0';
                tier[1] = last.length() > 2 ? last.charAt(3) - '0' : tier[0];
            }
            String[] parts = words.toArray(new String[0]);
            List<List<MapNode>> rows = new ArrayList<>();
            switch (parts[0]) {
                case "FIGHTS" -> rows.add(laneRow(map, row, lanes, rnd, cfg, true));
                case "STRETCH" -> {
                    int n = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                    for (int i = 0; i < n; i++) {
                        rows.add(laneRow(map, row + i, lanes, rnd, cfg, false));
                    }
                }
                default -> {
                    MapNode touchstone = add(map, row, (lanes - 1) / 2, NodeType.valueOf(parts[0]));
                    rows.add(List.of(touchstone));
                }
            }
            for (List<MapNode> current : rows) {
                tiers.put(row, tier);
                if (below == null) {
                    for (MapNode n : current) {
                        map.startIds.add(n.id);
                    }
                } else {
                    link(below, current, rnd);
                }
                below = current;
                row++;
            }
        }
        if (below == null || below.size() != 1 || below.get(0).type != NodeType.BOSS) {
            throw new IllegalStateException("mapLayout must end with BOSS: " + cfg.mapLayout);
        }
        map.bossId = below.get(0).id;

        // ids grow with the rows, so every node's parents are done before it
        Map<Integer, Set<String>> before = new HashMap<>();
        for (MapNode n : map.nodes) {
            Set<String> seen = before.getOrDefault(n.id, Set.of());
            if (n.type.isMatch()) {
                n.encounterId = pickEncounter(content, n, tiers.get(n.row), seen, rnd);
            }
            for (int next : n.next) {
                Set<String> s = before.computeIfAbsent(next, k -> new HashSet<>());
                s.addAll(seen);
                if (n.encounterId != null) {
                    s.add(n.encounterId);
                }
            }
        }
        return map;
    }

    private static MapNode add(final RunMap map, final int row, final int column, final NodeType type) {
        MapNode node = new MapNode();
        node.id = map.nodes.size();
        node.row = row;
        node.column = column;
        node.type = type;
        map.nodes.add(node);
        return node;
    }

    /** A row of lanes: all fights, or rolled types where a non-fight type already in the row is rerolled once. */
    private static List<MapNode> laneRow(final RunMap map, final int row, final int lanes, final Random rnd,
                                         final RunConfig cfg, final boolean fights) {
        List<MapNode> out = new ArrayList<>();
        List<NodeType> types = new ArrayList<>();
        for (int c = 0; c < lanes; c++) {
            NodeType t = fights ? NodeType.ENEMY : pickType(cfg, rnd);
            if (t != NodeType.ENEMY && types.contains(t)) {
                t = pickType(cfg, rnd);
            }
            types.add(t);
            out.add(add(map, row, c, t));
        }
        return out;
    }

    private static void link(final List<MapNode> below, final List<MapNode> above, final Random rnd) {
        if (below.size() == 1 || above.size() == 1) {
            for (MapNode b : below) {
                for (MapNode a : above) {
                    b.next.add(a.id);
                }
            }
            return;
        }
        boolean previousWentRight = false; // paths don't cross: after a step up-right, the next lane can't step up-left
        for (int c = 0; c < below.size(); c++) {
            MapNode b = below.get(c);
            int straight = Math.min(c, above.size() - 1);
            b.next.add(above.get(straight).id);
            int d = rnd.nextBoolean() ? 1 : -1;
            if (straight + d < 0 || straight + d >= above.size() || (d < 0 && previousWentRight)) {
                d = -d;
            }
            boolean fits = straight + d >= 0 && straight + d < above.size() && !(d < 0 && previousWentRight);
            if (fits) {
                b.next.add(above.get(straight + d).id);
            }
            previousWentRight = fits && d > 0;
        }
        // no node in the upper row is left unreachable
        for (int c = 0; c < above.size(); c++) {
            int id = above.get(c).id;
            if (below.stream().noneMatch(b -> b.next.contains(id))) {
                below.get(Math.min(c, below.size() - 1)).next.add(id);
            }
        }
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

    /**
     * A boss for the boss node; otherwise an enemy whose tier is in the range, widening the range a step at a time
     * if none is, and one not yet met on the way here if any fits.
     */
    private static String pickEncounter(final RoguelikeContent content, final MapNode node, final int[] tier,
                                        final Set<String> seen, final Random rnd) {
        boolean boss = node.type == NodeType.BOSS;
        List<String> options = new ArrayList<>();
        for (int widen = 0; options.isEmpty() && widen <= 3; widen++) {
            for (EncounterDef e : content.getEncounters().values()) {
                if (e.boss == boss && (boss || (e.tier >= tier[0] - widen && e.tier <= tier[1] + widen))) {
                    options.add(e.id);
                }
            }
        }
        if (options.isEmpty()) {
            throw new IllegalStateException("No " + (boss ? "boss" : "enemy") + " encounter for row " + node.row);
        }
        List<String> fresh = new ArrayList<>(options);
        fresh.removeAll(seen);
        List<String> from = fresh.isEmpty() ? options : fresh;
        return from.get(rnd.nextInt(from.size()));
    }
}
