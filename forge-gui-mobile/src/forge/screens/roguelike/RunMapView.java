package forge.screens.roguelike;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntConsumer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Align;

import forge.Graphics;
import forge.assets.FSkinFont;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Run;
import forge.gamemodes.roguelike.RunMap;
import forge.gamemodes.roguelike.RunState;
import forge.toolbox.FDisplayObject;

/**
 * Draws the run map: start at the bottom, boss at the top, nodes as lettered circles joined by their
 * paths. The path taken so far is highlighted, nodes that can be entered now are ringed, and tapping a
 * node selects it.
 */
public class RunMapView extends FDisplayObject {
    private static final Color LINE = new Color(0.45f, 0.45f, 0.5f, 1f);
    private static final Color LINE_TAKEN = new Color(0.95f, 0.85f, 0.35f, 1f);
    private static final Color RING_AVAILABLE = Color.WHITE;
    private static final Color RING_SELECTED = new Color(0.3f, 0.85f, 1f, 1f);
    private static final Color DONE = new Color(0.25f, 0.25f, 0.28f, 1f);

    private final Run run;
    private final Integer selectedId;
    private final IntConsumer onSelect;

    public RunMapView(final Run run, final Integer selectedId, final IntConsumer onSelect) {
        this.run = run;
        this.selectedId = selectedId;
        this.onSelect = onSelect;
    }

    private RunMap map() {
        return run.getState().map;
    }

    private int rows() {
        return map().get(map().bossId).row + 1;
    }

    private int columns() {
        int max = 0;
        for (MapNode n : map().nodes) {
            max = Math.max(max, n.column);
        }
        return Math.max(max + 1, run.getContent().getConfig().mapLanes);
    }

    private float x(final MapNode n) {
        if (n.id == map().bossId) {
            return getWidth() / 2;
        }
        float cell = getWidth() / columns();
        return cell * (n.column + 0.5f);
    }

    /** Room above and below the rows, so the boss's rings aren't clipped. */
    private float margin() {
        return getHeight() * 0.04f;
    }

    private float y(final MapNode n) {
        float cell = (getHeight() - 2 * margin()) / rows();
        return getHeight() - margin() - cell * (n.row + 0.5f);
    }

    private float radius() {
        return Math.min(getWidth() / columns(), (getHeight() - 2 * margin()) / rows()) * 0.32f;
    }

    private static Color colour(final MapNode n) {
        return switch (n.type) {
            case ENEMY -> new Color(0.75f, 0.25f, 0.2f, 1f);
            case BOSS -> new Color(0.5f, 0.05f, 0.1f, 1f);
            case ELITE -> new Color(0.85f, 0.45f, 0.1f, 1f);
            case SHOP -> new Color(0.8f, 0.6f, 0.1f, 1f);
            case REST -> new Color(0.2f, 0.6f, 0.3f, 1f);
            case CUT -> new Color(0.45f, 0.45f, 0.45f, 1f);
            case UPGRADE -> new Color(0.2f, 0.4f, 0.8f, 1f);
            case WORKSHOP -> new Color(0.55f, 0.35f, 0.2f, 1f);
            case EVENT -> new Color(0.5f, 0.3f, 0.6f, 1f);
        };
    }

    private static String letter(final MapNode n) {
        return switch (n.type) {
            case ENEMY -> "E";
            case BOSS -> "B";
            case ELITE -> "!";
            case SHOP -> "S";
            case REST -> "R";
            case CUT -> "C";
            case UPGRADE -> "U";
            case WORKSHOP -> "W";
            case EVENT -> "?";
        };
    }

    @Override
    public void draw(final Graphics g) {
        RunState s = run.getState();
        Set<Integer> available = new HashSet<>();
        for (MapNode n : run.getAvailableNodes()) {
            available.add(n.id);
        }
        float r = radius();
        float thin = Math.max(1f, r / 10f);

        for (MapNode n : map().nodes) {
            int at = s.visited.indexOf(n.id);
            for (int next : n.next) {
                boolean taken = at >= 0 && at + 1 < s.visited.size() && s.visited.get(at + 1) == next;
                MapNode m = map().get(next);
                g.drawLine(taken ? thin * 3 : thin, taken ? LINE_TAKEN : LINE, x(n), y(n), x(m), y(m));
            }
        }

        FSkinFont font = FSkinFont.forHeight(r);
        for (MapNode n : map().nodes) {
            float nr = n.id == map().bossId ? r * 1.35f : r;
            boolean done = s.visited.contains(n.id);
            g.fillCircle(done ? DONE : colour(n), x(n), y(n), nr);
            if (done) {
                g.drawCircle(thin * 2, LINE_TAKEN, x(n), y(n), nr);
            }
            if (available.contains(n.id) || (s.activeNodeId != null && s.activeNodeId == n.id)) {
                g.drawCircle(thin * 3, RING_AVAILABLE, x(n), y(n), nr + thin * 2);
            }
            if (selectedId != null && selectedId == n.id) {
                g.drawCircle(thin * 3, RING_SELECTED, x(n), y(n), nr + thin * 5);
            }
            g.drawText(letter(n), font, Color.WHITE, x(n) - nr, y(n) - nr, nr * 2, nr * 2, false, Align.center, true);
        }
    }

    @Override
    public boolean tap(final float tx, final float ty, final int count) {
        float r = radius() * 1.5f;
        MapNode best = null;
        float bestDist = Float.MAX_VALUE;
        for (MapNode n : map().nodes) {
            float dx = x(n) - tx;
            float dy = y(n) - ty;
            float d = dx * dx + dy * dy;
            if (d < r * r && d < bestDist) {
                best = n;
                bestDist = d;
            }
        }
        if (best == null) {
            return false;
        }
        onSelect.accept(best.id);
        return true;
    }
}
