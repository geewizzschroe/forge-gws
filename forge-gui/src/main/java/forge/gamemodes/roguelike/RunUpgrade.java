package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.game.CardUpgrade;

/**
 * A saved card upgrade: power/toughness and keywords, and for rare upgrades also a cost change and added abilities
 * (card-script lines). Turned into an engine {@link CardUpgrade} at match start.
 */
public class RunUpgrade {
    public int power;
    public int toughness;
    public List<String> keywords = new ArrayList<>();
    /** Change to the generic part of the mana cost (negative is cheaper). */
    public int costChange;
    /** Card-script lines (T:, S:, K:, SVar:) the card gains. */
    public List<String> abilities = new ArrayList<>();
    /** Shown instead of the generated description, for upgrades with abilities. */
    public String name;

    public RunUpgrade() {
    }

    public RunUpgrade(int power, int toughness, List<String> keywords) {
        this.power = power;
        this.toughness = toughness;
        this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
    }

    public CardUpgrade toCardUpgrade() {
        return new CardUpgrade(power, toughness, keywords, costChange, abilities);
    }

    /** Short label such as "+1/+1, Haste". */
    public String describe() {
        if (name != null) {
            return name;
        }
        List<String> parts = new ArrayList<>();
        if (power != 0 || toughness != 0) {
            parts.add((power >= 0 ? "+" : "") + power + "/" + (toughness >= 0 ? "+" : "") + toughness);
        }
        for (String k : keywords) {
            parts.add(k.replace(':', ' ')); // engine spelling "Ward:1" reads as "Ward 1"
        }
        return String.join(", ", parts);
    }

    @Override
    public String toString() {
        return describe();
    }
}
