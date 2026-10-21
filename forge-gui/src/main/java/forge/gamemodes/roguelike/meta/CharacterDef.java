package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/** A playable character, loaded from {@code characters/*.json}. */
public class CharacterDef {
    public String id;
    public String name;
    public String commander;
    /** Starter ids available from level 1. */
    public List<String> starters = new ArrayList<>();
    public List<LevelDef> levels = new ArrayList<>();
    public List<MilestoneDef> milestones = new ArrayList<>();
}
