package forge.game;

import java.util.List;
import java.util.Map;

import forge.StaticData;
import forge.card.MagicColor;
import forge.deck.Deck;
import forge.deck.DeckSection;
import forge.game.ability.AbilityKey;
import forge.game.card.Card;
import forge.game.card.CardCollection;
import forge.game.card.CardZoneTable;
import forge.game.player.Player;
import forge.game.trigger.TriggerType;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

/**
 * Landless play rules (see {@link GameRules#isConjureBasicLandEachTurn()} and
 * {@link GameRules#getMaxLandsPerPlayer()}).
 *
 * At the beginning of each player's first precombat main phase, if they control fewer lands than
 * the cap, they choose a basic land and it is conjured onto the battlefield under their control.
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

    /** The turn-based action: conjure the player's chosen basic land if they are under the cap. */
    public static Card conjureChosenBasicLand(final Player player) {
        if (isAtLandCap(player)) {
            return null;
        }
        String name = player.getController().chooseBasicLandToConjure(MagicColor.Constant.BASIC_LANDS);
        if (name == null || !MagicColor.Constant.BASIC_LANDS.contains(name)) {
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
