package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.testng.annotations.Test;

import forge.ai.AITest;
import forge.card.CardRarity;
import forge.card.CardRules;
import forge.game.Game;
import forge.game.ability.AbilityUtils;
import forge.game.card.Card;
import forge.game.keyword.Keyword;
import forge.game.player.Player;
import forge.game.spellability.SpellAbility;
import forge.game.zone.ZoneType;
import forge.item.PaperCard;

/** Final Fantasy and Foundations land searches rewritten to conjure, and landcycling turned into cycling. */
public class LandlessOverridesTest extends AITest {

    private Game landlessGame(final RoguelikeContent c) {
        Game game = initAndCreateGame();
        game.getRules().setConjureBasicLandEachTurn(true);
        game.getRules().setMaxLandsPerPlayer(10);
        game.getRules().setExtraConjurableLands(c.getConfig().landPickerExtras);
        return game;
    }

    private static Card overridden(final RoguelikeContent c, final String name, final Player p, final ZoneType zone) {
        CardRules rules = null;
        for (CardRules o : c.getOverrides()) {
            if (o.getName().equals(name)) {
                rules = o;
            }
        }
        Card card = Card.fromPaperCard(new PaperCard(rules, "FIN", CardRarity.Common), p);
        p.getZone(zone).add(card);
        return card;
    }

    private static void resolve(final Card card, final Player p) {
        SpellAbility sa = card.getFirstSpellAbility();
        sa.setActivatingPlayer(p);
        AbilityUtils.resolve(sa);
    }

    private static List<Card> lands(final Player p) {
        List<Card> out = new ArrayList<>();
        for (Card c : p.getCardsIn(ZoneType.Battlefield)) {
            if (c.isLand()) {
                out.add(c);
            }
        }
        return out;
    }

    @Test
    public void reachTheHorizonConjuresTwoDifferentLandsInTheDecksColours() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Game game = landlessGame(c);
        Player p = game.getPlayers().get(1);
        addCardToZone("Mulldrifter", p, ZoneType.Library); // with Reach itself (green): blue-green
        resolve(overridden(c, "Reach the Horizon", p, ZoneType.Hand), p);
        List<Card> lands = lands(p);
        assertEquals(2, lands.size());
        assertFalse("different names", lands.get(0).getName().equals(lands.get(1).getName()));
        for (Card land : lands) {
            assertTrue(land.isTapped());
            byte colours = land.getRules() == null ? 0 : land.getRules().getColorIdentity().getColor();
            assertEquals(land.getName() + " is outside blue-green", 0, colours & ~(forge.card.MagicColor.BLUE | forge.card.MagicColor.GREEN));
        }
    }

    @Test
    public void withOneColourReachTheHorizonStopsAfterTheOnlyChoice() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Game game = landlessGame(c);
        Player p = game.getPlayers().get(1); // only Reach itself: green
        resolve(overridden(c, "Reach the Horizon", p, ZoneType.Hand), p);
        List<Card> lands = lands(p);
        assertEquals(1, lands.size());
        assertEquals("Forest", lands.get(0).getName());
    }

    @Test
    public void growFromTheAshesConjuresOneUntappedLandUnkicked() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Game game = landlessGame(c);
        Player p = game.getPlayers().get(1);
        resolve(overridden(c, "Grow from the Ashes", p, ZoneType.Hand), p);
        List<Card> lands = lands(p);
        assertEquals(1, lands.size());
        assertEquals("Forest", lands.get(0).getName());
        assertFalse(lands.get(0).isTapped());
    }

    @Test
    public void callTheMountainChocoboConjuresAMountainIntoHand() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Game game = landlessGame(c);
        Player p = game.getPlayers().get(1);
        resolve(overridden(c, "Call the Mountain Chocobo", p, ZoneType.Hand), p);
        assertEquals(1, p.getCardsIn(ZoneType.Hand, "Mountain").size());
    }

    @Test
    public void landcyclingBecomesCycling() {
        initAndCreateGame();
        RoguelikeContent c = RoguelikeContent.load();
        Game game = landlessGame(c);
        Player p = game.getPlayers().get(1);
        for (String name : List.of("Ice Flan", "Malboro", "Hill Gigas", "Cloudbound Moogle", "Balamb T-Rexaur")) {
            Card card = overridden(c, name, p, ZoneType.Hand);
            assertTrue(name + " cycles", card.hasKeyword(Keyword.CYCLING));
            assertFalse(name + " landcycles", card.hasKeyword(Keyword.TYPECYCLING));
        }
    }
}
