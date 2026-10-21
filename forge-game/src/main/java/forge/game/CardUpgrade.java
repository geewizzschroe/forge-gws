package forge.game;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import forge.game.card.Card;
import forge.game.card.perpetual.PerpetualInterface;
import forge.game.card.perpetual.PerpetualKeywords;
import forge.game.card.perpetual.PerpetualPTBoost;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * A lasting change to every card of one name a player owns, applied when the game starts.
 *
 * Upgrades are additive: several for the same name all apply. They become perpetual effects, so
 * they stay with the card through zone changes. Keywords follow the normal rules, so a second
 * Haste does nothing while a second Ward or Flanking triggers again.
 */
public record CardUpgrade(int power, int toughness, List<String> keywords) {

    public CardUpgrade {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }

    public static CardUpgrade pt(int power, int toughness) {
        return new CardUpgrade(power, toughness, List.of());
    }

    public static CardUpgrade keywords(String... keywords) {
        return new CardUpgrade(0, 0, Arrays.asList(keywords));
    }

    private static final List<ZoneType> ZONES = List.of(
            ZoneType.Library, ZoneType.Sideboard, ZoneType.Command, ZoneType.Battlefield, ZoneType.Hand);

    /** Apply the upgrades for each card name to all matching cards the player owns. */
    public static void applyAll(final Player player, final Map<String, List<CardUpgrade>> byName) {
        if (byName == null || byName.isEmpty()) {
            return;
        }
        for (Card c : player.getCardsIn(ZONES)) {
            if (c.isImmutable() || !c.getOwner().equals(player)) {
                continue;
            }
            List<CardUpgrade> upgrades = byName.get(c.getName());
            if (upgrades == null) {
                continue;
            }
            for (CardUpgrade u : upgrades) {
                u.applyTo(c);
            }
        }
    }

    public void applyTo(final Card c) {
        applyTo(c, c.getGame().getNextTimestamp());
    }

    /** Apply with a given timestamp, for cards outside a game (previews). */
    public void applyTo(final Card c, final long timestamp) {
        if (power != 0 || toughness != 0) {
            add(c, new PerpetualPTBoost(timestamp, power, toughness));
        }
        if (!keywords.isEmpty()) {
            add(c, new PerpetualKeywords(timestamp, Lists.newArrayList(keywords), Lists.newArrayList(), false));
        }
        c.updatePTforView();
    }

    private static void add(final Card c, final PerpetualInterface p) {
        c.addPerpetual(p);
        p.applyEffect(c);
    }
}
