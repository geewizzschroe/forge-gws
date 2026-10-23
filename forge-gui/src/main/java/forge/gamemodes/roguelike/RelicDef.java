package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * A relic, loaded from {@code relics/*.json}.
 *
 * {@code script} holds card-script lines (A:, T:, S:, SVar:) that are added to the player's Base for
 * each match. SVar names must be unique across all relics; use the relic id as a prefix. Relics can
 * also change the run itself; {@code maxLifeBonus} is the first such effect.
 */
public class RelicDef {
    public String id;
    public String name;
    public String description;
    public List<String> script = new ArrayList<>();
    public int maxLifeBonus;
    /** Not offered until unlocked at an account level. */
    public boolean locked;
    /** Only an event gives it: never in shops, boss rewards or random relic events. */
    public boolean eventOnly;
    /** A rare relic: in boss relic choices like any other, but only rarely in shops and random relic events. */
    public boolean rare;
}
