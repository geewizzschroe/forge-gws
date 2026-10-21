package forge.gamemodes.roguelike;

/** Node types named in DESIGN.md 2.1. */
public enum NodeType {
    ENEMY, BOSS, SHOP, REST, CUT, UPGRADE;

    public boolean isMatch() {
        return this == ENEMY || this == BOSS;
    }
}
