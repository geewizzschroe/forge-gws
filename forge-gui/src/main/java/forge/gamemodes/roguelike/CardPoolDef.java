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
    /** Not available until unlocked at an account level. */
    public boolean locked;
    public List<String> cards = new ArrayList<>();
    /** A set bucket: instead of {@code cards}, it offers cards of this set within the run's colours. */
    public String fromSet;
}
