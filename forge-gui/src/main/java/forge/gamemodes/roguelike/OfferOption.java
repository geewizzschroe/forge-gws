package forge.gamemodes.roguelike;

import java.util.LinkedHashMap;
import java.util.Map;

/** One option of an {@link Offer}. Only the fields that apply to its kind are set. */
public class OfferOption {
    /** Display name, for example a bucket's theme. */
    public String label;
    /** Cards added to the deck (name to copies). */
    public Map<String, Integer> cards = new LinkedHashMap<>();
    /** A card name and the upgrade it receives (Upgrade node, or attached to a card reward). */
    public String upgradeCard;
    public RunUpgrade upgrade;
    public String relicId;
    /** A card name to remove (Cut node). */
    public String cutCard;
    /** Shop price; 0 elsewhere. */
    public int price;
    public boolean bought;
    /** The shop's card removal service: buying it opens a choice of a card to cut every copy of. */
    public boolean removal;
    /** The Workshop's upgrade service: buying it opens a choice of upgrades, paid for when one is taken. Repeatable. */
    public boolean upgradeService;
    /** The Workshop's Base supplies: adds this many SUPPLY counters to the run's Base. Repeatable. */
    public int supply;
    /** Where a single-card reward came from: "curated" or "full pool" (shown as a debug tag). */
    public String source;

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (label != null) {
            sb.append(label).append(": ");
        }
        if (!cards.isEmpty()) {
            sb.append(cards);
        }
        if (upgrade != null) {
            sb.append(" [").append(upgradeCard).append(" ").append(upgrade.describe()).append("]");
        }
        if (relicId != null) {
            sb.append("relic ").append(relicId);
        }
        if (cutCard != null) {
            sb.append("cut ").append(cutCard);
        }
        if (price > 0) {
            sb.append(" (").append(price).append(" gold").append(bought ? ", bought" : "").append(")");
        }
        return sb.toString().trim();
    }
}
