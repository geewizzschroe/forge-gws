package forge.gamemodes.roguelike;

/** Node types named in DESIGN.md 2.1. */
public enum NodeType {
    // CUT and UPGRADE no longer appear on new maps (2026-10-22); kept so older saved runs still load.
    ENEMY, BOSS, SHOP, REST, CUT, UPGRADE, WORKSHOP, EVENT;

    public boolean isMatch() {
        return this == ENEMY || this == BOSS;
    }
}
