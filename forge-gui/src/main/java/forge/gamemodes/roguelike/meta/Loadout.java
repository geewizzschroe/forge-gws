package forge.gamemodes.roguelike.meta;

import java.util.ArrayList;
import java.util.List;

/** What the player brings into a run: one starter, and which of their unlocks are switched off. */
public class Loadout {
    public String starterId;
    /** Keys (see {@link Progression#key}) of unlocks the player chose to leave behind. */
    public List<String> disabled = new ArrayList<>();
    /** Base file to use instead of the starter's own, or null. */
    public String base;
    /** Full Pool sets to switch off, up to the number the account has unlocked. */
    public List<String> disabledSets = new ArrayList<>();
}
