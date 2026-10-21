package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

import forge.gamemodes.roguelike.RunUpgrade;

/**
 * One unlock. Character unlocks: {@code starter} (id), {@code maxLife} (amount), {@code gold} (amount),
 * {@code retry} (amount), {@code upgrade} (card, upgrade), {@code base} (base file), {@code baseAbility}
 * (name, description, script: card-script lines added to the player's Base). Account unlocks:
 * {@code relic} (id), {@code pool} (id of a locked card pool), {@code set} (set code, minus {@code exclude}).
 */
public class UnlockDef {
    public String kind;
    public String id;
    public int amount;
    public String card;
    public RunUpgrade upgrade;
    public String base;
    public String set;
    public List<String> exclude = new ArrayList<>();
    public String name;
    public List<String> script = new ArrayList<>();
    /** Optional text for screens; a default is written from the fields otherwise. */
    public String description;
}
