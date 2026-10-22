package forge.gamemodes.roguelike.sim;

import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.gamemodes.roguelike.MapNode;
import forge.gamemodes.roguelike.Offer;
import forge.gamemodes.roguelike.OfferKind;
import forge.gamemodes.roguelike.OfferOption;
import forge.gamemodes.roguelike.Run;
import forge.item.PaperCard;

/**
 * A slightly smarter simulated player than {@link RandomPolicy}: rests when hurt, shops when it can afford
 * something, otherwise prefers fights; takes the reward that best fits the commander's colours (by rarity,
 * then by lower mana value); buys relics first; never cuts.
 */
public class GreedyPolicy implements RunPolicy {

    @Override
    public MapNode chooseNode(final Run run, final List<MapNode> available) {
        int life = run.getState().life;
        int max = run.getState().maxLife;
        int gold = run.getState().gold;
        MapNode best = available.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (MapNode n : available) {
            int score = switch (n.type) {
                case REST -> life * 2 < max ? 100 : 5;
                case SHOP -> gold >= run.getContent().getConfig().shopCardPrice ? 60 : 0;
                case UPGRADE -> 50;
                case ENEMY, BOSS -> 40;
                case CUT -> 1;
                case WORKSHOP -> gold >= run.getContent().getConfig().workshopArtifactPrice ? 55 : 0;
                case EVENT -> 30;
            };
            if (score > bestScore) {
                best = n;
                bestScore = score;
            }
        }
        return best;
    }

    @Override
    public void resolveOffer(final Run run) {
        Offer offer = run.getState().offer;
        if (offer.kind.isStore() || offer.kind == OfferKind.EVENT) {
            new RandomPolicy(0).resolveOffer(run); // buys relics first, then cards, while affordable; events at random
            return;
        }
        if (offer.kind == OfferKind.CUT || offer.options.isEmpty()) {
            run.skip();
            return;
        }
        int best = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        ColorSet identity = commanderIdentity(run);
        for (int i = 0; i < offer.options.size(); i++) {
            double score = score(offer.options.get(i), identity);
            if (score > bestScore) {
                best = i;
                bestScore = score;
            }
        }
        run.choose(best);
    }

    private static ColorSet commanderIdentity(final Run run) {
        PaperCard cmd = StaticData.instance().getCommonCards().getCard(run.getState().commander);
        return cmd == null ? null : cmd.getRules().getColorIdentity();
    }

    private static double score(final OfferOption o, final ColorSet identity) {
        if (o.relicId != null) {
            return 10;
        }
        if (o.upgrade != null && o.cards.isEmpty()) {
            return o.upgrade.power + o.upgrade.toughness + o.upgrade.keywords.size();
        }
        double score = 0;
        for (Map.Entry<String, Integer> e : o.cards.entrySet()) {
            PaperCard pc = StaticData.instance().getCommonCards().getCard(e.getKey());
            if (pc == null) {
                continue;
            }
            ColorSet c = pc.getRules().getColorIdentity();
            boolean fits = identity == null || c.isColorless() || c.hasNoColorsExcept(identity);
            double card = fits ? 3 : 0;
            CardRarity r = pc.getRarity();
            card += r == CardRarity.MythicRare ? 3 : r == CardRarity.Rare ? 2 : r == CardRarity.Uncommon ? 1 : 0;
            card -= pc.getRules().getManaCost().getCMC() * 0.2;
            score += card * e.getValue();
        }
        if (o.upgrade != null) {
            score += 1;
        }
        return score;
    }
}
