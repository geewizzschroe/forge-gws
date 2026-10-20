package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/** One node on the run map. */
public class MapNode {
    public int id;
    public int row;
    public int column;
    public NodeType type;
    /** The enemy fought here, for Enemy and Boss nodes. */
    public String encounterId;
    /** Ids of the nodes reachable from this one. */
    public List<Integer> next = new ArrayList<>();

    @Override
    public String toString() {
        return type + "#" + id + "(r" + row + "c" + column + (encounterId == null ? "" : " " + encounterId) + ")";
    }
}
