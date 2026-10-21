package forge.gamemodes.roguelike.sim;

import java.util.List;

import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Run;

/** How a simulated player makes the run's decisions (the matches themselves are played by the AI). */
public interface RunPolicy {
    /** Which reachable node to enter. */
    MapNode chooseNode(Run run, List<MapNode> available);

    /** Resolve the open offer with choose, buy or skip; it must leave no offer open (a shop may buy first). */
    void resolveOffer(Run run);
}
