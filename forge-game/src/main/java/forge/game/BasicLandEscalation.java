package forge.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.card.CardRules;
import forge.card.ICardFace;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.ability.AbilityFactory;
import forge.game.ability.AbilityKey;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

/**
 * Landless play rules (see {@link GameRules#isConjureBasicLandEachTurn()} and
 * {@link GameRules#getMaxLandsPerPlayer()}).
 *
 * At the beginning of each player's first precombat main phase, if they control fewer lands than
 * the cap, they choose a basic land and it is conjured onto the battlefield under their control.
 * Only the basics of the player's deck colours are offered, and with a single colour the land is
 * conjured without asking (see {@link #basicLandOptions(Player)}).
 * Separately, a land can never enter the battlefield under the control of a player already at the cap.
 */
public final class BasicLandEscalation {

    private BasicLandEscalation() {
    }

    /**
     * A copy of the deck with every card whose front face is a land removed from the main deck and
     * sideboard. Cards that are a spell on the front and a land on the back are kept.
     */
    public static Deck withoutLands(final Deck deck) {
        Deck result = new Deck(deck);
        for (DeckSection section : List.of(DeckSection.Main, DeckSection.Sideboard)) {
            if (result.has(section)) {
                result.get(section).removeIf(pc -> pc.getRules().getMainPart().getType().isLand());
            }
        }
        return result;
    }

    /**
     * Create Treasure tokens for conjured lands the land cap kept out (a ramp effect at the cap).
     * Uses the normal token ability, so token replacement effects and triggers apply.
     */
    public static void makeTreasures(final Player player, final SpellAbility cause, final int amount, final boolean tapped) {
        if (amount <= 0) {
            return;
        }
        SpellAbility token = AbilityFactory.getAbility("DB$ Token | TokenScript$ c_a_treasure_sac | TokenAmount$ " + amount
                + (tapped ? " | TokenTapped$ True" : ""), cause.getHostCard());
        token.setActivatingPlayer(player);
        AbilityUtils.resolve(token);
    }

    public static int countLands(final Player player) {
        int lands = 0;
        for (Card c : player.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand()) {
                lands++;
            }
        }
        return lands;
    }

    /** True if the game has a land cap and the player already controls that many lands. */
    public static boolean isAtLandCap(final Player player) {
        int cap = player.getGame().getRules().getMaxLandsPerPlayer();
        return cap > 0 && countLands(player) >= cap;
    }

    /** True if this card entering the battlefield would take its controller past the land cap. */
    public static boolean blocksLandEntering(final Card entering) {
        if (!entering.isLand()) {
            return false;
        }
        Player controller = entering.getController();
        return controller != null && isAtLandCap(controller);
    }

    /**
     * The lands to offer a player: one basic for each colour in the colour identity of the nonland, nontoken
     * cards they own in this game (their deck and commander), all five if that has no colour; then any of the
     * rules' extra conjurable lands whose colours are all among the deck's.
     */
    public static List<String> basicLandOptions(final Player player) {
        byte identity = 0;
        for (Card c : player.getGame().getCardsInGame()) {
            if (c.isToken() || c.isLand() || !player.equals(c.getOwner())) {
                continue;
            }
            CardRules rules = c.getRules();
            if (rules != null) {
                identity |= rules.getColorIdentity().getColor();
            }
        }
        List<String> options = new ArrayList<>();
        for (int i = 0; i < MagicColor.WUBRG.length; i++) {
            if ((identity & MagicColor.WUBRG[i]) != 0) {
                options.add(MagicColor.Constant.BASIC_LANDS.get(i));
            }
        }
        if (options.isEmpty()) {
            return MagicColor.Constant.BASIC_LANDS;
        }
        for (String name : player.getGame().getRules().getExtraConjurableLands()) {
            PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
            byte colours = pc == null ? 0 : pc.getRules().getColorIdentity().getColor();
            if (colours != 0 && (colours & ~identity) == 0) {
                options.add(name);
            }
        }
        return options;
    }

    /**
     * For a choice made only of basic lands and the rules' extra conjurable lands (a landless ramp card): just
     * the ones in {@link #basicLandOptions(Player)}. Any other choice, or one with none of those, is returned
     * unchanged.
     */
    public static List<ICardFace> limitBasicLandChoices(final Player player, final List<ICardFace> faces) {
        List<String> allowed = basicLandOptions(player);
        List<String> extras = player.getGame().getRules().getExtraConjurableLands();
        List<ICardFace> kept = new ArrayList<>();
        for (ICardFace face : faces) {
            if (!MagicColor.Constant.BASIC_LANDS.contains(face.getName()) && !extras.contains(face.getName())) {
                return faces;
            }
            if (allowed.contains(face.getName())) {
                kept.add(face);
            }
        }
        return kept.isEmpty() ? faces : kept;
    }

    /** The turn-based action: conjure the player's chosen basic land if they are under the cap. */
    public static Card conjureChosenBasicLand(final Player player) {
        if (isAtLandCap(player)) {
            return null;
        }
        List<String> options = basicLandOptions(player);
        String name = options.size() == 1 ? options.get(0) : player.getController().chooseBasicLandToConjure(options);
        if (name == null || !options.contains(name)) {
            return null;
        }
        PaperCard pc = StaticData.instance().getCommonCards().getCard(name);
        if (pc == null) {
            return null;
        }
        Game game = player.getGame();
        Card card = Card.fromPaperCard(pc, player);
        Map<AbilityKey, Object> moveParams = AbilityKey.newMap();
        game.getAction().moveTo(ZoneType.None, card, null, moveParams);
        Card made = game.getAction().moveTo(ZoneType.Battlefield, card, null, moveParams);
        if (made == null || !made.isInPlay()) {
            return null;
        }

        CardZoneTable table = new CardZoneTable();
        table.put(ZoneType.None, ZoneType.Battlefield, made);
        table.triggerChangesZoneAll(game, null);

        final Map<AbilityKey, Object> runParams = AbilityKey.mapFromPlayer(player);
        runParams.put(AbilityKey.Cards, new CardCollection(made));
        game.getTriggerHandler().runTrigger(TriggerType.ConjureAll, runParams, false);
        return made;
    }
}
