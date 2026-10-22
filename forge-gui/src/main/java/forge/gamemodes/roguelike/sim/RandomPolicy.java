package forge.gamemodes.roguelike.sim;

import java.util.List;
import java.util.Random;

import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Offer;
import forge.gamemodes.roguelike.OfferKind;
import forge.gamemodes.roguelike.OfferOption;
import forge.gamemodes.roguelike.Run;

/**
 * A simple baseline player: random nodes, a random reward or upgrade, never cuts, and in a shop buys
 * relics first, then cards, while it can afford them.
 */
public class RandomPolicy implements RunPolicy {
    private final Random rnd;

    public RandomPolicy(final long seed) {
        rnd = new Random(seed);
    }

    @Override
    public MapNode chooseNode(final Run run, final List<MapNode> available) {
        return available.get(rnd.nextInt(available.size()));
    }

    @Override
    public void resolveOffer(final Run run) {
        Offer offer = run.getState().offer;
        if (offer.kind.isStore()) {
            boolean bought = true;
            boolean suppliesBought = false;
            while (bought) {
                bought = false;
                for (boolean relicsFirst : new boolean[] {true, false}) {
                    for (int i = 0; i < offer.options.size() && !bought; i++) {
                        OfferOption o = offer.options.get(i);
                        // never the removal or upgrade services (they open a second choice); supplies only once a visit
                        if (!o.bought && !o.removal && !o.upgradeService && !(o.supply > 0 && suppliesBought)
                                && o.price <= run.getState().gold && (o.relicId != null) == relicsFirst) {
                            run.buy(i);
                            suppliesBought |= o.supply > 0;
                            bought = true;
                        }
                    }
                }
            }
            run.skip();
        } else if (offer.kind == OfferKind.CUT || offer.options.isEmpty()) {
            run.skip();
        } else {
            run.choose(rnd.nextInt(offer.options.size()));
        }
    }
}
