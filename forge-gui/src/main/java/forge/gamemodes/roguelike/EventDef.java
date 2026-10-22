package forge.gamemodes.roguelike;

/**
 * An event, loaded from {@code events/*.json}: a short scene at an Event node, picked at random (by
 * {@code weight}) among the events the run qualifies for. The {@code kind} says what it offers; the
 * other numbers are its settings. Kinds:
 * <ul>
 * <li>{@code gold}: take {@code amount} gold (or don't).</li>
 * <li>{@code upgrade}: pay {@code price} gold for one of the Upgrade offer's choices, or walk away.</li>
 * <li>{@code card}: take one of three single cards (2 copies each), or walk away.</li>
 * <li>{@code relicForLife}: pay {@code lifeCost} life for a random relic, or walk away. Needs a relic left to give
 * and more life than the cost.</li>
 * <li>{@code sellRetry}: give up a retry for the relic {@code relic}, or walk away. Needs a retry, and that relic
 * not yet owned.</li>
 * </ul>
 */
public class EventDef {
    public String id;
    public String title;
    public String text;
    public String kind;
    public int amount;
    public int price;
    public int lifeCost;
    public String relic;
    /** What taking the event's offer is called (for one-option events). */
    public String accept;
    public double weight = 1;
}
