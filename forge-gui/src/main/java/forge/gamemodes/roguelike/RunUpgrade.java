package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

import forge.game.CardUpgrade;

/** A saved card upgrade (power/toughness and keywords). Turned into an engine {@link CardUpgrade} at match start. */
public class RunUpgrade {
    public int power;
    public int toughness;
    public List<String> keywords = new ArrayList<>();

    public RunUpgrade() {
    }

    public RunUpgrade(int power, int toughness, List<String> keywords) {
        this.power = power;
        this.toughness = toughness;
        this.keywords = keywords == null ? new ArrayList<>() : new ArrayList<>(keywords);
    }

    public CardUpgrade toCardUpgrade() {
        return new CardUpgrade(power, toughness, keywords);
    }

    /** Short label such as "+1/+1, Haste". */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (power != 0 || toughness != 0) {
            parts.add((power >= 0 ? "+" : "") + power + "/" + (toughness >= 0 ? "+" : "") + toughness);
        }
        parts.addAll(keywords);
        return String.join(", ", parts);
    }

    @Override
    public String toString() {
        return describe();
    }
}
