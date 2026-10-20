package forge.ai;

import java.util.List;

import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.game.card.Card;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * AI choice of which basic land to conjure under the landless-play rule.
 *
 * Scores each basic by the coloured mana symbols the AI still needs (hand and commanders count
 * double, library single) divided by one plus the lands of that type it already controls, so it
 * keeps adding the colour its remaining cards ask for most while still splashing.
 */
public final class AiBasicLandChoice {

    private AiBasicLandChoice() {
    }

    public static String choose(final Player ai, final List<String> options) {
        final List<String> basics = MagicColor.Constant.BASIC_LANDS; // WUBRG order
        double[] need = new double[5];
        addNeed(need, ai.getCardsIn(ZoneType.Hand), 2.0);
        addNeed(need, ai.getCommanders(), 2.0);
        addNeed(need, ai.getCardsIn(ZoneType.Library), 1.0);

        int[] have = new int[5];
        for (Card c : ai.getCardsIn(ZoneType.Battlefield)) {
            if (!c.isLand()) {
                continue;
            }
            for (int i = 0; i < 5; i++) {
                if (c.getType().hasSubtype(basics.get(i))) {
                    have[i]++;
                }
            }
        }

        int best = -1;
        double bestScore = 0;
        for (int i = 0; i < 5; i++) {
            if (!options.contains(basics.get(i))) {
                continue;
            }
            double score = need[i] / (1 + have[i]);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        if (best >= 0) {
            return basics.get(best);
        }

        // nothing coloured left to cast: lean on the commander's colours, else take the first option
        ColorSet identity = ai.getCommanderColorID();
        for (int i = 0; i < 5 && identity != null; i++) {
            if (identity.hasAnyColor(MagicColor.WUBRG[i]) && options.contains(basics.get(i))) {
                return basics.get(i);
            }
        }
        return options.isEmpty() ? null : options.get(0);
    }

    private static void addNeed(final double[] need, final Iterable<Card> cards, final double weight) {
        for (Card c : cards) {
            int[] counts = c.getManaCost().getColorShardCounts();
            for (int i = 0; i < 5; i++) {
                need[i] += counts[i] * weight;
            }
        }
    }
}
