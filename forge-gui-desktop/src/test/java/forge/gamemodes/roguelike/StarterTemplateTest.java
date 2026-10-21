package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.testng.annotations.Test;

import forge.StaticData;
import forge.ai.AITest;
import forge.card.CardEdition;
import forge.card.CardRarity;
import forge.card.ColorSet;
import forge.card.MagicColor;
import forge.item.PaperCard;

/**
 * Starter decks follow the template (DESIGN.md 2.3): 16 different cards, 2 copies each, with 8 commons,
 * 6 uncommons and 2 rares by original printing (mythic counts as rare). A two-colour starter is
 * balanced between its colours at each rarity: a card of one colour counts -1, of the other +1, and
 * each rarity must total 0. A card of both colours counts as both (0) and a colourless card as
 * neither (0), so a gold rare can balance a colourless one.
 */
public class StarterTemplateTest extends AITest {

    /** Set types that hold a card's first printing; reprint products dated early would otherwise win. */
    private static final EnumSet<CardEdition.Type> ORIGINAL_TYPES = EnumSet.of(CardEdition.Type.CORE,
            CardEdition.Type.EXPANSION, CardEdition.Type.STARTER, CardEdition.Type.DRAFT, CardEdition.Type.COMMANDER,
            CardEdition.Type.DUEL_DECK, CardEdition.Type.MULTIPLAYER, CardEdition.Type.BOXED_SET, CardEdition.Type.OTHER);

    @Test
    public void startersFollowTheTemplate() {
        initAndCreateGame(); // loads the card database
        RoguelikeContent c = RoguelikeContent.load();
        for (StarterDef s : c.getStarters().values()) {
            assertEquals(s.id + ": different cards", 16, s.deck.size());
            Map<String, Integer> total = new TreeMap<>();
            Map<String, ColorSet> colours = new LinkedHashMap<>();
            Map<String, String> rarities = new LinkedHashMap<>();
            byte deckColours = 0;
            for (Map.Entry<String, Integer> e : s.deck.entrySet()) {
                assertEquals(s.id + ": copies of " + e.getKey(), Integer.valueOf(2), e.getValue());
                String rarity = originalRarity(e.getKey());
                total.merge(rarity, 1, Integer::sum);
                rarities.put(e.getKey(), rarity);
                ColorSet colour = StaticData.instance().getCommonCards().getCard(e.getKey()).getRules().getColor();
                colours.put(e.getKey(), colour);
                deckColours |= colour.getColor();
            }
            assertEquals(s.id + ": rarities", "{C=8, R=2, U=6}", total.toString());
            if (ColorSet.fromMask(deckColours).countColors() == 2) {
                List<MagicColor.Color> pair = new ArrayList<>();
                ColorSet.fromMask(deckColours).forEach(pair::add);
                Map<String, Integer> balance = new TreeMap<>(Map.of("C", 0, "U", 0, "R", 0));
                for (String name : s.deck.keySet()) {
                    ColorSet colour = colours.get(name);
                    int score = (colour.hasAnyColor(pair.get(1).getColorMask()) ? 1 : 0)
                            - (colour.hasAnyColor(pair.get(0).getColorMask()) ? 1 : 0);
                    balance.merge(rarities.get(name), score, Integer::sum);
                }
                assertEquals(s.id + ": colour balance by rarity (" + pair + ")", "{C=0, R=0, U=0}", balance.toString());
            }
        }
    }

    /** C, U or R for the card's earliest printing in an original (not reprint) product. */
    private static String originalRarity(final String name) {
        CardEdition.Collection editions = StaticData.instance().getEditions();
        List<PaperCard> printings = StaticData.instance().getCommonCards().getAllCards(name);
        PaperCard first = null;
        Date firstDate = null;
        for (PaperCard pc : printings) {
            CardEdition ed = editions.get(pc.getEdition());
            if (ed == null || !ORIGINAL_TYPES.contains(ed.getType()) || ed.getName().contains("Mystery Booster")) {
                continue;
            }
            if (first == null || ed.getDate().before(firstDate)) {
                first = pc;
                firstDate = ed.getDate();
            }
        }
        assertNotNull("no original printing found for " + name, first);
        CardRarity r = first.getRarity();
        return r == CardRarity.MythicRare ? "R" : r.toString();
    }
}
