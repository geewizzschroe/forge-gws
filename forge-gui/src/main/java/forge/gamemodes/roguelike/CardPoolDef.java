package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/**
 * A named list of card names, loaded from {@code pools/*.json}. Used both for card rewards and for
 * themed bucket rewards ("Goblins!", "Big Sorceries").
 */
public class CardPoolDef {
    public String id;
    public String name;
    /** True for a themed bucket that can be offered whole as a reward. */
    public boolean bucket;
    public List<String> cards = new ArrayList<>();
}
