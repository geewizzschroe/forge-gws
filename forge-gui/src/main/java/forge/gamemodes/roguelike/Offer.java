package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.List;

/** An open choice the player has to resolve before moving on. Saved with the run, so reloading can't reroll it. */
public class Offer {
    public OfferKind kind;
    public List<OfferOption> options = new ArrayList<>();
    public boolean canSkip;
    /** For an event: its title and scene text. */
    public String title;
    public String text;
    /**
     * For a second choice opened from a store (card removal, upgrade service) or a campfire (train, purge): the
     * offer to go back to when backing out, and, for a store, once paid for.
     */
    public Offer returnToShop;

    public Offer() {
    }

    public Offer(final OfferKind kind, final boolean canSkip) {
        this.kind = kind;
        this.canSkip = canSkip;
    }
}
