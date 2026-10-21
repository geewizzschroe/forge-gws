package forge.gamemodes.roguelike;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertNotNull;

import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * 6 uncommons and 2 rares by original printing (mythic counts as rare). A two-colour starter has
 * 4 commons, 3 uncommons and 1 rare of each colour.
 */
public class StarterTemplateTest extends AITest {

    /**
     * Starters excused from the per-colour split (the totals still apply). Lightning's rares are a
     * colourless card and a two-colour card, so neither colour has its own rare.
     */
    private static final Set<String> NO_COLOUR_SPLIT = Set.of("lightning_rw");

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
            Map<String, Integer> byColour = new TreeMap<>();
            ColorSet deckColours = ColorSet.C;
            for (Map.Entry<String, Integer> e : s.deck.entrySet()) {
                assertEquals(s.id + ": copies of " + e.getKey(), Integer.valueOf(2), e.getValue());
                String rarity = originalRarity(e.getKey());
                total.merge(rarity, 1, Integer::sum);
                ColorSet colour = StaticData.instance().getCommonCards().getCard(e.getKey()).getRules().getColor();
                deckColours = ColorSet.fromMask(deckColours.getColor() | colour.getColor());
                if (colour.isMonoColor()) {
                    byColour.merge(MagicColor.toShortString(colour.getColor()) + " " + rarity, 1, Integer::sum);
                }
            }
            assertEquals(s.id + ": rarities", "{C=8, R=2, U=6}", total.toString());
            if (deckColours.countColors() == 2 && !NO_COLOUR_SPLIT.contains(s.id)) {
                Map<String, Integer> expected = new TreeMap<>();
                for (MagicColor.Color colour : deckColours) {
                    String code = MagicColor.toShortString(colour.getColorMask());
                    expected.put(code + " C", 4);
                    expected.put(code + " U", 3);
                    expected.put(code + " R", 1);
                }
                assertEquals(s.id + ": rarities by colour", expected.toString(), byColour.toString());
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
