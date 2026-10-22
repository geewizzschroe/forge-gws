package forge.gamemodes.roguelike;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import forge.StaticData;
import forge.card.CardEdition;
import forge.card.CardRules;
import forge.item.PaperCard;

/**
 * The Full Pool: every nonland card of a run's Full Pool sets, not curated (DESIGN.md 2.5). Reward slots that
 * draw from it, and set buckets, only take cards within the run's colours or colourless.
 */
public final class FullPool {
    private static final Map<String, List<String>> SET_CARDS = new HashMap<>();

    private FullPool() {
    }

    /** The nonland cards of a set, each name once. Digital-only rebalanced versions ("A-") are left out. */
    public static synchronized List<String> setCards(final String code) {
        return SET_CARDS.computeIfAbsent(code, k -> {
            CardEdition edition = StaticData.instance().getEditions().get(k);
            if (edition == null) {
                throw new IllegalArgumentException("No set with code " + k);
            }
            Set<String> out = new LinkedHashSet<>();
            for (CardEdition.EditionEntry e : edition.getAllCardsInSet()) {
                if (e.name().startsWith("A-")) {
                    continue;
                }
                PaperCard pc = StaticData.instance().getCommonCards().getCard(e.name());
                if (pc != null && !pc.getRules().getMainPart().getType().isLand()) {
                    out.add(e.name());
                }
            }
            return new ArrayList<>(out);
        });
    }

    /** The cards of the given sets whose colour identity is within {@code colours} (colourless cards always are). */
    public static List<String> cards(final List<String> sets, final byte colours) {
        Set<String> out = new LinkedHashSet<>();
        for (String set : sets) {
            for (String name : setCards(set)) {
                CardRules rules = StaticData.instance().getCommonCards().getCard(name).getRules();
                if ((rules.getColorIdentity().getColor() & ~colours) == 0) {
                    out.add(name);
                }
            }
        }
        return new ArrayList<>(out);
    }
}
