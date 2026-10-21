package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/** What reaching a level unlocks. */
public class LevelDef {
    public int level;
    public List<UnlockDef> unlocks = new ArrayList<>();
}
