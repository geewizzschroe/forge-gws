package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/** An open choice the player has to resolve before moving on. Saved with the run, so reloading can't reroll it. */
public class Offer {
    public OfferKind kind;
    public List<OfferOption> options = new ArrayList<>();
    public boolean canSkip;
    /** For a store's card removal or upgrade service: the store to return to once paid for, or backed out of. */
    public Offer returnToShop;

    public Offer() {
    }

    public Offer(final OfferKind kind, final boolean canSkip) {
        this.kind = kind;
        this.canSkip = canSkip;
    }
}
