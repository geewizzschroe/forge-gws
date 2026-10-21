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
    /**
     * Cards in every one of this character's starters (2 copies each), so upgrades to them carry over
     * between decks. A starter marked {@code independentOfCore} instead shares none of them.
     */
    public List<String> core = new ArrayList<>();
    public List<LevelDef> levels = new ArrayList<>();
    public List<MilestoneDef> milestones = new ArrayList<>();
}
