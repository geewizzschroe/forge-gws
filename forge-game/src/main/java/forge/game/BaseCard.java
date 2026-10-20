package forge.game;

import java.util.List;

import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.item.PaperCard;

/**
 * A Base: one card per player that sits in the command zone for the whole game, in the shape of a
 * Vanguard avatar. Bases are not in the card database; they are built from card-script text, so a
 * game mode can assemble one per match (for example a base script plus the abilities of its relics).
 */
public final class BaseCard {

    private BaseCard() {
    }

    /** Build a Base from card-script lines (Name:, Types:, A:, T:, S:, SVar:, Oracle: ...). */
    public static PaperCard fromScript(final List<String> script) {
        CardRules rules = CardRules.fromScript(script);
        return new PaperCard(rules, CardEdition.UNKNOWN_CODE, CardRarity.Special);
    }
}
