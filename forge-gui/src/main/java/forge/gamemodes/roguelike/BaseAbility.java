package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * Card-script lines added to the player's Base for a run, from a meta-progression unlock (like a relic,
 * but earned by levelling). SVar names must be unique across everything on the Base.
 */
public class BaseAbility {
    public String name;
    public String description;
    public List<String> script = new ArrayList<>();

    public BaseAbility() {
    }

    public BaseAbility(final String name, final String description, final List<String> script) {
        this.name = name;
        this.description = description;
        this.script = new ArrayList<>(script);
    }
}
