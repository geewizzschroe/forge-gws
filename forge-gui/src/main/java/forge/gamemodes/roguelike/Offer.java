package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/** An open choice the player has to resolve before moving on. Saved with the run, so reloading can't reroll it. */
public class Offer {
    public OfferKind kind;
    public List<OfferOption> options = new ArrayList<>();
    public boolean canSkip;

    public Offer() {
    }

    public Offer(final OfferKind kind, final boolean canSkip) {
        this.kind = kind;
        this.canSkip = canSkip;
    }
}
