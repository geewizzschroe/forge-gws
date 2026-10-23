package forge.game;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.Lists;

import forge.game.card.Card;
import forge.game.card.ICardTraitChanges;
import forge.game.card.perpetual.PerpetualAbilities;
import forge.game.card.perpetual.PerpetualInterface;
import forge.game.card.perpetual.PerpetualKeywords;
import forge.game.card.perpetual.PerpetualManaCost;
import forge.game.card.perpetual.PerpetualPTBoost;
import forge.game.card.perpetual.PerpetualSVars;
import forge.game.mana.ManaCostBeingPaid;
import forge.game.staticability.StaticAbility;
import forge.game.trigger.Trigger;
import forge.game.trigger.TriggerHandler;
import forge.game.player.Player;
import forge.game.zone.ZoneType;

/**
 * A lasting change to every card of one name a player owns, applied when the game starts.
 *
 * Upgrades are additive: several for the same name all apply. They become perpetual effects, so
 * they stay with the card through zone changes. Keywords follow the normal rules, so a second
 * Haste does nothing while a second Ward or Flanking triggers again.
 * <p>
 * Besides power/toughness and keywords, an upgrade can change the generic part of the mana cost
 * ({@code costChange}, negative is cheaper, never below no generic mana) and add abilities written as
 * card-script lines ({@code T:}, {@code S:}, {@code K:} and the {@code SVar:} lines they use).
 */
public record CardUpgrade(int power, int toughness, List<String> keywords, int costChange, List<String> abilities) {

    public CardUpgrade {
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
        abilities = abilities == null ? List.of() : List.copyOf(abilities);
    }

    public CardUpgrade(int power, int toughness, List<String> keywords) {
        this(power, toughness, keywords, 0, List.of());
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
        List<String> allKeywords = Lists.newArrayList(keywords);
        Map<String, String> svars = new LinkedHashMap<>();
        List<Trigger> triggers = Lists.newArrayList();
        List<String> statics = Lists.newArrayList();
        for (String line : abilities) {
            if (line.startsWith("SVar:")) {
                String rest = line.substring(5);
                int colon = rest.indexOf(':');
                svars.put(rest.substring(0, colon), rest.substring(colon + 1));
            } else if (line.startsWith("K:")) {
                allKeywords.add(line.substring(2));
            }
        }
        if (!svars.isEmpty()) { // before the abilities that use them
            add(c, new PerpetualSVars(timestamp, svars));
        }
        for (String line : abilities) {
            if (line.startsWith("T:")) {
                triggers.add(TriggerHandler.parseTrigger(line.substring(2), c, false));
            } else if (line.startsWith("S:")) {
                statics.add(line.substring(2));
            }
        }
        if (!allKeywords.isEmpty()) {
            add(c, new PerpetualKeywords(timestamp, allKeywords, Lists.newArrayList(), false));
        }
        if (!triggers.isEmpty() || !statics.isEmpty()) {
            List<StaticAbility> stAbs = Lists.newArrayList();
            for (String st : statics) {
                stAbs.add(StaticAbility.create(st, c, c.getCurrentState(), false));
            }
            ICardTraitChanges changes = c.addChangedCardTraits(List.of(), triggers, List.of(), stAbs, null, timestamp, 0);
            c.addPerpetual(new PerpetualAbilities(timestamp, changes));
        }
        if (costChange != 0 && !c.getManaCost().isNoCost()) {
            ManaCostBeingPaid cost = new ManaCostBeingPaid(c.getManaCost());
            if (costChange < 0) {
                cost.decreaseGenericMana(Math.min(-costChange, c.getManaCost().getGenericCost()));
            } else {
                cost.increaseGenericMana(costChange);
            }
            add(c, new PerpetualManaCost(timestamp, cost.toManaCost()));
        }
        c.updatePTforView();
    }

    private static void add(final Card c, final PerpetualInterface p) {
        c.addPerpetual(p);
        p.applyEffect(c);
    }
}
